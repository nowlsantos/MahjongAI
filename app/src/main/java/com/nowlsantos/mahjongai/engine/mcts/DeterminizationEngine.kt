package com.nowlsantos.mahjongai.engine.mcts

import com.nowlsantos.mahjongai.model.MahjongTile

class DeterminizationEngine(
    private val fullTileSet: List<MahjongTile>
) {

    /**
     * Generates a plausible hidden hand for an opponent, ensuring we don't
     * assign tile copies that are already visible in the player's hand, public discards,
     * or any exposed melds. Matches by both unique tile ID and (suit, value) copy quota (max 4).
     */
    fun sampleOpponentHand(
        playerHand: List<MahjongTile>,
        publicDiscards: List<MahjongTile>,
        exposedMelds: List<List<MahjongTile>>,
        targetHandSize: Int
    ): List<MahjongTile> {
        val visibleTiles = buildList {
            addAll(playerHand)
            addAll(publicDiscards)
            exposedMelds.forEach { addAll(it) }
        }

        val visibleIds = visibleTiles.map { it.id }.toSet()
        val remainingQuotaByKind = mutableMapOf<Pair<com.nowlsantos.mahjongai.model.Suit, Int>, Int>()

        for (tile in fullTileSet) {
            val key = tile.suit to tile.value
            val maxCopies = if (tile.isBonus) 1 else 4
            remainingQuotaByKind[key] = maxCopies
        }

        for (visible in visibleTiles) {
            val key = visible.suit to visible.value
            remainingQuotaByKind[key] = ((remainingQuotaByKind[key] ?: 0) - 1).coerceAtLeast(0)
        }

        val availablePool = mutableListOf<MahjongTile>()
        for (tile in fullTileSet) {
            if (tile.id in visibleIds) continue
            val key = tile.suit to tile.value
            val quota = remainingQuotaByKind[key] ?: 0
            if (quota > 0) {
                availablePool.add(tile)
                remainingQuotaByKind[key] = quota - 1
            }
        }

        availablePool.shuffle()
        return availablePool.take(targetHandSize)
    }
}
