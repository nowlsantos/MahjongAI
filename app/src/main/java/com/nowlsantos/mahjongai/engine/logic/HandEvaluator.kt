package com.nowlsantos.mahjongai.engine.logic

import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit

object HandEvaluator {
    /**
     * Validates if a player's hand forms a winning configuration for Hong Kong Mahjong.
     * Takes into account already revealed/exposed melds (Chow, Pung, Kong).
     *
     * Expected tile count formula:
     * - 0 revealed melds: concealed hand must have 14 tiles (4 melds + 1 pair, or 7 pairs, or 13 orphans)
     * - 1 revealed meld:  concealed hand must have 11 tiles (3 melds + 1 pair)
     * - 2 revealed melds: concealed hand must have 8 tiles  (2 melds + 1 pair)
     * - 3 revealed melds: concealed hand must have 5 tiles  (1 meld + 1 pair)
     * - 4 revealed melds: concealed hand must have 2 tiles  (0 melds + 1 pair)
     *
     * @param tiles Concealed tiles currently held (including drawn/claimed 14th tile)
     * @param revealedMeldsCount Number of already exposed melds on the board (0..4)
     */
    fun isWinningHand(tiles: List<MahjongTile>, revealedMeldsCount: Int = 0): Boolean {
        val nonBonusTiles = tiles.filterNot { it.isBonus }
        val requiredMelds = 4 - revealedMeldsCount
        val expectedTileCount = (requiredMelds * 3) + 2

        if (nonBonusTiles.size != expectedTileCount || requiredMelds < 0) return false

        // Special hands (Concealed hand only: 0 revealed melds, 14 tiles)
        if (revealedMeldsCount == 0 && nonBonusTiles.size == 14) {
            if (isSevenPairs(nonBonusTiles)) return true
            if (isThirteenOrphans(nonBonusTiles)) return true
        }

        val sortedTiles = nonBonusTiles.sorted()
        val countsByKind = sortedTiles.groupingBy { it.suit to it.value }.eachCount()

        // Try every unique tile kind as the candidate pair
        val uniqueCandidatePairs = sortedTiles.distinctBy { it.suit to it.value }
        for (pairTile in uniqueCandidatePairs) {
            val kind = pairTile.suit to pairTile.value
            if ((countsByKind[kind] ?: 0) >= 2) {
                // Remove 2 tiles of this kind and check if the remaining tiles form the required melds
                val remaining = sortedTiles.toMutableList()
                repeat(2) {
                    val idx = remaining.indexOfFirst { it.suit == pairTile.suit && it.value == pairTile.value }
                    if (idx != -1) remaining.removeAt(idx)
                }

                if (canFormMelds(remaining, requiredMelds)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if a 14-tile concealed hand forms Seven Pairs (七對子 / 嚦咕嚦咕).
     * Accepts 7 distinct pairs or pairs including 4 of a kind (counted as two pairs).
     */
    fun isSevenPairs(tiles: List<MahjongTile>): Boolean {
        val nonBonus = tiles.filterNot { it.isBonus }
        if (nonBonus.size != 14) return false
        val counts = nonBonus.groupingBy { it.suit to it.value }.eachCount()
        return counts.values.all { it == 2 || it == 4 } && counts.values.sumOf { it / 2 } == 7
    }

    /**
     * Checks if a 14-tile concealed hand forms Thirteen Orphans (Sap Sam Jiu / 十三幺).
     * Requires 1 and 9 of each suit (6 tiles), 4 winds (East, South, West, North),
     * 3 dragons (Red, Green, White) + 1 duplicate pair of any of those 13 tiles.
     */
    fun isThirteenOrphans(tiles: List<MahjongTile>): Boolean {
        val nonBonus = tiles.filterNot { it.isBonus }
        if (nonBonus.size != 14) return false
        val counts = nonBonus.groupingBy { it.suit to it.value }.eachCount()
        val requiredKinds = setOf(
            Suit.CHARACTER to 1, Suit.CHARACTER to 9,
            Suit.BAMBOO to 1, Suit.BAMBOO to 9,
            Suit.DOT to 1, Suit.DOT to 9,
            Suit.HONOR to 1, Suit.HONOR to 2, Suit.HONOR to 3, Suit.HONOR to 4,
            Suit.HONOR to 5, Suit.HONOR to 6, Suit.HONOR to 7
        )
        return counts.keys.containsAll(requiredKinds) && counts.size == 13
    }

    private fun canFormMelds(tiles: MutableList<MahjongTile>, remainingMeldsNeeded: Int): Boolean {
        if (tiles.isEmpty() && remainingMeldsNeeded == 0) return true
        if (tiles.isEmpty() || remainingMeldsNeeded <= 0) return false

        // Take the first tile and try to form a Pung or a Chow
        val first = tiles[0]

        // 1. Try forming a Pung (3 identical tiles by suit and value)
        val identicalCount = tiles.count { it.suit == first.suit && it.value == first.value }
        if (identicalCount >= 3) {
            val subList = tiles.toMutableList()
            repeat(3) {
                val idx = subList.indexOfFirst { it.suit == first.suit && it.value == first.value }
                if (idx != -1) subList.removeAt(idx)
            }
            if (canFormMelds(subList, remainingMeldsNeeded - 1)) return true
        }

        // 2. Try forming a Chow (3 sequential numerical tiles in the same suit)
        if (!first.isHonor && !first.isBonus) {
            val second = tiles.find { it.suit == first.suit && it.value == first.value + 1 }
            val third = tiles.find { it.suit == first.suit && it.value == first.value + 2 }

            if (second != null && third != null) {
                val subList = tiles.toMutableList()
                subList.remove(first)
                subList.remove(second)
                subList.remove(third)
                if (canFormMelds(subList, remainingMeldsNeeded - 1)) return true
            }
        }

        return false
    }
}
