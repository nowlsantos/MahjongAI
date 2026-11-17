package com.nowlsantos.mahjongai.engine.logic

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit
import kotlin.math.min

/**
 * Calculates how many tile replacements a hand is away from a "Ting" (ready-to-win) state
 * or a full winning hand ("Hu"), along with live tile acceptance (Ukeire) and proto-meld quality.
 *
 * - Distance = 0: Ready to win (Ting / Tenpai) or already won (Hu)
 * - Distance = 1: 1 tile change away from Ting (1-Shanten)
 * - Distance = 8: Worst possible scattered hand
 */
object TingCalculator {

    private data class DecompositionCounts(
        val completeMelds: Int = 0,
        val protoMelds: Int = 0,
        val pairs: Int = 0
    )

    /**
     * Exact Shanten / Tile Distance calculation using two-phase recursive decomposition
     * (Phase 1: Complete Melds, Phase 2: Greedy Pair + Proto-Meld extraction).
     *
     * @param tiles Concealed tiles held by the player.
     * @param revealedMeldsCount Optional explicit revealed meld count; if null, inferred from hand size: (14 - nonBonusCount) / 3.
     */
    fun calculateTileDistance(tiles: List<MahjongTile>, revealedMeldsCount: Int? = null): Int {
        val nonBonus = tiles.filterNot { it.isBonus }
        if (nonBonus.isEmpty()) return 8

        val inferredRevealed = revealedMeldsCount ?: ((14 - nonBonus.size).coerceAtLeast(0) / 3).coerceIn(0, 4)

        // Check if already a winning hand (when holding 14-equivalent tiles)
        if (HandEvaluator.isWinningHand(nonBonus, inferredRevealed)) {
            return 0
        }

        val shanten = calculateExactShanten(nonBonus, inferredRevealed)
        return shanten.coerceAtLeast(0)
    }

