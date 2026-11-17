package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.ui.animations.AnimationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameStateMachine(
    internal val externalScope: CoroutineScope,
    private val stateMap: Map<GamePhase, GamePhaseState>
) {
    private val transitionMutex = Mutex()
    private var isInitialized = false

    var waitForAnimation: suspend () -> Unit = {}
    var waitForHumanFlower: suspend () -> Unit = {}
    var performMeldAnimation: suspend (playerIndex: Int, tile: MahjongTile, seat: MahjongSeat, animationType: AnimationType) -> Unit = { _, _, _, _ -> }
    var performDraw: suspend (playerIndex: Int) -> MahjongTile? = { null }
    var performDrawReplacement: suspend (playerIndex: Int) -> MahjongTile? = { null }
    var performBonusMeld: suspend (playerIndex: Int, tile: MahjongTile) -> Unit = { _, _ -> }
    var performDiscard: suspend (playerIndex: Int, tile: MahjongTile) -> Unit = { _, _ -> }
    var performKongMeld: suspend (playerIndex: Int, action: TurnAction) -> MahjongTile? = { _, _ -> null }

    // Read-only public exposure of the current state snapshot
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // Current state snapshot accessor for phase states
    val currentState: GameState get() = _state.value
    val currentPhase: GamePhase get() = _state.value.gamePhase

    // MutableSharedFlow for one-time UI effects (e.g., showing messages, animations).
    // Buffered to prevent dropping or blocking fast back-to-back effects.
    private val _uiEffect = MutableSharedFlow<UiEffect>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val uiEffect: SharedFlow<UiEffect> = _uiEffect.asSharedFlow()

    // Explicit suspend function for entering the initial phase: called in GameViewModel
    suspend fun initialize() {
        val shouldEnter = transitionMutex.withLock {
            if (isInitialized) {
                false
            } else {
                isInitialized = true
                true
            }
        }
        if (shouldEnter) {
            stateMap[currentPhase]?.onEnter(this)
        }
    }

    /**
     * Safely transitions the machine from the current GamePhaseState to a new one.
     * Executes lifecycle hooks in sequence: activePhase.onExit -> update -> newPhase.onEnter
     */
    suspend fun transitionTo(nextPhase: GamePhase) {
        transitionMutex.withLock {
            val prev = currentPhase
            if (nextPhase == prev) return
            stateMap[prev]?.onExit(this)
            _state.update { it.copy(gamePhase = nextPhase) }
        }
        stateMap[nextPhase]?.onEnter(this)
    }

    /**
     * Functional reducer helper to update the immutable GameState payload atomically.
     */
    fun updateState(reducer: (GameState) -> GameState) {
        _state.update(reducer)
    }

    suspend fun handleInput(input: GameInput) {
        stateMap[currentPhase]?.handleInput(this, input)
    }

    fun emitUiEffect(effect: UiEffect) {
        if (!_uiEffect.tryEmit(effect)) {
            externalScope.launch {
                _uiEffect.emit(effect)
            }
        }
    }
}