package com.nowlsantos.mahjongai.di

import com.nowlsantos.mahjongai.fsm.game.ArrangingState
import com.nowlsantos.mahjongai.fsm.game.ClaimInterceptionState
import com.nowlsantos.mahjongai.fsm.game.DistributionState
import com.nowlsantos.mahjongai.fsm.game.GameOverState
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.fsm.game.GameStateMachine
import com.nowlsantos.mahjongai.fsm.game.PlayingState
import com.nowlsantos.mahjongai.fsm.game.RollDiceState
import com.nowlsantos.mahjongai.fsm.game.RoundEndState
import com.nowlsantos.mahjongai.fsm.game.SetupState
import com.nowlsantos.mahjongai.model.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModel

val appModule = module {
    // Provide a shared SupervisorJob-backed CoroutineScope for state machines
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Provide individual phase states
    factory { SetupState() }
    factory { RollDiceState() }
    factory { ArrangingState() }
    factory { DistributionState() }
    factory { PlayingState() }
    factory { ClaimInterceptionState() }
    factory { RoundEndState() }
    factory { GameOverState() }

    // Provide GameStateMachine as a singleton so its state persists across screen recreations
    single { GameStateMachine(
        stateMap = mapOf(
            GamePhase.SETUP to get<SetupState>(),
            GamePhase.ROLLING_DICE to get<RollDiceState>(),
            GamePhase.DISTRIBUTION to get<DistributionState>(),
            GamePhase.ARRANGING to get<ArrangingState>(),
            GamePhase.PLAYING to get<PlayingState>(),
            GamePhase.CLAIM_INTERCEPTION to get<ClaimInterceptionState>(),
            GamePhase.ROUND_END to get<RoundEndState>(),
            GamePhase.GAME_OVER to get<GameOverState>()
        ),
        externalScope = get())
    }

    // Provide the ViewModel using Koin's viewModel DSL, injecting dependencies
    viewModel { GameViewModel(stateMachine = get()) }
}