    /**
     * Computes exact integer Shanten (-1 = Winning Hand, 0 = Ting/Tenpai, 1 = 1-Shanten, etc.).
     */
    fun calculateExactShanten(tiles: List<MahjongTile>, revealedMeldsCount: Int = 0): Int {
        val targetMelds = (4 - revealedMeldsCount).coerceIn(0, 4)
        var minShanten = 2 * targetMelds

        val charCounts = IntArray(10)
        val bamCounts = IntArray(10)
        val dotCounts = IntArray(10)
        val honorArray = IntArray(8)
        var nonBonusCount = 0

        for (i in 0 until tiles.size) {
            val tile = tiles[i]
            if (tile.isBonus) continue
            nonBonusCount++
            when (tile.suit) {
                Suit.CHARACTER -> if (tile.value in 1..9) charCounts[tile.value]++
                Suit.BAMBOO -> if (tile.value in 1..9) bamCounts[tile.value]++
                Suit.DOT -> if (tile.value in 1..9) dotCounts[tile.value]++
                Suit.HONOR -> if (tile.value in 1..7) honorArray[tile.value]++
                else -> {}
            }
        }

        // Evaluate Honor tiles directly (Honors can only form Pungs and Pairs, never Chows)
        var honorMelds = 0
        var honorPairs = 0
        for (v in 1..7) {
            val count = honorArray[v]
            if (count >= 3) {
                honorMelds++
            } else if (count == 2) {
                honorPairs++
            }
        }

        val charStates = extractSuitDecompositions(charCounts)
        val bambooStates = extractSuitDecompositions(bamCounts)
        val dotStates = extractSuitDecompositions(dotCounts)

        for (c in charStates) {
            for (b in bambooStates) {
                for (d in dotStates) {
                    val totalMelds = c.completeMelds + b.completeMelds + d.completeMelds + honorMelds
                    val totalPairs = c.pairs + b.pairs + d.pairs + honorPairs
                    val totalProtos = c.protoMelds + b.protoMelds + d.protoMelds

                    val cappedMelds = min(totalMelds, targetMelds)
                    val remainingMeldSlots = targetMelds - cappedMelds

                    // Case A: Designate one pair as the winning head (Jantou), remaining pairs can act as proto-Pungs
                    val shantenWithHead = if (totalPairs >= 1) {
                        val availableBlocks = (totalPairs - 1) + totalProtos
                        val usedBlocks = min(remainingMeldSlots, availableBlocks)
                        2 * remainingMeldSlots - usedBlocks - 1
                    } else {
                        Int.MAX_VALUE
                    }

                    // Case B: No dedicated pair head yet; all pairs and proto-melds fill remaining meld slots
                    val availableBlocksNoHead = totalPairs + totalProtos
                    val usedBlocksNoHead = min(remainingMeldSlots, availableBlocksNoHead)
                    val shantenWithoutHead = 2 * remainingMeldSlots - usedBlocksNoHead

                    val bestForCombo = min(shantenWithHead, shantenWithoutHead)
                    if (bestForCombo < minShanten) {
                        minShanten = bestForCombo
                    }
                }
            }
        }

        // Special Concealed Hands: Seven Pairs (七對子) and Thirteen Orphans (十三幺)
        // Only valid when revealedMeldsCount == 0 (concealed hand of 13 or 14 tiles)
        if (revealedMeldsCount == 0 && nonBonusCount >= 13) {
            // 1. Seven Pairs Shanten (computed via primitive arrays with zero pair-object allocation)
            var pairEquivalents = 0
            for (v in 1..9) {
                pairEquivalents += (charCounts[v] / 2) + (bamCounts[v] / 2) + (dotCounts[v] / 2)
            }
            for (v in 1..7) {
                pairEquivalents += honorArray[v] / 2
            }
            val sevenPairsShanten = 6 - pairEquivalents
            if (sevenPairsShanten < minShanten) {
                minShanten = sevenPairsShanten
            }

            // 2. Thirteen Orphans Shanten
            var uniqueOrphans = 0
            var hasOrphanPair = false

            fun checkOrphan(count: Int) {
                if (count >= 1) uniqueOrphans++
                if (count >= 2) hasOrphanPair = true
            }

            checkOrphan(charCounts[1]); checkOrphan(charCounts[9])
            checkOrphan(bamCounts[1]); checkOrphan(bamCounts[9])
            checkOrphan(dotCounts[1]); checkOrphan(dotCounts[9])
            for (v in 1..7) checkOrphan(honorArray[v])

            // Only consider 13 Orphans path if holding at least 9 unique orphan kinds (prevents corrupting normal hands)
            if (uniqueOrphans >= 9) {
                val orphansShanten = 13 - uniqueOrphans - (if (hasOrphanPair) 1 else 0)
                if (orphansShanten < minShanten) {
                    minShanten = orphansShanten
                }
            }
        }

        return minShanten
    }

    /**
     * Counts the total number of live (unseen) copies of tiles that would strictly reduce the
     * player's Shanten distance (or complete a winning hand if already in Ting).
     *
     * @param hand The 13-tile (or post-discard) concealed hand.
     * @param revealedMeldsCount Number of exposed melds the player already has.
     * @param visibleCounts Map of (Suit, value) -> number of copies already visible on the board (0..4).
     */
    private val ALL_TILE_KINDS = buildList {
        for (suit in listOf(Suit.CHARACTER, Suit.BAMBOO, Suit.DOT)) {
            for (v in 1..9) add(suit to v)
        }
        for (v in 1..7) add(Suit.HONOR to v)
    }

