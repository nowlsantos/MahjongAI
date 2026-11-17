package com.nowlsantos.mahjongai.engine.rl

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.util.GameConstants

/**
 * Extracts a normalized 24-dimensional state-action feature vector phi(s, a)
 * for the Actor-Critic Neural Network (MahjongNeuralNet).
 */
object MahjongFeatureExtractor {
    const val FEATURE_DIM = 24

    fun extractFeatures(
        handBeforeDiscard: List<MahjongTile>,
        handAfterDiscard: List<MahjongTile>,
        candidateTile: MahjongTile,
        revealedMeldsCount: Int,
        shantenBefore: Int,
        shantenAfter: Int,
        offensiveUtility: Double,
        intermediateReward: Double,
        globalDangerScore: Double,
        targetedOpponentDanger: Double,
        isAnyOpponentInTing: Boolean,
        maxOpponentMelds: Int,
        wallRemaining: Int,
        playerScore: Int,
        seatWind: HonorType?,
        prevalentWind: HonorType?,
        hasRevealedChow: Boolean
    ): DoubleArray {
        val features = DoubleArray(FEATURE_DIM)
        val nonBonusAfter = handAfterDiscard.filterNot { it.isBonus }
        val totalTiles = nonBonusAfter.size.coerceAtLeast(1)

        // 0: Normalized Shanten after discard (-1..6 mapped to -0.2..1.0)
        features[0] = shantenAfter.coerceIn(-1, 6) / 6.0

        // 1: Shanten delta (positive if improved, negative if regressed)
        features[1] = (shantenBefore - shantenAfter).coerceIn(-2, 2) / 2.0

        // 2: Tenpai indicator (1.0 if ready to win)
        features[2] = if (shantenAfter <= 0) 1.0 else 0.0

        // 3: 1-Shanten indicator
        features[3] = if (shantenAfter == 1) 1.0 else 0.0

        // 4: Normalized Expectimax Offensive Utility (scaled to ~[-1.0, 2.0])
        features[4] = (offensiveUtility / 350.0).coerceIn(-1.5, 2.5)

        // 5: Normalized Potential-Based Intermediate Reward
        features[5] = (intermediateReward / 25.0).coerceIn(-2.0, 2.0)

        // 6: Global board danger of candidate tile (0.0..1.0)
        features[6] = globalDangerScore.coerceIn(0.0, 1.0)

        // 7: Targeted Bayesian opponent danger (Genbutsu/Suji/Flush adjusted)
        features[7] = targetedOpponentDanger.coerceIn(0.0, 1.0)

        // 8: Non-linear interaction: Targeted Danger * Opponent Ting Alert
        features[8] = if (isAnyOpponentInTing) targetedOpponentDanger * 1.5 else targetedOpponentDanger * 0.4

        // 9: Non-linear interaction: Danger * Distance from Tenpai (high when far from winning and tile is dangerous)
        features[9] = targetedOpponentDanger * (shantenAfter.coerceAtLeast(0) / 4.0)

        // 10: Suit Flush Concentration (ratio of dominant suit + PAIRED/PUNGED honors only; singleton honors do not count!)
        val suitCounts = nonBonusAfter.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
        val dominantSuit = suitCounts.maxByOrNull { it.value }?.key
        val dominantSuitCount = suitCounts[dominantSuit] ?: 0
        val honorRankCounts = nonBonusAfter.filter { it.suit == Suit.HONOR }.groupingBy { it.value }.eachCount()
        val pairedHonorTiles = honorRankCounts.values.filter { it >= 2 }.sum()
        features[10] = (dominantSuitCount + pairedHonorTiles).toDouble() / totalTiles

        // 11: Pure Flush Ratio (dominant suit only)
        features[11] = dominantSuitCount.toDouble() / totalTiles

        // 12: Pair / Pung density (All-Pungs / Seven-Pairs potential)
        val rankCounts = nonBonusAfter.groupingBy { it.suit to it.value }.eachCount()
        val pairsOrMore = rankCounts.values.count { it >= 2 }
        features[12] = (pairsOrMore / 5.0).coerceIn(0.0, 1.0)

        // 13: Does candidate break dominant flush suit?
        features[13] = if (dominantSuit != null && dominantSuitCount >= 6 && candidateTile.suit == dominantSuit) 1.0 else 0.0

        // 14: Is candidate a PAIRED/PUNGED Value Honor (Dragon, Seat Wind, Prevalent Wind)?
        val candidateCountInHandBefore = handBeforeDiscard.count { it.suit == candidateTile.suit && it.value == candidateTile.value }
        val isValueHonor = candidateTile.suit == Suit.HONOR && (
            candidateTile.honorType in listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE) ||
                candidateTile.honorType == seatWind ||
                candidateTile.honorType == prevalentWind
            )
        features[14] = if (isValueHonor && candidateCountInHandBefore >= 2) 1.0 else 0.0

        // 15: Is candidate a central connected suit tile (4, 5, 6)?
        features[15] = if (candidateTile.isSuitTile && candidateTile.value in 4..6) 1.0 else 0.0

        // 16: Is candidate an ISOLATED singleton Honor or isolated Terminal? (Strongly favored for early discard!)
        val isSingletonHonor = candidateTile.suit == Suit.HONOR && candidateCountInHandBefore == 1
        val isIsolatedTerminal = candidateTile.isSuitTile && (candidateTile.value == 1 || candidateTile.value == 9) && candidateCountInHandBefore == 1
        features[16] = when {
            isSingletonHonor && !isValueHonor -> 1.5 // Guest wind singleton -> discard first!
            isSingletonHonor -> 1.0                  // Value honor singleton -> discard if unconnected
            isIsolatedTerminal -> 0.5
            else -> 0.0
        }

        // 17: Wall Remaining ratio (1.0 = start of round, 0.0 = exhaustive draw imminent)
        val wallRatio = (wallRemaining.toDouble() / 80.0).coerceIn(0.0, 1.0)
        features[17] = wallRatio

        // 18: Late-game pressure interaction: (1 - wallRatio) * targetedOpponentDanger
        features[18] = (1.0 - wallRatio) * targetedOpponentDanger

        // 19: Opponent Meld Threat (0..4 mapped to 0..1)
        features[19] = (maxOpponentMelds / 4.0).coerceIn(0.0, 1.0)

        // 20: Self Revealed Melds ratio (0..4 mapped to 0..1)
        features[20] = (revealedMeldsCount / 4.0).coerceIn(0.0, 1.0)

        // 21: Concealed Hand Status (Menzenchin bonus eligibility)
        features[21] = if (revealedMeldsCount == 0) 1.0 else 0.0

        // 22: Score Lead / Deficit relative to starting points (-1.0..1.0)
        val scoreDelta = (playerScore - GameConstants.STARTING_POINTS).toDouble() / GameConstants.STARTING_POINTS.coerceAtLeast(1)
        features[22] = scoreDelta.coerceIn(-1.0, 1.0)

        // 23: Bias term (constant 1.0)
        features[23] = 1.0

        return features
    }
}
