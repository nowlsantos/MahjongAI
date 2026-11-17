package com.nowlsantos.mahjongai.engine.defense

import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit

class SafetyTileAnalyzer(private val numberOfPlayers: Int = 4) {

    // Tracks all discards categorized by player index (0 to 3)
    private val playerDiscards = mutableMapOf<Int, MutableList<MahjongTile>>().apply {
        for (i in 0 until numberOfPlayers) put(i, mutableListOf())
    }

    fun recordDiscard(playerIndex: Int, tile: MahjongTile) {
        playerDiscards[playerIndex]?.add(tile)
    }

    /**
     * Determines if a tile is an Absolute Safe MahjongTile (Genbutsu) against a specific opponent.
     * Rule: A tile an opponent has previously discarded themselves is completely safe
     * against that specific opponent.
     */
    fun isAbsoluteSafeForPlayer(tile: MahjongTile, targetPlayerIndex: Int): Boolean {
        val discards = playerDiscards[targetPlayerIndex] ?: return false
        return discards.any { it.suit == tile.suit && it.value == tile.value }
    }

    /**
     * Computes a comprehensive tactical danger score for a candidate discard
     * (0.0 = completely safe, 1.0 = extremely dangerous).
     *
     * Accounts for:
     * 1. Exhaustion / Scarcity (3 visible copies = 4th copy cannot form Pung/Pair, very safe).
     * 2. Honor Tile Safety (1-2 copies visible makes Honors very safe; 0 visible late-game can be a Dragon Pung trap).
     * 3. Kabe (No-Chance Wall Barrier): If all 4 copies of a suit rank (e.g. 3 Dot) are visible on the board,
     *    opponents cannot hold a [2-3] or [3-4] sequence wait, making 1 Dot and 2 Dot much safer!
     * 4. Suji (Half-Safety): If 4, 5, or 6 of the same suit is heavily discarded, 1/7, 2/8, or 3/9 have reduced Ryanmen risk.
     * 5. Central Tile Connectivity: Middle suit tiles (4, 5, 6) can deal into the widest range of Chow waits.
     */
    fun calculateDangerScore(tile: MahjongTile, visibleBoardTiles: List<MahjongTile>): Double {
        if (tile.isBonus) return 0.0

        val visibleCounts = visibleBoardTiles
            .filterNot { it.isBonus }
            .groupingBy { it.suit to it.value }
            .eachCount()

        val selfVisible = visibleCounts[tile.suit to tile.value] ?: 0

        // 1. If 3 or 4 copies are already visible on the board (including the candidate in hand), it's nearly 100% safe
        if (selfVisible >= 4) return 0.02
        if (selfVisible == 3) return 0.06

        // 2. Honor tiles (Winds & Dragons) can never be claimed for Chows
        if (tile.suit == Suit.HONOR) {
            return when (selfVisible) {
                2 -> 0.12 // Only 1 copy left unseen -> impossible for opponent to have a concealed Pung wait, only single pair wait
                1 -> 0.25 // Live honor
                else -> 0.40 // Completely unseen honor
            }
        }

        // 3. Suit Tiles (Character, Bamboo, Dot): Evaluate Chow & Pung danger + Kabe (Wall Barriers) + Suji
        var danger = when (tile.value) {
            in 4..6 -> 0.68 // Central tiles have maximum Ryanmen (two-sided) exposure
            3, 7 -> 0.55    // Semi-central tiles
            2, 8 -> 0.38    // Near-terminal tiles
            else -> 0.24    // Terminals (1, 9) only deal into single-sided edge waits or pairs
        }

        // Kabe (Wall Barrier) Reduction:
        // For low tiles (1, 2): if 3 or 4 is exhausted (all 4 visible), sequence waits across that rank are impossible
        if (tile.value in 1..2 && ((visibleCounts[tile.suit to 3] ?: 0) >= 4 || (visibleCounts[tile.suit to 4] ?: 0) >= 4)) {
            danger *= 0.35
        }
        // For high tiles (8, 9): if 7 or 6 is exhausted (all 4 visible), sequence waits across that rank are impossible
        if (tile.value in 8..9 && ((visibleCounts[tile.suit to 7] ?: 0) >= 4 || (visibleCounts[tile.suit to 6] ?: 0) >= 4)) {
            danger *= 0.35
        }

        // Suji (1-4-7, 2-5-8, 3-6-9) Reduction: if the middle connector is already visible in discards
        val sujiMiddle = when (tile.value) {
            1, 7 -> 4
            2, 8 -> 5
            3, 9 -> 6
            else -> null
        }
        if (sujiMiddle != null && (visibleCounts[tile.suit to sujiMiddle] ?: 0) >= 2) {
            danger *= 0.75
        }

        // Pair/Shanpon scarcity adjustment
        if (selfVisible == 2) {
            danger -= 0.08
        }

        return danger.coerceIn(0.02, 0.95)
    }
}