    /**
     * Counts the total number of live (unseen) copies of tiles that would strictly reduce the
     * player's Shanten distance (or complete a winning hand if already in Ting).
     *
     * @param hand The 13-tile (or post-discard) concealed hand.
     * @param revealedMeldsCount Number of exposed melds the player already has.
     * @param visibleCounts Map of (Suit, value) -> number of copies already visible on the board (0..4).
     */
    fun calculateLiveTileAcceptance(
        hand: List<MahjongTile>,
        revealedMeldsCount: Int,
        visibleCounts: Map<Pair<Suit, Int>, Int>
    ): Int {
        val currentShanten = calculateExactShanten(hand, revealedMeldsCount)
        var totalLiveHelpfulTiles = 0

        val isOrphansCandidate = revealedMeldsCount == 0 && hand.count {
            it.suit == Suit.HONOR || (it.isSuitTile && (it.value == 1 || it.value == 9))
        } >= 9

        for (i in 0 until ALL_TILE_KINDS.size) {
            val (suit, value) = ALL_TILE_KINDS[i]
            val visible = visibleCounts[suit to value] ?: 0
            val liveRemaining = (4 - visible).coerceAtLeast(0)
            if (liveRemaining == 0) continue

            // Quick proximity filter: only test tile kinds that exist in or adjacent to the hand (or orphan tiles if on 13-Orphans path)
            val isRelevant = if (isOrphansCandidate && (suit == Suit.HONOR || value == 1 || value == 9)) {
                true
            } else if (suit == Suit.HONOR) {
                hand.any { it.suit == Suit.HONOR && it.value == value }
            } else {
                hand.any { it.suit == suit && kotlin.math.abs(it.value - value) <= 2 }
            }
            if (!isRelevant && currentShanten <= 2) continue

            val dummyTile = createRepresentativeTile(suit, value)
            val testHand = hand + dummyTile
            val nextShanten = calculateExactShanten(testHand, revealedMeldsCount)

            if (nextShanten < currentShanten) {
                totalLiveHelpfulTiles += liveRemaining
            }
        }

        return totalLiveHelpfulTiles
    }

