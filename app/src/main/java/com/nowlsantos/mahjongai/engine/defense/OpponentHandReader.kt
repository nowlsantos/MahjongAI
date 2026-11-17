package com.nowlsantos.mahjongai.engine.defense

import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.model.Suit

data class OpponentThreatProfile(
    val playerId: Int,
    val tenpaiProbability: Double,
    val suspectedFlushSuit: Suit?,
    val isSuspectedAllPungs: Boolean,
    val estimatedFanThreat: Double,
    val tileDangerMultiplier: Double
)

/**
 * Bayesian Opponent Hand Reader (inspired by Suphx & Mortal).
 * Analyzes each opponent's individual discard stream (Ho) and open melds (Furo)
 * to infer Tenpai probability, Flush commitments, All-Pungs trajectories,
 * per-seat Genbutsu (100% safe), and per-seat Suji traps.
 */
object OpponentHandReader {

    /**
     * Evaluates the composite danger of discarding [candidate] against all 3 opponents,
     * weighted by each opponent's probability of being in Tenpai and their estimated hand Fan.
     */
    fun evaluateTargetedDanger(
        candidate: MahjongTile,
        opponents: Collection<PlayerState>,
        globalDangerScore: Double
    ): Double {
        if (candidate.isBonus || opponents.isEmpty()) return 0.0

        var maxWeightedDanger = globalDangerScore * 0.4
        var sumThreatDanger = 0.0

        for (opp in opponents) {
            val profile = analyzeOpponentForTile(candidate, opp, globalDangerScore)
            val seatRisk = profile.tenpaiProbability * profile.tileDangerMultiplier * (1.0 + profile.estimatedFanThreat * 0.22)
            if (seatRisk > maxWeightedDanger) {
                maxWeightedDanger = seatRisk
            }
            sumThreatDanger += seatRisk * 0.35
        }

        return (maxWeightedDanger + sumThreatDanger).coerceIn(0.01, 1.0)
    }

    fun analyzeOpponentForTile(
        candidate: MahjongTile,
        opponent: PlayerState,
        baseDanger: Double
    ): OpponentThreatProfile {
        val discards = opponent.discards
        val melds = opponent.revealedMelds.filter { it.type != MeldType.FLOWER }

        // 1. Absolute Genbutsu Check: If this opponent discarded this exact tile, 0% Ron risk for them
        val isGenbutsu = discards.any { it.suit == candidate.suit && it.value == candidate.value }
        if (isGenbutsu) {
            return OpponentThreatProfile(
                playerId = opponent.playerId,
                tenpaiProbability = estimateTenpaiProbability(opponent, discards.size, melds.size),
                suspectedFlushSuit = null,
                isSuspectedAllPungs = false,
                estimatedFanThreat = 1.0,
                tileDangerMultiplier = 0.02
            )
        }

        // 2. Tenpai Probability Estimation
        val tenpaiProb = estimateTenpaiProbability(opponent, discards.size, melds.size)

        // 3. Flush (Chinitsu / Honitsu) Detection
        val suspectedFlushSuit = detectSuspectedFlushSuit(discards, melds)

        // 4. All-Pungs (Toitoi) Detection
        val pungMeldCount = melds.count { it.type == MeldType.PUNG || it.type == MeldType.KONG || it.type == MeldType.CONCEALED_KONG }
        val isSuspectedAllPungs = pungMeldCount >= 2 && melds.none { it.type == MeldType.CHOW }

        // 5. Estimated Hand Value (Fan Threat)
        var estimatedFan = 1.0
        if (suspectedFlushSuit != null) estimatedFan += 3.0
        if (isSuspectedAllPungs) estimatedFan += 2.5
        if (melds.size >= 3) estimatedFan += 1.5
        if (opponent.bonusTiles.size >= 2) estimatedFan += 1.0

        // 6. Per-Seat Suji & Tile Danger Multiplier
        var multiplier = baseDanger

        // If opponent is on a suspected Flush in candidate's suit, danger spikes dramatically!
        if (suspectedFlushSuit != null) {
            if (candidate.suit == suspectedFlushSuit) {
                multiplier = (multiplier * 1.65).coerceAtMost(0.98)
            } else if (candidate.suit == Suit.HONOR) {
                multiplier = (multiplier * 1.35).coerceAtMost(0.90) // Half-flush honor danger
            } else {
                // Off-suit against a flush player is much safer
                multiplier *= 0.45
            }
        }

        // Per-opponent Suji check (if opponent discarded 4, 5, or 6 of candidate's suit)
        if (candidate.isSuitTile) {
            val oppDiscardedValuesInSuit = discards.filter { it.suit == candidate.suit }.map { it.value }.toSet()
            val isOpponentSujiSafe = when (candidate.value) {
                1, 7 -> 4 in oppDiscardedValuesInSuit
                2, 8 -> 5 in oppDiscardedValuesInSuit
                3, 9 -> 6 in oppDiscardedValuesInSuit
                4 -> (1 in oppDiscardedValuesInSuit) && (7 in oppDiscardedValuesInSuit)
                5 -> (2 in oppDiscardedValuesInSuit) && (8 in oppDiscardedValuesInSuit)
                6 -> (3 in oppDiscardedValuesInSuit) && (9 in oppDiscardedValuesInSuit)
                else -> false
            }
            if (isOpponentSujiSafe && !isSuspectedAllPungs) {
                multiplier *= 0.52
            }

            // Early middle discard read (Ura-suji / Matagi-suji):
            // If opponent discarded 2 or 8 early, tiles 1/3 or 7/9 are safer, but 4/5/6 remain live
            val earlyDiscards = discards.take(6).filter { it.suit == candidate.suit }.map { it.value }
            if (candidate.value == 1 && 2 in earlyDiscards) multiplier *= 0.65
            if (candidate.value == 9 && 8 in earlyDiscards) multiplier *= 0.65
        }

        return OpponentThreatProfile(
            playerId = opponent.playerId,
            tenpaiProbability = tenpaiProb,
            suspectedFlushSuit = suspectedFlushSuit,
            isSuspectedAllPungs = isSuspectedAllPungs,
            estimatedFanThreat = estimatedFan,
            tileDangerMultiplier = multiplier.coerceIn(0.02, 0.99)
        )
    }

