package com.nowlsantos.mahjongai.engine.rl

import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.engine.scoring.ScoringResult
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit

data class StepTransition(
    val previousHand: List<MahjongTile>,
    val newHand: List<MahjongTile>,
    val actionTaken: String,
    val isOpponentInTing: Boolean,
    val discardedTileDangerScore: Double,
    val revealedMeldsCount: Int = 0
)

object RewardCalculator {
    private const val GAMMA = 0.97

    /**
     * Potential function Phi(s) for Potential-Based Reward Shaping (Ng et al., 1999).
     * Guarantees that shaped rewards F(s, s') = gamma * Phi(s') - Phi(s) telescope across
     * the episode and NEVER alter the optimal policy, while guiding agents toward high-Fan
     * hand structures (Pure Flush, Half Flush, All-Pungs, Three Dragons, Seven Pairs).
     */
    fun calculateStatePotential(
        hand: List<MahjongTile>,
        revealedMeldsCount: Int = 0
    ): Double {
        val nonBonus = hand.filterNot { it.isBonus }
        if (nonBonus.isEmpty()) return 0.0

        val shanten = TingCalculator.calculateExactShanten(nonBonus, revealedMeldsCount)
        val visibleCounts = TingCalculator.buildVisibleTileCounts(nonBonus)
        val ukeire = TingCalculator.calculateLiveTileAcceptance(nonBonus, revealedMeldsCount, visibleCounts).coerceAtMost(36)

        // 1. Suit concentration (Half Flush 3 Fan / Pure Flush 7 Fan)
        val suitCounts = nonBonus.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
        val maxSuitCount = suitCounts.maxOfOrNull { it.value } ?: 0
        val honorTiles = nonBonus.filter { it.suit == Suit.HONOR }
        val honorRankCounts = honorTiles.groupingBy { it.value }.eachCount()
        val pairedHonorTiles = honorRankCounts.values.filter { it >= 2 }.sum()
        val singletonHonors = honorRankCounts.values.count { it == 1 }
        val totalTiles = nonBonus.size.coerceAtLeast(1)

        val pureSuitRatio = maxSuitCount.toDouble() / totalTiles
        val halfFlushRatio = (maxSuitCount + pairedHonorTiles).toDouble() / totalTiles

        val flushPotential = when {
            pureSuitRatio >= 0.85 -> 38.0
            pureSuitRatio >= 0.70 -> (pureSuitRatio - 0.50) * 65.0
            halfFlushRatio >= 0.80 -> 26.0
            halfFlushRatio >= 0.68 && maxSuitCount >= 6 -> (halfFlushRatio - 0.50) * 45.0
            else -> 0.0
        } - (singletonHonors * 3.5)

        // 2. Pair / Pung density (All-Pungs 3 Fan / Seven Pairs 4 Fan)
        val rankCounts = nonBonus.groupingBy { it.suit to it.value }.eachCount()
        val pairsOrMore = rankCounts.values.count { it >= 2 }
        val triplets = rankCounts.values.count { it >= 3 }
        val pungPotential = when {
            pairsOrMore >= 5 -> 30.0 + (triplets * 5.0)
            pairsOrMore == 4 -> 18.0 + (triplets * 4.0)
            pairsOrMore == 3 -> 8.0
            else -> 0.0
        }

        // 3. Dragon Concentration (Small Three Dragons 5 Fan / Big Three Dragons 8 Fan)
        val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
        val dragonGroups = honorTiles.filter { it.honorType in dragonTypes }.groupBy { it.honorType }
        val dragonPairsOrPungs = dragonTypes.count { (dragonGroups[it]?.size ?: 0) >= 2 }
        val dragonBonus = when {
            dragonPairsOrPungs >= 3 -> 42.0
            dragonPairsOrPungs == 2 -> 20.0
            dragonPairsOrPungs == 1 -> 6.0
            else -> 0.0
        }

        val shantenScore = (5 - shanten.coerceIn(-1, 5)) * 18.0
        val ukeireScore = ukeire * 0.45

        return shantenScore + ukeireScore + flushPotential + pungPotential + dragonBonus
    }

    /**
     * Calculates step-level intermediate rewards using Potential-Based Reward Shaping (PBRS)
     * plus tactical survival cost against active opponent threats.
     */
    fun calculateIntermediateReward(transition: StepTransition): Double {
        val prevPotential = calculateStatePotential(transition.previousHand, transition.revealedMeldsCount)
        val nextPotential = calculateStatePotential(transition.newHand, transition.revealedMeldsCount)

        // 1. Potential-Based Shaping term: F(s, s') = gamma * Phi(s') - Phi(s)
        var reward = (GAMMA * nextPotential) - prevPotential

        // 2. Tactical Risk / Survival Shaping
        if (transition.isOpponentInTing) {
            if (transition.discardedTileDangerScore > 0.60) {
                reward -= 14.0 * transition.discardedTileDangerScore
            } else if (transition.discardedTileDangerScore < 0.18) {
                reward += 6.0
            }
        }

        return reward
    }

    /**
     * Calculates terminal rewards at the end of a round using exponential Hong Kong Fan chip scaling
     * (FanCalculator.calculatePoints), teaching the AI the true exponential payoff of 3-8+ Fan monster hands.
     */
    fun calculateTerminalReward(
        isWinner: Boolean,
        isSelfDraw: Boolean,
        isDealtInto: Boolean,
        scoringResult: ScoringResult?
    ): Double {
        val fan = scoringResult?.totalFan ?: 0
        // Base points scale exponentially: 1 Fan=200, 3 Fan=800, 4 Fan=1600, 6 Fan=3200, 7 Fan=4800, 8 Fan=6400
        val baseChips = FanCalculator.calculatePoints(fan).toDouble()
        // Normalize chip scale (where 200 chips = 20.0 reward units, 800 chips = 80.0, 4800 chips = 480.0)
        val exponentialFanBonus = (baseChips / 10.0).coerceAtMost(650.0)

        return when {
            isWinner && isSelfDraw -> 75.0 + (exponentialFanBonus * 1.35) // Tsumo collects from all 3 players
            isWinner && !isSelfDraw -> 55.0 + exponentialFanBonus
            isDealtInto -> -80.0 - (exponentialFanBonus * 1.25) // Shooter pays all 3 shares on Ron
            else -> -4.0
        }
    }
}
