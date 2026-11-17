package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class ArrangingState : GamePhaseState {
    private val tag = "ArrangingState"

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: ArrangingState")

        // Access and log each player's tile collection from GameState.players
        val players = machine.currentState.players
        players.forEach { (playerId, playerState) ->
            Logger.d(
                tag,
                "Player $playerId (${playerState.playerName}) - Seat: ${playerState.seat} - Hand (${playerState.hand.size} tiles): ${playerState.hand}"
            )
        }

        delay(1000L.milliseconds)
        machine.transitionTo(GamePhase.PLAYING)
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        // Automated playing phase
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "ArrangingState complete. Transitioning to PlayingState.")
    }
}