    private fun estimateTenpaiProbability(
        opponent: PlayerState,
        discardCount: Int,
        meldCount: Int
    ): Double {
        if (opponent.isTing) return 0.98

        // Base probability grows with turn count (discardCount) and open melds (meldCount)
        val turnProgress = (discardCount.toDouble() / 16.0).coerceIn(0.0, 1.0)
        val meldBoost = when (meldCount) {
            0 -> 0.0
            1 -> 0.18
            2 -> 0.42
            3 -> 0.75
            else -> 0.92
        }

        // Check if recent discards contain central suit tiles (4, 5, 6), signaling hand completion
        val recentCentralDiscards = opponent.discards.takeLast(4).count { it.isSuitTile && it.value in 3..7 }
        val tedashiBoost = recentCentralDiscards * 0.08

        return (turnProgress * 0.55 + meldBoost + tedashiBoost).coerceIn(0.05, 0.95)
    }

    private fun detectSuspectedFlushSuit(discards: List<MahjongTile>, melds: List<Meld>): Suit? {
        val suitTypes = listOf(Suit.BAMBOO, Suit.CHARACTER, Suit.DOT)

        // Check open melds first: if 2+ suit melds are all in the same suit
        val suitMelds = melds.mapNotNull { m -> m.tiles.firstOrNull { it.isSuitTile }?.suit }
        if (suitMelds.size >= 2 && suitMelds.distinct().size == 1) {
            val meldSuit = suitMelds.first()
            // Verify opponent hasn't been dumping this suit heavily
            val dumpedInMeldSuit = discards.take(8).count { it.suit == meldSuit }
            if (dumpedInMeldSuit <= 1) return meldSuit
        }

        // Check skewed early discards: if 6+ discards and 0 tiles discarded from one specific suit
        if (discards.size >= 6) {
            val earlySuitDiscards = discards.take(8).filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
            val missingSuits = suitTypes.filter { (earlySuitDiscards[it] ?: 0) == 0 }
            if (missingSuits.size == 1) {
                val candidateFlush = missingSuits.first()
                if (suitMelds.all { it == candidateFlush }) {
                    return candidateFlush
                }
            }
        }

        return null
    }
}
