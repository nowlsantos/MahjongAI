package com.nowlsantos.mahjongai.model

import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerWall
import com.nowlsantos.mahjongai.model.WallStack

object WallBuilder {
    fun createWallsFromDeck(shuffledDeck: List<MahjongTile>): Map<Int, PlayerWall> {
        require(shuffledDeck.size == 144) {
            "Deck must contain exactly 144 tiles to construct standard walls. Found: ${shuffledDeck.size}"
        }

        val playerSegments = shuffledDeck.chunked(36)

        return playerSegments.mapIndexed { playerIndex, segment ->
            val stacks = segment.chunked(2).mapIndexed { stackIdx, tilePair ->
                WallStack(
                    stackIndex = stackIdx,
                    topTile = tilePair[0],
                    bottomTile = tilePair[1],
                    isTopDrawn = false,
                    isBottomDrawn = false
                )
            }

            playerIndex to PlayerWall(
                playerIndex = playerIndex,
                stacks = stacks,
                currentStackPointer = 0
            )
        }.toMap()
    }
}