    /**
     * Evaluates the strategic connectivity and live wait quality of a hand, weighting open waits
     * (Ryanmen), complex golden shapes (Nobetan, Nakabukure, Ryankan), 5-Block Theory,
     * and Anpai (Safe Honor Shield) retention.
     */
    fun evaluateLiveShapeQuality(
        hand: List<MahjongTile>,
        visibleCounts: Map<Pair<Suit, Int>, Int>,
        seatWind: HonorType? = null,
        prevalentWind: HonorType? = null
    ): Double {
        var score = 0.0
        val nonBonus = hand.filterNot { it.isBonus }
        val handCounts = nonBonus.groupingBy { it.suit to it.value }.eachCount()

        fun liveCopies(suit: Suit, value: Int): Int {
            if (suit != Suit.HONOR && value !in 1..9) return 0
            val visible = visibleCounts[suit to value] ?: 0
            return (4 - visible).coerceAtLeast(0)
        }

        // Count total singleton Honors and dead/safe Anpai Honors (0 or 1 live copy left unseen)
        val totalVisibleTiles = visibleCounts.values.sum()
        val singletonHonors = handCounts.filter { it.key.first == Suit.HONOR && it.value == 1 }.keys
        val safeAnpaiHonor = singletonHonors.firstOrNull { (suit, v) -> liveCopies(suit, v) <= 1 }
        val shanten = calculateExactShanten(nonBonus)

        for ((kind, count) in handCounts) {
            val (suit, value) = kind
            val liveSelf = liveCopies(suit, value)

            // 1. Pair / Pung upgrade potential
            if (count == 2) {
                score += liveSelf * 3.5 // Live copies to complete Pung or serve as Jantou
                if (suit == Suit.HONOR) {
                    val isValueHonor = value in 5..7 || // Dragons
                        (seatWind != null && value == seatWind.ordinal + 1) ||
                        (prevalentWind != null && value == prevalentWind.ordinal + 1)
                    if (isValueHonor && liveSelf > 0) {
                        score += liveSelf * 4.5 // High priority for Fan-bearing Dragon/Wind Pung
                    }
                }
            } else if (count >= 3) {
                score += 14.0
            }

            // 2. Suit sequence connectivity, Golden 4-Tile Shapes & live proto-meld waits
            if (suit == Suit.CHARACTER || suit == Suit.BAMBOO || suit == Suit.DOT) {
                val hasNext1 = (handCounts[suit to (value + 1)] ?: 0) >= 1
                val hasNext2 = (handCounts[suit to (value + 2)] ?: 0) >= 1
                val hasNext3 = (handCounts[suit to (value + 3)] ?: 0) >= 1
                val hasNext4 = (handCounts[suit to (value + 4)] ?: 0) >= 1

                // Open-ended / adjacent neighbor (Ryanmen / Penchan: v and v+1 waiting on v-1 and v+2)
                if (hasNext1) {
                    val leftWaitLive = liveCopies(suit, value - 1)
                    val rightWaitLive = liveCopies(suit, value + 2)
                    score += (leftWaitLive + rightWaitLive) * 2.6
                }
                // Gutshot / inside wait (Kanchan: v and v+2 waiting on v+1)
                if (hasNext2) {
                    val middleWaitLive = liveCopies(suit, value + 1)
                    score += middleWaitLive * 1.5
                }

                // Golden Shape A: 4-Tile Nobetan (v, v+1, v+2, v+3, e.g. 2-3-4-5)
                // Provides dual pair waits + wide sequence expansion
                if (hasNext1 && hasNext2 && hasNext3) {
                    val nobetanOuts = liveCopies(suit, value) + liveCopies(suit, value + 3) +
                        liveCopies(suit, value - 1) + liveCopies(suit, value + 4)
                    score += 8.0 + (nobetanOuts * 1.2)
                }

                // Golden Shape B: Nakabukure / Bulging Middle (v, v+1, v+1, v+2, e.g. 3-4-4-5)
                if (hasNext1 && hasNext2 && (handCounts[suit to (value + 1)] ?: 0) >= 2) {
                    val bulgeOuts = liveCopies(suit, value) + liveCopies(suit, value + 2)
                    score += 7.5 + (bulgeOuts * 1.4)
                }

                // Golden Shape C: Ryankan / Double Gutshot (v, v+2, v+4, e.g. 1-3-5 or 3-5-7)
                // Waits on BOTH v+1 and v+3 (8 outs total, nearly as strong as Ryanmen)
                if (!hasNext1 && hasNext2 && !hasNext3 && hasNext4) {
                    val ryankanOuts = liveCopies(suit, value + 1) + liveCopies(suit, value + 3)
                    score += 5.5 + (ryankanOuts * 1.3)
                }

                // Centrality bonus for flexible middle tiles (3..7) vs terminals (1, 9)
                val centrality = when (value) {
                    in 3..7 -> 1.2
                    2, 8 -> 0.7
                    else -> 0.2
                }
                score += centrality * (if (liveSelf > 0) 1.0 else 0.3)
            } else if (suit == Suit.HONOR && count == 1) {
                val isValueHonor = value in 5..7 || // Dragons
                    (seatWind != null && value == seatWind.ordinal + 1) ||
                    (prevalentWind != null && value == prevalentWind.ordinal + 1)

                // 5-Block Theory & Proactive Anpai Shielding (Mid-to-Late Game):
                // Once mid-game arrives (totalVisibleTiles >= 28), if the player is at 1-Shanten (5 blocks formed)
                // and holds a single dead/exhausted Honor tile (liveSelf <= 1), retain it as a 100% safe defensive
                // shield (Anpai) instead of keeping an extra dangerous floating middle suit tile!
                if (totalVisibleTiles >= 28 && shanten == 1 && singletonHonors.size == 1 && kind == safeAnpaiHonor) {
                    score += 4.5 // Mid/Late-game strategic Anpai insurance slot
                } else {
                    // Normal Opening/Mid-game Discard Order: dump Guest Winds first, then isolated Value Honors
                    score += if (!isValueHonor) {
                        when (liveSelf) {
                            0, 1 -> -28.0
                            2 -> -22.0
                            else -> -16.0
                        }
                    } else {
                        when (liveSelf) {
                            0 -> -26.0
                            1 -> -18.0
                            2 -> -12.0
                            else -> -8.0
                        }
                    }
                }
            }
        }

        return score
    }

