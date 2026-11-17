package com.nowlsantos.mahjongai.engine.rl

import com.nowlsantos.mahjongai.engine.ExpectimaxEngine
import com.nowlsantos.mahjongai.engine.defense.OpponentHandReader
import com.nowlsantos.mahjongai.engine.defense.SafetyTileAnalyzer
import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.util.GameConstants
import kotlin.math.exp
import kotlin.random.Random

data class RlDiscardDecision(
    val tileToDiscard: MahjongTile,
    val qValue: Double,
    val resultingShanten: Int,
    val intermediateReward: Double,
    val dangerScore: Double,
    val posture: PostureType = PostureType.BALANCED,
    val features: DoubleArray = DoubleArray(0)
)

class RlDecisionEngine(
    private val expectimaxEngine: ExpectimaxEngine = ExpectimaxEngine(),
    private val safetyAnalyzer: SafetyTileAnalyzer = SafetyTileAnalyzer(),
    private val neuralNet: MahjongNeuralNet = MahjongNeuralNet.getSharedInstance()
) {

    /**
     * Evaluates all candidate discards using the Actor-Critic Neural Network (MahjongNeuralNet),
     * Bayesian Opponent Hand Reading (OpponentHandReader), and Potential-Based Reward Shaping (PBRS),
     * selecting the move via Boltzmann Softmax temperature sampling.
     */
    fun selectOptimalDiscard(
        currentHand: List<MahjongTile>,
        revealedMeldsCount: Int = 0,
        visibleBoardTiles: List<MahjongTile> = emptyList(),
        posture: TacticalPosture = TacticalPosture.Default,
        seatWind: HonorType? = null,
        prevalentWind: HonorType? = null,
        hasRevealedChow: Boolean = false,
        isOpponentInTing: Boolean = false,
        opponents: Collection<PlayerState> = emptyList(),
        wallRemaining: Int = 60,
        playerScore: Int = GameConstants.STARTING_POINTS
    ): RlDiscardDecision {
        val nonBonusHand = currentHand.filterNot { it.isBonus }
        require(nonBonusHand.isNotEmpty()) { "Hand must contain at least one non-bonus tile." }

        val allVisibleTiles = nonBonusHand + visibleBoardTiles
        val visibleCounts = TingCalculator.buildVisibleTileCounts(allVisibleTiles)
        val uniqueCandidates = nonBonusHand.distinctBy { it.suit to it.value }
        val shantenBefore = TingCalculator.calculateExactShanten(nonBonusHand, revealedMeldsCount)
        val maxOpponentMelds = opponents.maxOfOrNull { it.revealedMelds.size } ?: 0

        val evaluatedCandidates = mutableListOf<RlDiscardDecision>()

        for (candidate in uniqueCandidates) {
            val handAfterDiscard = nonBonusHand.toMutableList().apply { remove(candidate) }

            // 1. Evaluate offensive utility (Shanten, Ukeire outs, Fan potential)
            val offensiveUtility = expectimaxEngine.evaluatePostDiscardState(
                handAfterDiscard = handAfterDiscard,
                revealedMeldsCount = revealedMeldsCount,
                visibleCounts = visibleCounts,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = hasRevealedChow
            )

            // 2. Compute global board danger and Bayesian per-opponent targeted danger
            val globalDanger = safetyAnalyzer.calculateDangerScore(candidate, allVisibleTiles)
            val targetedDanger = if (opponents.isNotEmpty()) {
                OpponentHandReader.evaluateTargetedDanger(candidate, opponents, globalDanger)
            } else {
                globalDanger
            }

            // 3. Calculate Potential-Based Intermediate RL Reward F(s, s') = gamma * Phi(s') - Phi(s)
            val transition = StepTransition(
                previousHand = nonBonusHand,
                newHand = handAfterDiscard,
                actionTaken = "DISCARD_${candidate.suit}_${candidate.value}",
                isOpponentInTing = isOpponentInTing,
                discardedTileDangerScore = targetedDanger,
                revealedMeldsCount = revealedMeldsCount
            )
            val intermediateReward = RewardCalculator.calculateIntermediateReward(transition)
            val resultingShanten = TingCalculator.calculateExactShanten(handAfterDiscard, revealedMeldsCount)

            // 4. Extract 24-dimensional state-action feature vector phi(s, a)
            val features = MahjongFeatureExtractor.extractFeatures(
                handBeforeDiscard = nonBonusHand,
                handAfterDiscard = handAfterDiscard,
                candidateTile = candidate,
                revealedMeldsCount = revealedMeldsCount,
                shantenBefore = shantenBefore,
                shantenAfter = resultingShanten,
                offensiveUtility = offensiveUtility,
                intermediateReward = intermediateReward,
                globalDangerScore = globalDanger,
                targetedOpponentDanger = targetedDanger,
                isAnyOpponentInTing = isOpponentInTing,
                maxOpponentMelds = maxOpponentMelds,
                wallRemaining = wallRemaining,
                playerScore = playerScore,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = hasRevealedChow
            )

            // 5. Forward pass through trained Actor-Critic Neural Network + Posture modulation
            val rawNeuralQ = neuralNet.predict(features)
            val postureAdjustment = when (posture.type) {
                PostureType.DEFENSIVE_FOLD -> -targetedDanger * 45.0 * posture.dangerWeight
                PostureType.ATTACK -> (offensiveUtility / 50.0) * (posture.fanWeight / 10.0)
                PostureType.SPEED -> (shantenBefore - resultingShanten) * 18.0
                PostureType.BALANCED -> 0.0
            }
            val finalQValue = rawNeuralQ + postureAdjustment

            evaluatedCandidates.add(
                RlDiscardDecision(
                    tileToDiscard = candidate,
                    qValue = finalQValue,
                    resultingShanten = resultingShanten,
                    intermediateReward = intermediateReward,
                    dangerScore = targetedDanger,
                    posture = posture.type,
                    features = features
                )
            )
        }

        if (evaluatedCandidates.isEmpty()) {
            return RlDiscardDecision(
                tileToDiscard = nonBonusHand.first(),
                qValue = 0.0,
                resultingShanten = 4,
                intermediateReward = 0.0,
                dangerScore = 0.0,
                posture = posture.type
            )
        }

        // 6. Stochastic Boltzmann / Softmax Action Selection
        return selectWithSoftmax(evaluatedCandidates, posture.temperature)
    }

    private fun selectWithSoftmax(
        candidates: List<RlDiscardDecision>,
        temperature: Double
    ): RlDiscardDecision {
        if (temperature <= 0.05 || candidates.size == 1) {
            return candidates.maxByOrNull { it.qValue } ?: candidates.first()
        }

        val maxQ = candidates.maxOf { it.qValue }
        val weights = candidates.map { candidate ->
            val scaled = (candidate.qValue - maxQ) / temperature
            exp(scaled.coerceIn(-50.0, 0.0))
        }

        val totalWeight = weights.sum()
        if (totalWeight <= 0.0 || totalWeight.isNaN()) {
            return candidates.maxByOrNull { it.qValue } ?: candidates.first()
        }

        val roll = Random.nextDouble() * totalWeight
        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (roll <= cumulative) {
                return candidates[i]
            }
        }

        return candidates.last()
    }

    /**
     * Evaluates whether claiming a meld (Chow or Pung) provides a net positive Q-value advantage
     * over passing, scaled by the dynamic tactical posture threshold.
     */
    fun shouldAcceptClaim(
        baseUtilityBeforeClaim: Double,
        projectedUtilityAfterClaim: Double,
        claimThreshold: Double
    ): Boolean {
        val utilityGain = projectedUtilityAfterClaim - baseUtilityBeforeClaim
        return utilityGain >= claimThreshold
    }
}
