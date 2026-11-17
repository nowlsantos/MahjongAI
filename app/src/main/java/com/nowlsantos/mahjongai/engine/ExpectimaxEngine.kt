package com.nowlsantos.mahjongai.engine

import com.nowlsantos.mahjongai.engine.defense.SafetyTileAnalyzer
import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit

data class ExpectimaxResult(
    val bestTileToDiscard: MahjongTile,
    val expectedUtility: Double
)

class ExpectimaxEngine {

    private val safetyAnalyzer = SafetyTileAnalyzer()

    /**
     * Evaluates the player's hand, revealed melds, and all visible board tiles (discards + melds)
     * to determine the optimal discard that maximizes progress toward a winning hand (Hu),
     * while preferring safer discards when offensive value is tied.
     */
    fun findOptimalDiscard(
        currentHand: List<MahjongTile>,
        revealedMeldsCount: Int = 0,
        visibleBoardTiles: List<MahjongTile> = emptyList(),
        seatWind: HonorType? = null,
        prevalentWind: HonorType? = null,
        hasRevealedChow: Boolean = false
    ): ExpectimaxResult {
        val nonBonusHand = currentHand.filterNot { it.isBonus }
        require(nonBonusHand.isNotEmpty()) { "currentHand must contain at least one non-bonus tile." }

        // Combine player's concealed hand with all public discards & exposed melds to know exact live tile counts
        val allVisibleTiles = nonBonusHand + visibleBoardTiles
        val visibleCounts = TingCalculator.buildVisibleTileCounts(allVisibleTiles)

        val uniqueCandidates = nonBonusHand.distinctBy { it.suit to it.value }

        var bestTile = uniqueCandidates.first()
        var bestScore = Double.NEGATIVE_INFINITY

        for (candidateDiscard in uniqueCandidates) {
            val handAfterDiscard = nonBonusHand.toMutableList()
            handAfterDiscard.remove(candidateDiscard)

            val offensiveUtility = evaluatePostDiscardState(
                handAfterDiscard = handAfterDiscard,
                revealedMeldsCount = revealedMeldsCount,
                visibleCounts = visibleCounts,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = hasRevealedChow
            )

            // Tactical safety tie-breaker: prefer discarding safer tiles (Kabe, Suji, exhausted Honors)
            // Dynamic High-Roller Mode: If the hand is in a high-paying 4+ Fan trajectory (offensiveUtility >= 400.0)
            // or close to winning (shanten <= 1), discount defensive fear so the AI pushes forward to victory!
            val dangerScore = safetyAnalyzer.calculateDangerScore(candidateDiscard, allVisibleTiles)
            val isHighRollerHand = offensiveUtility >= 400.0
            val safetyMultiplier = if (isHighRollerHand) 1.8 else 4.5
            val safetyAdjustment = -(dangerScore * safetyMultiplier)

            val totalUtility = offensiveUtility + safetyAdjustment

            if (totalUtility > bestScore) {
                bestScore = totalUtility
                bestTile = candidateDiscard
            }
        }

        return ExpectimaxResult(bestTile, bestScore)
    }

    /**
     * Legacy overload accepting a remainingWall list for backward compatibility.
     */
    fun findOptimalDiscard(
        currentHand: List<MahjongTile>,
        remainingWall: List<MahjongTile>
    ): ExpectimaxResult {
        val nonBonusHand = currentHand.filterNot { it.isBonus }
        if (nonBonusHand.isEmpty()) {
            return ExpectimaxResult(currentHand.first(), 0.0)
        }
        val inferredRevealed = ((14 - nonBonusHand.size).coerceAtLeast(0) / 3).coerceIn(0, 4)

        // Convert remainingWall into visibleCounts (4 - remainingInWall)
        val wallCounts = remainingWall.filterNot { it.isBonus }.groupingBy { it.suit to it.value }.eachCount()
        val visibleCounts = mutableMapOf<Pair<Suit, Int>, Int>()
        for (suit in listOf(Suit.CHARACTER, Suit.BAMBOO, Suit.DOT)) {
            for (v in 1..9) {
                val inWall = wallCounts[suit to v] ?: 0
                visibleCounts[suit to v] = (4 - inWall).coerceIn(0, 4)
            }
        }
        for (v in 1..7) {
            val inWall = wallCounts[Suit.HONOR to v] ?: 0
            visibleCounts[Suit.HONOR to v] = (4 - inWall).coerceIn(0, 4)
        }

        val uniqueCandidates = nonBonusHand.distinctBy { it.suit to it.value }
        var bestTile = uniqueCandidates.first()
        var maxExpectedUtility = Double.NEGATIVE_INFINITY

        for (candidateDiscard in uniqueCandidates) {
            val handAfterDiscard = nonBonusHand.toMutableList()
            handAfterDiscard.remove(candidateDiscard)

            val expectedUtility = evaluatePostDiscardState(
                handAfterDiscard = handAfterDiscard,
                revealedMeldsCount = inferredRevealed,
                visibleCounts = visibleCounts,
                seatWind = null,
                prevalentWind = null
            )

            if (expectedUtility > maxExpectedUtility) {
                maxExpectedUtility = expectedUtility
                bestTile = candidateDiscard
            }
        }

        return ExpectimaxResult(bestTile, maxExpectedUtility)
    }

