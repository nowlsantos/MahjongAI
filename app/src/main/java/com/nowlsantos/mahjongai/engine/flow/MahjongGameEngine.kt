package com.nowlsantos.mahjongai.engine.flow

import com.nowlsantos.mahjongai.model.MahjongTile

enum class TurnStage {
    DRAW_PHASE, DISCARD_PHASE, CLAIM_WINDOW, ROUND_OVER
}

class MahjongGameEngine(
    val playersCount: Int = 4,
    val minFanRequired: Int = 0
) {

    var activePlayerIndex: Int = 0
        private set
    var currentStage: TurnStage = TurnStage.DRAW_PHASE
        private set

    private val wall: MutableList<MahjongTile> = mutableListOf()
    private val playerHands = Array(4) { mutableListOf<MahjongTile>() }
    private val publicDiscards = mutableListOf<MahjongTile>()
    var lastDiscardedTile: MahjongTile? = null
        private set

    fun initializeRound(shuffledDeck: List<MahjongTile>) {
        wall.clear()
        wall.addAll(shuffledDeck)
        publicDiscards.clear()
        lastDiscardedTile = null

        // Deal 13 tiles to each player
        for (i in 0 until 4) {
            playerHands[i].clear()
            repeat(13) {
                if (wall.isNotEmpty()) {
                    playerHands[i].add(wall.removeAt(0))
                }
            }
        }

        activePlayerIndex = 0
        currentStage = TurnStage.DRAW_PHASE
    }

    fun executeDraw(): MahjongTile? {
        if (currentStage != TurnStage.DRAW_PHASE || wall.isEmpty()) return null

        val drawnTile = wall.removeAt(0)
        playerHands[activePlayerIndex].add(drawnTile)
        currentStage = TurnStage.DISCARD_PHASE
        return drawnTile
    }

    fun executeDiscard(playerIndex: Int, tile: MahjongTile): Boolean {
        if (playerIndex != activePlayerIndex || currentStage != TurnStage.DISCARD_PHASE) return false

        playerHands[playerIndex].remove(tile)
        lastDiscardedTile = tile
        publicDiscards.add(tile)
        currentStage = TurnStage.CLAIM_WINDOW
        return true
    }

    fun processClaims(intents: List<ClaimIntent>): Boolean {
        if (currentStage != TurnStage.CLAIM_WINDOW) return false

        val winningClaim = ClaimResolver.resolveHighestClaim(activePlayerIndex, intents)

        if (winningClaim == null) {
            // No claims made: advance turn to next player
            activePlayerIndex = (activePlayerIndex + 1) % 4
            currentStage = TurnStage.DRAW_PHASE
        } else {
            // Process successful claim
            activePlayerIndex = winningClaim.playerIndex
            if (winningClaim.claimType == ClaimType.WIN) {
                currentStage = TurnStage.ROUND_OVER
            } else {
                currentStage = TurnStage.DISCARD_PHASE
            }
        }

        return true
    }
}