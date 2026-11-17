package com.nowlsantos.mahjongai.model

import com.nowlsantos.mahjongai.engine.logic.TingCalculator

enum class ActionType {
    DISCARD, DRAW
}

data class MahjongAction(
    val type: ActionType,
    val tile: MahjongTile
)

data class MahjongGameState(
    val playerHand: List<MahjongTile>,
    val revealedDiscards: List<MahjongTile>,
    val remainingWallSize: Int,
    val isTerminal: Boolean,
    val scoreEvaluation: Double // Evaluated utility from heuristic or rollout
) {
    fun getLegalActions(): List<MahjongAction> {
        if (isTerminal) return emptyList()
        val turnActions = com.nowlsantos.mahjongai.engine.flow.MoveGenerator.getLegalTurnActions(
            playerHand = playerHand,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST
        )
        return turnActions.filterIsInstance<com.nowlsantos.mahjongai.engine.flow.TurnAction.Discard>().map {
            MahjongAction(ActionType.DISCARD, it.tile)
        }
    }

    fun applyAction(action: MahjongAction): MahjongGameState {
        val newHand = playerHand.toMutableList()
        newHand.remove(action.tile)
        val newDiscards = revealedDiscards + action.tile

        // Check if hand reached winning condition or wall depleted
        val terminal = newHand.isEmpty() || remainingWallSize <= 0
        val eval = if (terminal) evaluateHand(newHand) else 0.0

        return MahjongGameState(
            playerHand = newHand,
            revealedDiscards = newDiscards,
            remainingWallSize = remainingWallSize - 1,
            isTerminal = terminal,
            scoreEvaluation = eval
        )
    }

    private fun evaluateHand(hand: List<MahjongTile>): Double {
        // Basic heuristic: Reward lower tile distance or winning structure
        return 100.0 / (TingCalculator.calculateTileDistance(hand) + 1.0)
    }
}