    /**
     * Computes a unified strategic score for a 13-tile (post-discard or post-meld) hand:
     * 1. Primary driver: Minimal Shanten (closer to winning = exponentially higher base utility).
     * 2. Secondary driver: Live Tile Acceptance / Ukeire (number of remaining unseen tiles that advance the hand).
     * 3. Tertiary driver: Proto-meld shape quality + Flush/Pung Fan potential.
     */
    fun evaluatePostDiscardState(
        handAfterDiscard: List<MahjongTile>,
        revealedMeldsCount: Int,
        visibleCounts: Map<Pair<Suit, Int>, Int>,
        seatWind: HonorType? = null,
        prevalentWind: HonorType? = null,
        hasRevealedChow: Boolean = false
    ): Double {
        val shanten = TingCalculator.calculateExactShanten(handAfterDiscard, revealedMeldsCount)
        val liveAcceptance = TingCalculator.calculateLiveTileAcceptance(
            hand = handAfterDiscard,
            revealedMeldsCount = revealedMeldsCount,
            visibleCounts = visibleCounts
        )
        val shapeQuality = TingCalculator.evaluateLiveShapeQuality(
            hand = handAfterDiscard,
            visibleCounts = visibleCounts,
            seatWind = seatWind,
            prevalentWind = prevalentWind
        )

        // Suit concentration and flush potential (Half Flush 3 Fan / Full Flush 7 Fan)
        val nonBonusTiles = handAfterDiscard.filterNot { it.isBonus }
        val suitCounts = nonBonusTiles.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
        val totalSuitTiles = suitCounts.values.sum()
        val dominantSuit = suitCounts.maxByOrNull { it.value }?.key
        val dominantSuitCount = suitCounts[dominantSuit] ?: 0
        val offSuitCount = totalSuitTiles - dominantSuitCount
        val honorTiles = nonBonusTiles.filter { it.suit == Suit.HONOR }
        val honorCount = honorTiles.size
        // Only count Honor tiles that form at least a Pair/Pung toward Half-Flush progress (prevents hoarding useless singleton Winds)
        val honorRankCounts = honorTiles.groupingBy { it.value }.eachCount()
        val pairedHonorTileCount = honorRankCounts.values.filter { it >= 2 }.sum()
        val singletonHonorCount = honorRankCounts.values.count { it == 1 }

        // Pure Flush / Full Flush (7 Fan / 4,800 pts)
        val pureFlushBonus = if (offSuitCount == 0 && honorCount == 0 && dominantSuitCount >= 7) {
            900.0 + (dominantSuitCount - 7) * 45.0
        } else 0.0

        val nearPureFlushBonus = if (offSuitCount <= 2 && honorCount == 0 && dominantSuitCount >= 7) {
            (480.0 + (dominantSuitCount - 7) * 55.0) - (offSuitCount * 95.0)
        } else 0.0

        // Half Flush / Mixed One Suit (3 Fan / 800 pts) - Requires real paired/punged honors, not random singleton winds
        val halfFlushBonus = if (offSuitCount == 0 && pairedHonorTileCount >= 2 && dominantSuitCount >= 6) {
            460.0 + (dominantSuitCount - 6) * 35.0 + (pairedHonorTileCount * 15.0) - (singletonHonorCount * 25.0)
        } else 0.0

        val nearHalfFlushBonus = if (offSuitCount <= 2 && dominantSuitCount + pairedHonorTileCount >= 8 && dominantSuitCount >= 6) {
            (310.0 + ((dominantSuitCount + pairedHonorTileCount) - 8) * 35.0) - (offSuitCount * 75.0) - (singletonHonorCount * 25.0)
        } else 0.0

        val generalSuitBonus = if (dominantSuitCount >= 6) (dominantSuitCount - 5) * 55.0 else 0.0

        val bestFlushBonus = maxOf(pureFlushBonus, nearPureFlushBonus, halfFlushBonus, nearHalfFlushBonus, generalSuitBonus)

        // All Pungs potential (3 Fan / 800 pts) - only viable if no revealed Chows exist
        val tileCounts = nonBonusTiles.groupingBy { it.suit to it.value }.eachCount()
        val pairsOrTriplets = tileCounts.values.count { it >= 2 }
        val tripletsCount = tileCounts.values.count { it >= 3 }
        val pungsBonus = if (!hasRevealedChow) {
            when {
                pairsOrTriplets >= 5 -> 450.0 + (tripletsCount * 40.0)
                pairsOrTriplets == 4 -> 280.0 + (tripletsCount * 35.0)
                pairsOrTriplets == 3 -> 130.0 + (tripletsCount * 25.0)
                else -> 0.0
            }
        } else 0.0

        // Seven Pairs potential (4 Fan / 1,600 pts) - requires completely concealed hand
        val sevenPairsBonus = if (revealedMeldsCount == 0) {
            when {
                pairsOrTriplets >= 6 -> 580.0
                pairsOrTriplets == 5 -> 380.0
                pairsOrTriplets == 4 -> 190.0
                else -> 0.0
            }
        } else 0.0

        // Lethal Hybrid Combo: All Pungs (3 Fan) + Mixed Suit (3 Fan) = 6 Fan (3,200 pts)
        val pungsAndFlushComboBonus = if (!hasRevealedChow && pairsOrTriplets >= 3 && offSuitCount <= 1 && dominantSuitCount >= 5 && honorCount >= 2) {
            520.0 - (offSuitCount * 120.0)
        } else 0.0

        // Value Honors (Big Three Dragons 8 Fan, Small Three Dragons 5 Fan, Dragon Pungs 1 Fan, Seat/Prevalent Wind 1 Fan)
        var valueHonorsBonus = 0.0
        val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
        val honorGroups = honorTiles.groupBy { it.honorType }

        val dragonPungCount = dragonTypes.count { (honorGroups[it]?.size ?: 0) >= 3 }
        val dragonPairCount = dragonTypes.count { (honorGroups[it]?.size ?: 0) == 2 }

        // Big Three Dragons (大三元, 8 Fan) & Small Three Dragons (小三元, 5 Fan) Opportunistic Aggression
        if (dragonPungCount == 3) {
            valueHonorsBonus += 1200.0
        } else if (dragonPungCount == 2 && dragonPairCount >= 1) {
            valueHonorsBonus += 780.0
        } else if (dragonPungCount + dragonPairCount >= 2) {
            valueHonorsBonus += 420.0
        }

        for (dragon in dragonTypes) {
            val count = honorGroups[dragon]?.size ?: 0
            if (count >= 3) valueHonorsBonus += 160.0
            else if (count == 2) valueHonorsBonus += 85.0
        }

        if (seatWind != null) {
            val count = honorGroups[seatWind]?.size ?: 0
            if (count >= 3) valueHonorsBonus += 150.0
            else if (count == 2) valueHonorsBonus += 60.0
        }

        if (prevalentWind != null && prevalentWind != seatWind) {
            val count = honorGroups[prevalentWind]?.size ?: 0
            if (count >= 3) valueHonorsBonus += 130.0
            else if (count == 2) valueHonorsBonus += 50.0
        }

        val totalPatternBonus = bestFlushBonus + pungsBonus + pungsAndFlushComboBonus + valueHonorsBonus + sevenPairsBonus

        // Shanten Compromise: When holding a high-Fan seed (totalPatternBonus >= 300.0),
        // discount the shanten penalty (-700 vs -1000) so the AI willingly sacrifices 1 off-suit sequence
        // to aggressively pursue Half Flush, Pure Flush, All-Pungs, or Three Dragons!
        val shantenWeight = when {
            totalPatternBonus >= 550.0 -> 620.0
            totalPatternBonus >= 300.0 -> 720.0
            else -> 1000.0
        }

        // 1-Fan Minimum Enforcement for AI (一番起胡):
        // A hand qualifies for 1+ Fan if:
        // 1. Concealed hand (revealedMeldsCount == 0) -> guarantees at least 1 Fan (門前清 / Concealed Hand)!
        // 2. Holds value honors (Dragon Pung/Pair, Seat Wind, Prevalent Wind) -> at least 1 Fan!
        // 3. Pursuing Flush (Full or Half Flush) -> 3 to 7 Fan!
        // 4. Pursuing All Pungs -> 3 Fan!
        // 5. Pursuing Ping Wu (All Chows) -> 1 Fan!
        val isPingWuCandidate = honorCount == 0 && pairsOrTriplets <= 1
        val hasValid1FanPath = revealedMeldsCount == 0 ||
            bestFlushBonus >= 200.0 ||
            pungsBonus >= 200.0 ||
            valueHonorsBonus >= 40.0 ||
            isPingWuCandidate

        // Open Hand Zero-Fan Penalty:
        // An open hand (revealedMeldsCount > 0) with mixed suits, no value honors, and not Ping Wu
        // is a pure 0-Fan Chicken Hand. Penalize so the AI avoids this dead end.
        val openZeroFanPenalty = if (revealedMeldsCount > 0 && !hasValid1FanPath) -1000.0 else 0.0

        // Zero-Fan Tenpai Penalty:
        // Prevent settling into a 0-Fan Tenpai that cannot declare a win under AI_MIN_FAN_REQUIRED = 1
        val zeroFanPenalty = if (shanten == 0 && !hasValid1FanPath) -1000.0 else 0.0

        // Penalize hands that are in Tenpai (shanten == 0) or 1-Shanten but have 0 live winning tiles left ("dead wait")
        val deadWaitPenalty = if (liveAcceptance == 0) -250.0 else 0.0

        return (-shanten * shantenWeight) + (liveAcceptance * 18.0) + shapeQuality +
            totalPatternBonus + openZeroFanPenalty + zeroFanPenalty + deadWaitPenalty
    }
}
