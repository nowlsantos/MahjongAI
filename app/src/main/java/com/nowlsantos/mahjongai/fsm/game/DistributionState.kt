package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.presentation.TileDistributor
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class DistributionState : GamePhaseState {
    private val tag = "DistributionState"
    lateinit var tileDistributor: TileDistributor
        private set

    //var onStartDistribution: (() -> Unit)? = null

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: DistributionState")
        machine.updateState { it.copy(gamePhase = GamePhase.DISTRIBUTION) }
        tileDistributor = TileDistributor()
        Logger.d(tag, "TileDistributor initialized")

        // Trigger distribution start
        machine.emitUiEffect(UiEffect.ExecuteDistribution(tileDistributor = tileDistributor))
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when (input) {
            GameInput.DistributionComplete -> {
                Logger.d(tag, "Received DistributionComplete. Preparing transition...")
                // delay for 1 second before transition
                delay(1000L.milliseconds)
                machine.transitionTo(GamePhase.PLAYING)
            }
            else -> {
                Logger.w(tag, "Input $input not handled in DistributionState.")
            }
        }
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "Exit:: Distribution complete. Transitioning to PlayingState.")
    }
}
