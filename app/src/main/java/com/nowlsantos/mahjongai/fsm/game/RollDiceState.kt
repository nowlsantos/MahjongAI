package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.util.Logger

class RollDiceState : GamePhaseState {
    private val tag = "RollDiceState"

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: RollDiceState")
        // You can emit effects here
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when (input) {
            is GameInput.RollForDealer -> {
                Logger.d(tag, "Received RollForDealer input.")
            }
            is GameInput.RollForTargetWall -> {
                Logger.d(tag, "Received RollForTargetWall input.")
            }
            is GameInput.RollForWallBreak -> {
                Logger.d(tag, "Received RollForWallBreak input.")
            }
            GameInput.GoDistribute -> {
                Logger.d(tag, "Received GoDistribute input. Preparing transition...")
                machine.transitionTo(GamePhase.DISTRIBUTION)
            }
            else -> {
                Logger.w(tag, "Input $input not handled in RollDiceState.")
            }
        }
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "Exit:: RollDiceState complete. Transitioning to DistributionState.")
        // Maybe we can set the DiceView to false here?
    }
}
