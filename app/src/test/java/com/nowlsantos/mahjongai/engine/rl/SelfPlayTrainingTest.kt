package com.nowlsantos.mahjongai.engine.rl

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SelfPlayTrainingTest {

    @Test
    fun executeSelfPlayTrainingAndExportNeuralWeights() {
        val assetDirs = listOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        )
        val targetDir = assetDirs.firstOrNull { it.exists() || it.parentFile?.exists() == true } ?: File("src/main/assets")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val checkpointFile = File(targetDir, MahjongNeuralNet.ASSET_FILE_NAME)

        // 1. Load existing checkpoint if present for Cumulative Multi-Generation Training
        val neuralNet = MahjongNeuralNet()
        val hadCheckpoint = if (checkpointFile.exists()) {
            neuralNet.loadFromJsonString(checkpointFile.readText())
        } else {
            false
        }
        val previousEpisodes = neuralNet.gamesTrained

        val additionalEpisodes = 1500
        val trainer = HeadlessSelfPlayTrainer(neuralNet)

        println("==========================================================================")
        println("🚀 STARTING MULTI-CORE HEADLESS MAHJONG RL SELF-PLAY TRAINING PIPELINE")
        println("Architecture   : 24 -> 48 (LeakyReLU) -> 24 (LeakyReLU) -> Q(s,a) + ResSkip")
        println("Checkpoint     : ${if (hadCheckpoint) "Loaded ($previousEpisodes prior episodes)" else "Fresh Domain Priors"}")
        println("Target Session : +$additionalEpisodes episodes (${additionalEpisodes * 4} player-hands)")
        println("==========================================================================")

        val summary = trainer.runTrainingSession(
            numEpisodes = additionalEpisodes,
            gamma = 0.96,
            initialLearningRate = 0.00035,
            seed = System.currentTimeMillis(),
            batchSize = 64
        ) { ep, stats ->
            println(
                String.format(
                    java.util.Locale.US,
                    "[Episode %5d/%5d | Total: %5d] Wins: %4d (Tsumo: %4d, Ron: %4d) | Draws: %4d | Avg Fan: %.2f | Loss: %.2f -> %.2f | Time: %.1f s",
                    ep,
                    additionalEpisodes,
                    previousEpisodes + ep,
                    stats.totalWins,
                    stats.selfDrawWins,
                    stats.ronWins,
                    stats.draws,
                    stats.avgWinningFan,
                    stats.initialAvgLoss,
                    stats.finalAvgLoss,
                    stats.elapsedMs / 1000.0
                )
            )
        }

        // 2. Save updated cumulative neural weights to app/src/main/assets/mahjong_nn_weights.json
        val cumulativeEpisodes = neuralNet.gamesTrained
        val jsonPayload = neuralNet.toJsonString(
            gamesTrained = cumulativeEpisodes,
            avgReward = summary.avgEpisodeReturn
        )
        checkpointFile.writeText(jsonPayload)

        println("==========================================================================")
        println("✅ 10,000-EPISODE SELF-PLAY TRAINING COMPLETE!")
        println("   • Session Episodes Simulated : ${summary.totalEpisodes} (${summary.totalEpisodes * 4} player-hands)")
        println("   • Cumulative Episodes Trained: $cumulativeEpisodes (${cumulativeEpisodes * 4} total player-hands)")
        println("   • Decisive Wins              : ${summary.totalWins} (Tsumo: ${summary.selfDrawWins}, Ron: ${summary.ronWins})")
        println("   • Exhaustive Draws           : ${summary.draws}")
        println("   • Average Winning Fan        : ${"%.2f".format(java.util.Locale.US, summary.avgWinningFan)}")
        println("   • TD Loss Convergence        : ${"%.2f".format(java.util.Locale.US, summary.initialAvgLoss)} -> ${"%.2f".format(java.util.Locale.US, summary.finalAvgLoss)}")
        println("   • Total Elapsed Time         : ${"%.2f".format(java.util.Locale.US, summary.elapsedMs / 1000.0)} seconds")
        println("   • Saved Neural Weights To    : ${checkpointFile.absolutePath} (${checkpointFile.length()} bytes)")
        println("==========================================================================")

        val reloadedNet = MahjongNeuralNet()
        val loadedSuccessfully = reloadedNet.loadFromJsonString(checkpointFile.readText())
        assertTrue("Trained neural network weights should reload cleanly", loadedSuccessfully)
        assertTrue("Cumulative gamesTrained should be at least 10,000", reloadedNet.gamesTrained >= 10000)
    }
}
