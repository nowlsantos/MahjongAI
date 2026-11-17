package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.GameInput

/**
 * Interface defining the contract for each game phase state.
 * Each state is responsible for handling inputs relevant to its phase
 * and potentially triggering transitions to other states.
 */
interface GamePhaseState {
    /**
     * Called when the state machine transitions into this phase.
     * Use this for initial setup or logging for the phase.
     */
    suspend fun onEnter(machine: GameStateMachine)

    /**
     * Handles a game input for the current phase.
     * May update the game state or trigger a transition to a new phase.
     * Marked as suspend because it might call suspend functions (e.g., emitUiEffect, delay).
     */
    suspend fun handleInput(machine: GameStateMachine, input: GameInput)

    /**
     * Called when the state machine transitions out of this phase.
     * Use this for cleanup or final actions before leaving the phase.
     */
    suspend fun onExit(machine: GameStateMachine)
}