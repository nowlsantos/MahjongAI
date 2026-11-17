package com.nowlsantos.mahjongai.engine.rl

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.util.GameConstants

enum class PostureType {
    ATTACK,          // Push aggressively for high Fan or immediate win
    SPEED,           // Accelerate low/mid Fan win to break opponent rhythm or stop dealer
    DEFENSIVE_FOLD,  // Fold hand; prioritize pure safety (Genbutsu/Suji/Kabe/safe honors)
    BALANCED         // Standard shape-building and flexible progression
}

data class TacticalPosture(
    val type: PostureType,
    val shantenWeight: Double,
    val acceptanceWeight: Double,
    val fanWeight: Double,
    val dangerWeight: Double,
    val chowClaimThreshold: Double,
    val pungClaimThreshold: Double,
    val temperature: Double
) {
    companion object {
        val Default = TacticalPosture(
            type = PostureType.BALANCED,
            shantenWeight = 20.0,
            acceptanceWeight = 2.0,
            fanWeight = 12.0,
            dangerWeight = 1.0,
            chowClaimThreshold = 10.0,
            pungClaimThreshold = 8.0,
            temperature = 0.35
        )

        /**
         * Evaluates a player's dynamic tactical posture based on the hand's natural shape,
         * early-wall high-Fan potential, game progress (wall remaining), opponent threats, and table score.
         */
        fun evaluate(
            hand: List<MahjongTile>,
            currentShanten: Int,
            maxOpponentMelds: Int,
            isAnyOpponentInTing: Boolean,
            wallRemaining: Int,
            playerScore: Int
        ): TacticalPosture {
            val nonBonusTiles = hand.filterNot { it.isBonus }
            val isFarFromTing = currentShanten >= 2
            val isEarlyOrMidWall = wallRemaining >= 40
            val isLateGame = wallRemaining < 26

            // High Fan Launchpad Detection:
            // 1. Flush Seed: 6+ in one suit (pure) or 7+ in one suit + honors (half-flush)
            // 2. All-Pungs / Seven-Pairs Seed: 3+ pairs/triplets in early wall, or 4+ pairs anytime
            // 3. Three Dragons Seed: 2+ Dragon pairs/pungs
            val counts = nonBonusTiles.groupingBy { it.suit to it.value }.eachCount()
            val pairOrPungCount = counts.values.count { it >= 2 }
            val suitCounts = nonBonusTiles.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
            val maxSuitCount = suitCounts.maxOfOrNull { it.value } ?: 0
            val honorTiles = nonBonusTiles.filter { it.suit == Suit.HONOR }
            val pairedHonorCount = honorTiles.groupingBy { it.value }.eachCount().values.filter { it >= 2 }.sum()

            val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
            val dragonGroups = honorTiles.filter { it.honorType in dragonTypes }.groupBy { it.honorType }
            val dragonPairsOrPungs = dragonTypes.count { (dragonGroups[it]?.size ?: 0) >= 2 }

            val hasFlushSeed = (maxSuitCount >= 7) || (maxSuitCount + pairedHonorCount >= 8 && maxSuitCount >= 5) || (isEarlyOrMidWall && maxSuitCount >= 6 && maxSuitCount + pairedHonorCount >= 7)
            val hasPungOrPairSeed = (pairOrPungCount >= 4) || (isEarlyOrMidWall && pairOrPungCount >= 3)
            val hasDragonSeed = dragonPairsOrPungs >= 2

            val hasHighFanPotential = hasFlushSeed || hasPungOrPairSeed || hasDragonSeed

            // Threat assessment
            val isHeavyThreat = isAnyOpponentInTing || (isLateGame && maxOpponentMelds >= 2) || (maxOpponentMelds >= 3)

            // Score context
            val isLeader = playerScore > (GameConstants.STARTING_POINTS + 8000)
            val isTrailingCritically = playerScore < (GameConstants.STARTING_POINTS - 6000)

            return when {
                // 1. DEFENSIVE FOLD (Betaori)
                // Heavy threat and hand is not close to ready (2+ shanten), unless pushing a monster hand while trailing
                isHeavyThreat && isFarFromTing && (!hasHighFanPotential || isLeader || isLateGame) -> {
                    TacticalPosture(
                        type = PostureType.DEFENSIVE_FOLD,
                        shantenWeight = 5.0,
                        acceptanceWeight = 0.5,
                        fanWeight = 0.5,
                        dangerWeight = 3.5,
                        chowClaimThreshold = 60.0, // Refuse to open hand
                        pungClaimThreshold = 45.0,
                        temperature = 0.15 // Discard strictly safest tiles
                    )
                }

                // 2. ATTACK (Push / High-Roller Ambition)
                // Triggered when in Ting (0-shanten), OR 1-2 shanten with High-Fan potential (Flush/All-Pungs/Dragons), OR trailing
                (currentShanten == 0) ||
                    (currentShanten <= 2 && hasHighFanPotential && !isHeavyThreat) ||
                    (currentShanten == 1 && (hasHighFanPotential || isTrailingCritically)) -> {
                    val defenseWeight = when {
                        currentShanten == 0 && hasHighFanPotential -> 0.20
                        hasHighFanPotential && isEarlyOrMidWall -> 0.35
                        else -> 0.55
                    }
                    TacticalPosture(
                        type = PostureType.ATTACK,
                        shantenWeight = if (hasHighFanPotential && currentShanten >= 1) 18.0 else 25.0,
                        acceptanceWeight = 2.5,
                        fanWeight = if (hasHighFanPotential) 28.0 else 15.0,
                        dangerWeight = defenseWeight,
                        // Protect All-Pungs from cheap chows, but allow flush chows/pungs
                        chowClaimThreshold = if (hasPungOrPairSeed) 40.0 else if (hasFlushSeed) 5.0 else 12.0,
                        pungClaimThreshold = if (hasPungOrPairSeed || hasFlushSeed || hasDragonSeed) -5.0 else 8.0,
                        temperature = 0.25
                    )
                }

                // 3. SPEED (Mawashi / Quick intercept)
                // Fast path to 1-2 Fan win when hand lacks high-Fan structure, or opponent building melds
                (currentShanten == 1) || (maxOpponentMelds >= 2 && currentShanten <= 2) -> {
                    TacticalPosture(
                        type = PostureType.SPEED,
                        shantenWeight = 30.0,
                        acceptanceWeight = 3.0,
                        fanWeight = 6.0,
                        dangerWeight = 0.9,
                        chowClaimThreshold = -5.0, // Open to quick chows to accelerate
                        pungClaimThreshold = 0.0,
                        temperature = 0.35
                    )
                }

                // 4. BALANCED (Flexible shape building)
                else -> {
                    TacticalPosture(
                        type = PostureType.BALANCED,
                        shantenWeight = 20.0,
                        acceptanceWeight = 2.0,
                        fanWeight = 13.0,
                        dangerWeight = if (isLateGame) 1.8 else 1.0,
                        chowClaimThreshold = 10.0,
                        pungClaimThreshold = 8.0,
                        temperature = 0.38
                    )
                }
            }
        }
    }
}
