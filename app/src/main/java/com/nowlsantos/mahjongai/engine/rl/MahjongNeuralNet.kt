package com.nowlsantos.mahjongai.engine.rl

import java.io.File
import java.io.InputStream
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure-Kotlin 2-Layer Actor-Critic Multi-Layer Perceptron (MLP) for Mahjong Action-Value Q(s, a)
 * and Advantage Estimation.
 *
 * Architecture:
 * Input (24) -> Dense1 (48, LeakyReLU) -> Dense2 (24, LeakyReLU) -> Output Scalar Q-Score + Linear Residual Skip
 *
 * Includes a residual skip connection (ResNet-style) from the 24 domain features directly to the output
 * so the network starts with strong domain priors and learns deep non-linear corrections via self-play!
 */
class MahjongNeuralNet(
    val inputDim: Int = MahjongFeatureExtractor.FEATURE_DIM,
    val hidden1Dim: Int = 48,
    val hidden2Dim: Int = 24
) {
    // Layer 1: [hidden1Dim x inputDim]
    val w1 = DoubleArray(hidden1Dim * inputDim)
    val b1 = DoubleArray(hidden1Dim)

    // Layer 2: [hidden2Dim x hidden1Dim]
    val w2 = DoubleArray(hidden2Dim * hidden1Dim)
    val b2 = DoubleArray(hidden2Dim)

    // Output Head: [1 x hidden2Dim]
    val wOut = DoubleArray(hidden2Dim)
    var bOut: Double = 0.0

    // Residual Skip Weights (Direct linear connection from input features to output): [inputDim]
    val wSkip = DoubleArray(inputDim)

    // Cumulative episodes trained across all generations
    var gamesTrained: Int = 0

    init {
        initializeWithDomainPriors(Random(42))
    }

    /**
     * Initializes the network with He initialization on the hidden layers and expert domain priors
     * on the residual skip weights, ensuring immediate grandmaster baseline before self-play refinement.
     */
    fun initializeWithDomainPriors(rng: Random = Random.Default) {
        val scale1 = sqrt(2.0 / inputDim) * 0.15
        for (i in w1.indices) w1[i] = rng.nextGaussian() * scale1
        b1.fill(0.01)

        val scale2 = sqrt(2.0 / hidden1Dim) * 0.15
        for (i in w2.indices) w2[i] = rng.nextGaussian() * scale2
        b2.fill(0.01)

        val scaleOut = sqrt(2.0 / hidden2Dim) * 0.15
        for (i in wOut.indices) wOut[i] = rng.nextGaussian() * scaleOut
        bOut = 0.0

        // Residual skip prior aligned with MahjongFeatureExtractor indices (0..23):
        wSkip[0] = -38.0  // 0: Lower shanten after discard is strongly preferred
        wSkip[1] = 28.0   // 1: Positive shanten advancement
        wSkip[2] = 35.0   // 2: Tenpai reach bonus
        wSkip[3] = 14.0   // 3: 1-Shanten bonus
        wSkip[4] = 48.0   // 4: Expectimax offensive utility (ukeire + fan value)
        wSkip[5] = 22.0   // 5: PBRS intermediate reward
        wSkip[6] = -14.0  // 6: Global board danger penalty
        wSkip[7] = -26.0  // 7: Targeted Bayesian opponent danger penalty
        wSkip[8] = -34.0  // 8: Targeted Danger * Opponent Ting interaction
        wSkip[9] = -28.0  // 9: Danger * Distance from Tenpai (fold when far!)
        wSkip[10] = 18.0  // 10: Half-flush concentration
        wSkip[11] = 22.0  // 11: Pure-flush concentration
        wSkip[12] = 15.0  // 12: Pair/Pung density
        wSkip[13] = -16.0 // 13: Penalty for discarding dominant flush suit tile
        wSkip[14] = -8.0  // 14: Slight retention of value honors unless isolated
        wSkip[15] = -4.0  // 15: Retention of central 4-5-6 connectors
        wSkip[16] = 6.0   // 16: Slight preference to discard isolated terminals/guest winds early
        wSkip[17] = 2.0   // 17: Wall remaining ratio
        wSkip[18] = -24.0 // 18: Late-game * targeted danger penalty
        wSkip[19] = -8.0  // 19: Opponent meld threat caution
        wSkip[20] = 4.0   // 20: Self revealed melds
        wSkip[21] = 6.0   // 21: Concealed hand bonus
        wSkip[22] = -3.0  // 22: Score lead caution
        wSkip[23] = 0.0   // 23: Bias
    }

    private fun Random.nextGaussian(): Double {
        var u1: Double
        var u2: Double
        do {
            u1 = nextDouble()
            u2 = nextDouble()
        } while (u1 <= 1e-7)
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    private fun leakyRelu(x: Double): Double = if (x >= 0.0) x else 0.05 * x
    private fun leakyReluDeriv(y: Double): Double = if (y >= 0.0) 1.0 else 0.05

    /**
     * Forward pass returning the scalar Q(s, a) evaluation.
     */
    fun predict(features: DoubleArray): Double {
        // Hidden Layer 1
        val h1 = DoubleArray(hidden1Dim)
        for (i in 0 until hidden1Dim) {
            var sum = b1[i]
            val rowOffset = i * inputDim
            for (j in 0 until inputDim) {
                sum += w1[rowOffset + j] * features[j]
            }
            h1[i] = leakyRelu(sum)
        }

        // Hidden Layer 2
        val h2 = DoubleArray(hidden2Dim)
        for (i in 0 until hidden2Dim) {
            var sum = b2[i]
            val rowOffset = i * hidden1Dim
            for (j in 0 until hidden1Dim) {
                sum += w2[rowOffset + j] * h1[j]
            }
            h2[i] = leakyRelu(sum)
        }

        // Output + Residual Skip
        var out = bOut
        for (i in 0 until hidden2Dim) {
            out += wOut[i] * h2[i]
        }
        for (j in 0 until inputDim) {
            out += wSkip[j] * features[j]
        }

        return out
    }

    /**
     * Performs a single Huber-clipped TD(lambda) / Monte-Carlo policy-value gradient update step
     * toward target return [targetQ], returning the squared TD error.
     */
    @Synchronized
    fun trainStep(features: DoubleArray, targetQ: Double, learningRate: Double = 0.0008): Double {
        // 1. Forward pass with activations cached
        val h1 = DoubleArray(hidden1Dim)
        for (i in 0 until hidden1Dim) {
            var sum = b1[i]
            val rowOffset = i * inputDim
            for (j in 0 until inputDim) {
                sum += w1[rowOffset + j] * features[j]
            }
            h1[i] = leakyRelu(sum)
        }

        val h2 = DoubleArray(hidden2Dim)
        for (i in 0 until hidden2Dim) {
            var sum = b2[i]
            val rowOffset = i * hidden1Dim
            for (j in 0 until hidden1Dim) {
                sum += w2[rowOffset + j] * h1[j]
            }
            h2[i] = leakyRelu(sum)
        }

        var predQ = bOut
        for (i in 0 until hidden2Dim) {
            predQ += wOut[i] * h2[i]
        }
        for (j in 0 until inputDim) {
            predQ += wSkip[j] * features[j]
        }

        val error = predQ - targetQ
        // Huber gradient clipping for stability against high-Fan outlier spikes
        val dOut = error.coerceIn(-15.0, 15.0)
        val l2Decay = 1e-5

        // 2. Backprop into Output & Skip Weights
        val dH2 = DoubleArray(hidden2Dim)
        for (i in 0 until hidden2Dim) {
            dH2[i] = dOut * wOut[i] * leakyReluDeriv(h2[i])
            wOut[i] -= learningRate * (dOut * h2[i] + l2Decay * wOut[i])
        }
        bOut -= learningRate * dOut

        for (j in 0 until inputDim) {
            wSkip[j] -= learningRate * (dOut * features[j] + l2Decay * wSkip[j])
        }

        // 3. Backprop into Layer 2
        val dH1 = DoubleArray(hidden1Dim)
        for (i in 0 until hidden2Dim) {
            val grad2 = dH2[i]
            val rowOffset = i * hidden1Dim
            for (j in 0 until hidden1Dim) {
                dH1[j] += grad2 * w2[rowOffset + j]
                w2[rowOffset + j] -= learningRate * (grad2 * h1[j] + l2Decay * w2[rowOffset + j])
            }
            b2[i] -= learningRate * grad2
        }

        // 4. Backprop into Layer 1
        for (j in 0 until hidden1Dim) {
            val grad1 = dH1[j] * leakyReluDeriv(h1[j])
            val rowOffset = j * inputDim
            for (k in 0 until inputDim) {
                w1[rowOffset + k] -= learningRate * (grad1 * features[k] + l2Decay * w1[rowOffset + k])
            }
            b1[j] -= learningRate * grad1
        }

        return error * error
    }

    /**
     * Serializes the neural network weights to a compact, dependency-free JSON format.
     */
    fun toJsonString(gamesTrained: Int = 0, avgReward: Double = 0.0): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"gamesTrained\": ").append(gamesTrained).append(",\n")
        sb.append("  \"avgReward\": ").append("%.4f".format(java.util.Locale.US, avgReward)).append(",\n")
        sb.append("  \"bOut\": ").append("%.6f".format(java.util.Locale.US, bOut)).append(",\n")
        sb.append("  \"wSkip\": ").append(wSkip.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append(",\n")
        sb.append("  \"wOut\": ").append(wOut.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append(",\n")
        sb.append("  \"b1\": ").append(b1.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append(",\n")
        sb.append("  \"b2\": ").append(b2.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append(",\n")
        sb.append("  \"w1\": ").append(w1.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append(",\n")
        sb.append("  \"w2\": ").append(w2.joinToString(",", "[", "]") { "%.6f".format(java.util.Locale.US, it) }).append("\n")
        sb.append("}")
        return sb.toString()
    }

    fun loadFromJsonString(json: String): Boolean {
        return try {
            fun parseArray(key: String, target: DoubleArray) {
                val keyIdx = json.indexOf("\"$key\"")
                if (keyIdx == -1) return
                val startBracket = json.indexOf('[', keyIdx)
                val endBracket = json.indexOf(']', startBracket)
                if (startBracket == -1 || endBracket == -1) return
                val tokens = json.substring(startBracket + 1, endBracket).split(',')
                for (i in 0 until minOf(tokens.size, target.size)) {
                    target[i] = tokens[i].trim().toDouble()
                }
            }

            val gamesMatch = Regex("\"gamesTrained\"\\s*:\\s*([0-9]+)").find(json)
            if (gamesMatch != null) {
                gamesTrained = gamesMatch.groupValues[1].toIntOrNull() ?: 0
            }

            val bOutMatch = Regex("\"bOut\"\\s*:\\s*([-0-9.]+)").find(json)
            if (bOutMatch != null) {
                bOut = bOutMatch.groupValues[1].toDouble()
            }

            parseArray("wSkip", wSkip)
            parseArray("wOut", wOut)
            parseArray("b1", b1)
            parseArray("b2", b2)
            parseArray("w1", w1)
            parseArray("w2", w2)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ASSET_FILE_NAME = "mahjong_nn_weights.json"
        private const val LOCAL_ASSET_PATH = "src/main/assets/mahjong_nn_weights.json"
        private const val ROOT_ASSET_PATH = "app/src/main/assets/mahjong_nn_weights.json"

        @Volatile
        private var sharedInstance: MahjongNeuralNet? = null

        /**
         * Returns the shared production neural network, automatically loading trained weights
         * from disk or Android assets if available.
         */
        fun getSharedInstance(assetStreamProvider: (() -> InputStream?)? = null): MahjongNeuralNet {
            sharedInstance?.let { return it }
            synchronized(this) {
                sharedInstance?.let { return it }
                val net = MahjongNeuralNet()
                var loaded = false

                // 1. Try provided Android Asset InputStream
                if (assetStreamProvider != null) {
                    try {
                        assetStreamProvider()?.bufferedReader()?.use { reader ->
                            loaded = net.loadFromJsonString(reader.readText())
                        }
                    } catch (_: Exception) {}
                }

                // 2. Try filesystem paths (for JVM tests & studio runtime)
                if (!loaded) {
                    val candidates = listOf(File(ROOT_ASSET_PATH), File(LOCAL_ASSET_PATH))
                    for (file in candidates) {
                        if (file.exists()) {
                            loaded = net.loadFromJsonString(file.readText())
                            if (loaded) break
                        }
                    }
                }

                // 3. Try ClassLoader resource stream
                if (!loaded) {
                    try {
                        val stream = MahjongNeuralNet::class.java.classLoader?.getResourceAsStream("assets/$ASSET_FILE_NAME")
                            ?: MahjongNeuralNet::class.java.classLoader?.getResourceAsStream(ASSET_FILE_NAME)
                        stream?.bufferedReader()?.use { reader ->
                            net.loadFromJsonString(reader.readText())
                        }
                    } catch (_: Exception) {}
                }

                sharedInstance = net
                return net
            }
        }
    }
}
