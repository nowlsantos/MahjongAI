package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.GameOverData
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.Logger

class GameOverState : GamePhaseState {
    private val tag = "GameOverState"

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: GameOverState")

        val state = machine.currentState
        val ranked = state.players.values.sortedByDescending { it.score }

        machine.updateState {
            it.copy(
                gameOverData = GameOverData(rankedPlayers = ranked),
                isGameActive = false
            )
        }

        Logger.d(tag, "Game Over! Final Standings: ${ranked.map { "${it.playerName}: ${it.score}" }}")
        machine.emitUiEffect(UiEffect.RefreshUI)
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when (input) {
            is GameInput.RestartMatch -> {
                Logger.d(tag, "Restarting entire match from Round 1...")
                machine.updateState {
                    it.copy(
                        roundNumber = 1,
                        dealerIndex = 0,
                        prevalentWind = HonorType.EAST,
                        roundEndData = null,
                        gameOverData = null,
                        players = GameState.initializePlayers(0),
                        isGameActive = false,
                        publicDiscards = emptyList(),
                        lastDiscardedTile = null,
                        lastDiscarderIndex = null
                    )
                }
                machine.transitionTo(GamePhase.SETUP)
            }
            else -> {}
        }
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "Exit:: GameOverState")
    }
}