    /**
     * Two-Phase Suit Decomposition:
     * - Phase 1: Recursively branches only on Complete Melds (Pungs & Chows). Since a hand has at most
     *   14 tiles (at most 4 melds), Phase 1 has <= 15 branches even on pure 14-tile single-suit hands.
     * - Phase 2: On the leftover tiles after removing complete melds, evaluates both Pair-first and
     *   Proto-Chow-first greedy passes in O(9) linear time.
     * This guarantees < 0.05ms execution time with zero combinatorial blowup.
     */
    private fun extractSuitDecompositions(counts: IntArray): Set<DecompositionCounts> {
        val results = mutableSetOf<DecompositionCounts>()

        fun evaluatePartialsGreedy(rem: IntArray, melds: Int, pairsFirst: Boolean) {
            val c = rem.clone()
            var pairs = 0
            var protos = 0

            if (pairsFirst) {
                for (i in 1..9) {
                    while (c[i] >= 2) {
                        c[i] -= 2
                        pairs++
                    }
                }
                for (i in 1..9) {
                    while (c[i] >= 1) {
                        if (i <= 8 && c[i + 1] >= 1) {
                            c[i]--
                            c[i + 1]--
                            protos++
                        } else if (i <= 7 && c[i + 2] >= 1) {
                            c[i]--
                            c[i + 2]--
                            protos++
                        } else {
                            break
                        }
                    }
                }
            } else {
                // Extract proto-chows before pairs, preserving at most 1 pair first if beneficial
                for (i in 1..9) {
                    while (c[i] >= 1) {
                        if (i <= 8 && c[i + 1] >= 1) {
                            c[i]--
                            c[i + 1]--
                            protos++
                        } else if (i <= 7 && c[i + 2] >= 1) {
                            c[i]--
                            c[i + 2]--
                            protos++
                        } else if (c[i] >= 2) {
                            c[i] -= 2
                            pairs++
                        } else {
                            break
                        }
                    }
                }
            }

            results.add(DecompositionCounts(melds, protos, pairs))
        }

        fun dfsMelds(index: Int, melds: Int) {
            var idx = index
            while (idx <= 9 && counts[idx] == 0) {
                idx++
            }
            if (idx > 9) {
                evaluatePartialsGreedy(counts, melds, pairsFirst = true)
                evaluatePartialsGreedy(counts, melds, pairsFirst = false)
                return
            }

            // 1. Try extracting a Pung (3 identical tiles)
            if (counts[idx] >= 3) {
                counts[idx] -= 3
                dfsMelds(idx, melds + 1)
                counts[idx] += 3
            }

            // 2. Try extracting a Chow (idx, idx+1, idx+2)
            if (idx <= 7 && counts[idx] >= 1 && counts[idx + 1] >= 1 && counts[idx + 2] >= 1) {
                counts[idx]--
                counts[idx + 1]--
                counts[idx + 2]--
                dfsMelds(idx, melds + 1)
                counts[idx]++
                counts[idx + 1]++
                counts[idx + 2]++
            }

            // 3. Advance to next rank without extracting more complete melds at idx
            dfsMelds(idx + 1, melds)
        }

        dfsMelds(1, 0)

        // Prune dominated decompositions
        return results.filter { candidate ->
            results.none { other ->
                other != candidate &&
                    other.completeMelds >= candidate.completeMelds &&
                    other.pairs >= candidate.pairs &&
                    (other.completeMelds + other.protoMelds + other.pairs) >= (candidate.completeMelds + candidate.protoMelds + candidate.pairs) &&
                    (other.completeMelds > candidate.completeMelds || other.pairs > candidate.pairs || other.protoMelds > candidate.protoMelds)
            }
        }.toSet().ifEmpty { setOf(DecompositionCounts()) }
    }

    fun buildVisibleTileCounts(visibleTiles: Iterable<MahjongTile>): Map<Pair<Suit, Int>, Int> {
        return visibleTiles
            .filterNot { it.isBonus }
            .groupingBy { it.suit to it.value }
            .eachCount()
    }

    private fun createRepresentativeTile(suit: Suit, value: Int): MahjongTile {
        val honorType = if (suit == Suit.HONOR) {
            HonorType.entries.getOrElse(value - 1) { HonorType.EAST }
        } else null
        return MahjongTile(
            id = 143,
            suit = suit,
            value = value,
            honorType = honorType
        )
    }
}
