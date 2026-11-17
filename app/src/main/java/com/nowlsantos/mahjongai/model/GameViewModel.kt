package com.nowlsantos.mahjongai.model

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.fsm.game.GameStateMachine
import com.nowlsantos.mahjongai.presentation.DiceResult
import com.nowlsantos.mahjongai.presentation.DiceRoller
import com.nowlsantos.mahjongai.presentation.TileDistributor
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.ui.animations.AnimationType
import com.nowlsantos.mahjongai.util.Logger
import com.nowlsantos.mahjongai.validation.GameValidator
import com.nowlsantos.mahjongai.validation.ValidationResult
import com.nowlsantos.mahjongai.validation.isError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class GameViewModel(
    private val stateMachine: GameStateMachine
) : ViewModel() {
    private val tag = "GameViewModel"
    val gameState: StateFlow<GameState> = stateMachine.state

    private val _uiState = MutableStateFlow(UIState())
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    val gameValidator = GameValidator()
    /* ---------- EFFECTS ---------- */
    val uiEffect: SharedFlow<UiEffect> = stateMachine.uiEffect

    private var drawAnimationDeferred: CompletableDeferred<Unit>? = null
    private var discardAnimationDeferred: CompletableDeferred<Unit>? = null
    private var bonusAnimationDeferred: CompletableDeferred<Unit>? = null
    private var meldAnimationDeferred: CompletableDeferred<Unit>? = null
    private var humanBonusDeferred: CompletableDeferred<Unit>? = null
    private val animationMutex = Mutex()
    private val kongPaymentCounter = AtomicLong(0L)

    companion object {
        /**
         * OPTIMIZATION 1: Cached Comparator.
         * Pre-allocated static comparator eliminates repeated object allocations and lambda closures
         * during hand sorting (dealing, player sorting, scoring checks).
         */
        private val TILE_COMPARATOR = compareBy<MahjongTile>({ it.suit.ordinal }, { it.value }, { it.id })
    }

    init {
        Logger.d(tag, "init")

        // Wire game action callbacks on GameStateMachine
        stateMachine.performDraw = { playerIndex ->
            drawRegularTile(playerIndex)
        }
        stateMachine.performDrawReplacement = { playerIndex ->
            drawReplacementTile(playerIndex)
        }
        stateMachine.performBonusMeld = { playerIndex, tile ->
            val seat = PlayerState.getMahjongSeat(0, playerIndex)
            awaitBonusAnimation(tile, playerIndex, seat)
        }
        stateMachine.performDiscard = { playerIndex, tile ->
            val seat = PlayerState.getMahjongSeat(0, playerIndex)
            awaitDiscardAnimation(tile, playerIndex, seat)
        }
        stateMachine.performKongMeld = { playerIndex, action ->
            performInTurnKong(playerIndex, action)
        }
        stateMachine.performMeldAnimation = { playerIndex, tile, seat, animType ->
            awaitMeldAnimation(tile, playerIndex, seat, animType, isFromDiscard = true)
        }

        stateMachine.waitForHumanFlower = {
            humanBonusDeferred = CompletableDeferred()
            humanBonusDeferred?.await()
            humanBonusDeferred = null
        }

        viewModelScope.launch {
            // Listen to machine effects BEFORE initializing so initial effects are never dropped
            launch {
                stateMachine.uiEffect.collect { effect ->
                    when(effect) {
                        is UiEffect.ShowEastWindBadge -> {
                            _uiState.update { it.copy(eastWindBadgePosition = effect.mahjongSeat) }
                        }

                        is UiEffect.ExecuteDistribution -> {
                            executeDistribution(distributor = effect.tileDistributor)
                        }

                        is UiEffect.RefreshUI -> {
                            val currentDealer = gameState.value.dealerIndex
                            val dealerSeat = PlayerState.getMahjongSeat(0, currentDealer)
                            _uiState.update {
                                it.copy(
                                    playerStates = gameState.value.players,
                                    selectedTileIds = emptySet(),
                                    eastWindBadgePosition = if (gameState.value.firstRollResult != null || gameState.value.roundNumber > 1) dealerSeat else null,
                                    eastWindPlayerIndex = currentDealer
                                )
                            }
                        }

                        is UiEffect.PromptHumanClaim -> {
                            val nonPassClaims = effect.legalClaims
                                .filter { it.claimType != com.nowlsantos.mahjongai.engine.flow.ClaimType.PASS }
                                .map { it.claimType }
                                .toSet()
                            _uiState.update {
                                it.copy(
                                    availableClaims = nonPassClaims,
                                    legalClaimIntents = effect.legalClaims
                                )
                            }
                        }

                        is UiEffect.ClearHumanClaim -> {
                            _uiState.update {
                                it.copy(
                                    availableClaims = emptySet(),
                                    legalClaimIntents = emptyList(),
                                    selectedTileIds = emptySet()
                                )
                            }
                        }

                        is UiEffect.ShowKongPayment -> {
                            val eventId = kongPaymentCounter.incrementAndGet()
                            _uiState.update {
                                it.copy(
                                    lastKongPaymentDeltas = effect.deltas,
                                    kongPaymentEventId = eventId
                                )
                            }
                        }

                        is UiEffect.TriggerAiDrawRegularTile -> { drawRegularTile(effect.playerIndex) }
                        else -> null
                    }
                }
            }

            // Safe coroutine boundary to trigger the state machine's initial onEnter
            stateMachine.initialize()
        }
    }

    /* ---------- UI → DOMAIN ---------- */
    fun onEvent(input: GameInput) {
        viewModelScope.launch {
            when (input) {
                is GameInput.GoDistribute,
                is GameInput.SortBySuit -> { sortBySuit() }
                is GameInput.DistributionComplete -> { stateMachine.handleInput(input) }
                is GameInput.RollForDealer -> { rollForDealer(input.result) }
                is GameInput.RollForTargetWall -> { rollForTargetWall(input.result) }
                is GameInput.RollForWallBreak -> { rollForWallBreak(input.result) }
                is GameInput.ExecuteDiscard -> { executeDiscard(input.playerId, input.tile) }
                is GameInput.StartDiscardAnimation -> {
                    // OPTIMIZATION 2: Avoid O(N*M) linear hand scans with ID set lookup
                    val selectedSet = _uiState.value.selectedTileIds
                    val playerHand = stateMachine.currentState.players[input.playerIndex]?.hand ?: emptyList()
                    val selectedTiles = if (selectedSet.isEmpty()) {
                        listOf(input.tile)
                    } else {
                        playerHand.filter { it.id in selectedSet }
                    }

                    val validationResult = gameValidator.validateDiscard(
                        gameState = stateMachine.currentState,
                        playerIndex = input.playerIndex,
                        discardedTile = input.tile,
                        selectedTiles = selectedTiles
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "Discard Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    // Direct suspend invocation: eliminates redundant nested coroutine launch overhead
                    awaitDiscardAnimation(input.tile, input.playerIndex, input.seat)
                }
                is GameInput.CompleteDiscardAnimation -> {
                    completeDiscardAnimation(input.playerId, input.tile)
                }
                is GameInput.DrawRegularTile -> {
                    val validationResult = gameValidator.validateDraw(
                        gameState = stateMachine.currentState,
                        playerIndex = input.playerIndex
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "Draw Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    drawRegularTile(input.playerIndex)
                }
                is GameInput.DrawReplacementTile -> {
                    drawReplacementTile(input.playerIndex)
                }
                is GameInput.StartDrawAnimation -> {
                    awaitDrawAnimation(
                        input.tile,
                        input.playerIndex,
                        input.seat,
                        input.startX,
                        input.startY,
                        input.targetX,
                        input.targetY
                    )
                }
                is GameInput.CompleteDrawAnimation -> { completeDrawAnimation(input.playerId, input.tile) }
                is GameInput.PerformBonusMeld -> {
                    val validationResult = gameValidator.validateBonusMeld(
                        gameState = stateMachine.currentState,
                        playerIndex = input.playerIndex,
                        tile = input.tile
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "Bonus Meld Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    awaitBonusAnimation(input.tile, input.playerIndex, input.seat)
                    delay(500.milliseconds)
                    drawReplacementTile(input.playerIndex)
                    if (input.playerIndex == 0) {
                        val remainingBonus = stateMachine.currentState.players[0]?.hand?.any { it.isBonus } == true
                        if (!remainingBonus) {
                            humanBonusDeferred?.complete(Unit)
                        }
                    }
                }
                is GameInput.CompleteBonusAnimation -> { completeBonusAnimation(input.playerId, input.tile) }
                is GameInput.CompleteMeldAnimation -> { completeMeldAnimation(input.playerId, input.tile) }
                is GameInput.PerformInTurnKong -> {
                    val validationResult = gameValidator.validateInTurnKong(
                        gameState = stateMachine.currentState,
                        playerIndex = input.playerIndex
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "In-Turn Kong Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    performInTurnKong(input.playerIndex)
                }
                is GameInput.ExecuteBonusExchange -> { stateMachine.handleInput(input) }
                is GameInput.ExecuteBonusExchangeComplete -> { stateMachine.handleInput(input) }
                is GameInput.PauseGame -> {}
                is GameInput.StartNewRound -> {}
                is GameInput.SubmitClaim -> {
                    val validationResult = gameValidator.validateClaim(
                        gameState = stateMachine.currentState,
                        claimIntent = input.claimIntent
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "Claim Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    if (input.claimIntent.playerIndex == 0) {
                        _uiState.update {
                            it.copy(
                                availableClaims = emptySet(),
                                legalClaimIntents = emptyList(),
                                selectedTileIds = emptySet()
                            )
                        }
                    }
                    stateMachine.handleInput(input)
                }
                is GameInput.ClaimResolved -> { stateMachine.handleInput(input) }
                is GameInput.ExecuteClaimInterception -> { stateMachine.handleInput(input) }
                is GameInput.ToggleTileSelection -> { toggleTileSelection(input.tileId) }
                is GameInput.ReorderHand -> { reorderHand(input.playerId, input.newHand) }
                is GameInput.StartNextRound,
                is GameInput.RestartMatch -> {
                    _uiState.update {
                        UIState()
                    }
                    stateMachine.handleInput(input)
                }
                is GameInput.DeclareSelfDrawWin -> {
                    val validationResult = gameValidator.validateSelfDrawWin(
                        gameState = stateMachine.currentState,
                        playerIndex = input.playerIndex,
                        winningTile = input.winningTile
                    )
                    if (validationResult.isError()) {
                        val errorMsg = (validationResult as ValidationResult.Error).message
                        Logger.w(tag, "Self-Draw Win Validation Error: $errorMsg")
                        stateMachine.emitUiEffect(UiEffect.ShowError(errorMsg))
                        return@launch
                    }

                    stateMachine.handleInput(input)
                }
            }
        }
    }

    private fun toggleTileSelection(tileId: Int) {
        _uiState.update { currentState ->
            val currentSelected = currentState.selectedTileIds
            val newSelected = if (currentSelected.contains(tileId)) {
                currentSelected - tileId
            } else if (currentSelected.size < 4) {
                currentSelected + tileId
            } else {
                currentSelected
            }
            currentState.copy(selectedTileIds = newSelected)
        }
    }

    private fun reorderHand(playerId: Int, newHand: List<MahjongTile>) {
        stateMachine.updateState { state ->
            val playerState = state.players[playerId]
            if (playerState == null) {
                state
            } else {
                val updatedPlayerState = playerState.copy(hand = newHand)
                state.copy(players = state.players + (playerId to updatedPlayerState))
            }
        }
        _uiState.update { currentState ->
            val playerState = currentState.playerStates[playerId]
            if (playerState == null) {
                currentState
            } else {
                val updatedPlayerState = playerState.copy(hand = newHand)
                currentState.copy(playerStates = currentState.playerStates + (playerId to updatedPlayerState))
            }
        }
    }

    private fun rollForDealer(providedResult: DiceResult? = null) {
        val result = providedResult ?: DiceRoller.rollTwoDice()
        val currentState = stateMachine.currentState

        val electedDealer = (currentState.dealerIndex + result.sum - 1) % 4
        Logger.d(tag, "Roll for Dealer (Round 1): Sum = ${result.sum}. Elected Dealer is Player $electedDealer.")

        stateMachine.updateState { state ->
            state.copy(
                firstRollResult = result,
                dealerIndex = electedDealer,
                targetWallPlayer = electedDealer,
                eastWindPlayerIndex = electedDealer
            )
        }

        val mahjongSeat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = electedDealer)
        _uiState.update {
            it.copy(
                eastWindPlayerIndex = electedDealer,
                eastWindBadgePosition = mahjongSeat
            )
        }
        stateMachine.emitUiEffect(UiEffect.ShowEastWindBadge(mahjongSeat))
    }

    private fun rollForTargetWall(providedResult: DiceResult? = null) {
        val result = providedResult ?: DiceRoller.rollTwoDice()
        val currentState = stateMachine.currentState

        val currentDealer = currentState.dealerIndex
        val targetWall = (currentDealer + result.sum - 1) % 4
        Logger.d(tag, "Roll for Target Wall (Round ${currentState.roundNumber}): Sum = ${result.sum}. Dealer is Player $currentDealer, Target Wall is Player $targetWall.")

        stateMachine.updateState { state ->
            state.copy(
                firstRollResult = result,
                targetWallPlayer = targetWall
            )
        }

        val mahjongSeat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = currentDealer)
        _uiState.update {
            it.copy(
                eastWindPlayerIndex = currentDealer,
                eastWindBadgePosition = mahjongSeat
            )
        }
        stateMachine.emitUiEffect(UiEffect.ShowEastWindBadge(mahjongSeat))
    }

    private suspend fun rollForWallBreak(providedResult: DiceResult? = null) {
        val result = providedResult ?: DiceRoller.rollTwoDice()
        val currentState = gameState.value
        val firstSum = currentState.firstRollResult?.sum ?: 5
        val breakIndex = result.sum

        Logger.d(tag, "Roll 2 for Wall Break: Second Roll Sum = ${result.sum}. Break Index = $breakIndex")

        stateMachine.updateState { state ->
            val targetWall = state.playerWalls[state.targetWallPlayer] ?: return@updateState state
            val finalBreakIndex = breakIndex.coerceIn(0, targetWall.stacks.size - 1)
            val updatedWall = targetWall.copy(currentStackPointer = finalBreakIndex, isAscending = true)
            val initialDeadWallPlayer = if (finalBreakIndex > 0) state.targetWallPlayer else (state.targetWallPlayer + 1) % 4
            val initialDeadWallPointer = if (finalBreakIndex > 0) finalBreakIndex - 1 else 17
            state.copy(
                playerWalls = state.playerWalls + (state.targetWallPlayer to updatedWall),
                secondRollResult = result,
                diceResult = DiceResult(firstSum, result.sum, finalBreakIndex),
                deadWallPlayer = initialDeadWallPlayer,
                deadWallStackPointer = initialDeadWallPointer
            )
        }
        // Delegate and notify the state machine to handle the next input Distribution
        // Provide a pause so the user can clearly observe the resting second dice roll outcome
        delay(1200.milliseconds)
        stateMachine.handleInput(GameInput.GoDistribute)
    }

    private val distributionCoordinator by lazy {
        com.nowlsantos.mahjongai.presentation.TileDistributionCoordinator(
            stateMachine = stateMachine,
            uiStateFlow = _uiState,
            tileSortFunction = ::suitSort
        )
    }

    suspend fun executeDistribution(distributor: TileDistributor) {
        distributionCoordinator.execute(distributor, gameState.value)
    }

    /**
     * OPTIMIZATION 4: Memory Leak Prevention in Deferred Animations.
     * Cancels any previously pending deferred before replacing to avoid hanging coroutine leaks.
     */
    suspend fun awaitDrawAnimation(tile: MahjongTile, playerIndex: Int, seat: MahjongSeat, startX: Float, startY: Float, targetX: Float, targetY: Float) {
        val deferred = CompletableDeferred<Unit>()
        animationMutex.withLock {
            drawAnimationDeferred?.cancel()
            drawAnimationDeferred = deferred
        }
        stateMachine.emitUiEffect(UiEffect.StartDrawTileAnimation(tile, playerIndex, seat, startX, startY, targetX, targetY))
        try {
            deferred.await()
        } catch (_: Exception) {}
    }

    suspend fun awaitDiscardAnimation(tile: MahjongTile, playerIndex: Int, seat: MahjongSeat) {
        val deferred = CompletableDeferred<Unit>()
        animationMutex.withLock {
            discardAnimationDeferred?.cancel()
            discardAnimationDeferred = deferred
        }
        stateMachine.emitUiEffect(UiEffect.StartDiscardTileAnimation(tile, playerIndex, seat))
        try {
            deferred.await()
        } catch (_: Exception) {}
    }

    suspend fun awaitBonusAnimation(tile: MahjongTile, playerIndex: Int, seat: MahjongSeat) {
        val deferred = CompletableDeferred<Unit>()
        animationMutex.withLock {
            bonusAnimationDeferred?.cancel()
            bonusAnimationDeferred = deferred
        }
        stateMachine.emitUiEffect(UiEffect.StartBonusTileAnimation(tile, playerIndex, seat))
        try {
            deferred.await()
        } catch (_: Exception) {}
    }

    suspend fun awaitMeldAnimation(
        tile: MahjongTile,
        playerIndex: Int,
        seat: MahjongSeat,
        animationType: AnimationType = AnimationType.PUNG_TILES,
        isFromDiscard: Boolean = true,
        isConcealed: Boolean = false
    ) {
        val deferred = CompletableDeferred<Unit>()
        animationMutex.withLock {
            meldAnimationDeferred?.cancel()
            meldAnimationDeferred = deferred
        }
        stateMachine.emitUiEffect(
            UiEffect.StartMeldTileAnimation(tile, playerIndex, seat, animationType, isFromDiscard, isConcealed)
        )
        try {
            deferred.await()
        } catch (_: Exception) {}
    }

    suspend fun performInTurnKong(playerIndex: Int, action: TurnAction? = null): MahjongTile? {
        val playerState = stateMachine.currentState.players[playerIndex] ?: return null
        val kongAction = action ?: run {
            val seatWind = stateMachine.currentState.getPlayerSeatWind(playerIndex)
            val legalActions = MoveGenerator.getLegalTurnActions(
                playerHand = playerState.hand,
                revealedMelds = playerState.revealedMelds,
                seatWind = seatWind,
                prevalentWind = stateMachine.currentState.prevalentWind
            )
            legalActions.filterIsInstance<TurnAction.ConcealedKong>().firstOrNull()
                ?: legalActions.filterIsInstance<TurnAction.AddedKong>().firstOrNull()
        } ?: return null

        val seat = PlayerState.getMahjongSeat(0, playerIndex)

        val paymentDeltas = when (kongAction) {
            is TurnAction.ConcealedKong -> {
                com.nowlsantos.mahjongai.engine.scoring.KongPaymentRules.calculateKongPayment(
                    declarerIndex = playerIndex,
                    isConcealed = true,
                    isPromoted = false
                )
            }
            is TurnAction.AddedKong -> {
                com.nowlsantos.mahjongai.engine.scoring.KongPaymentRules.calculateKongPayment(
                    declarerIndex = playerIndex,
                    isConcealed = false,
                    isPromoted = true
                )
            }
            else -> emptyMap()
        }

        when (kongAction) {
            is TurnAction.ConcealedKong -> {
                val tiles = kongAction.tiles
                awaitMeldAnimation(tiles.first(), playerIndex, seat, AnimationType.KONG_TILES, isFromDiscard = false, isConcealed = true)

                val newMeld = Meld(MeldType.CONCEALED_KONG, tiles)
                val updatedHand = playerState.hand - tiles.toSet()
                val updatedMelds = playerState.revealedMelds + newMeld
                val updatedPlayer = playerState.copy(hand = updatedHand, revealedMelds = updatedMelds)

                // OPTIMIZATION 5: Compute updated players map once instead of twice
                val updatedPlayers = stateMachine.currentState.players.mapValues { (pid, p) ->
                    val delta = paymentDeltas[pid] ?: 0
                    val basePlayer = if (pid == playerIndex) updatedPlayer else p
                    basePlayer.copy(score = (basePlayer.score + delta).coerceAtLeast(0))
                }

                stateMachine.updateState { state ->
                    state.copy(players = updatedPlayers)
                }
                val eventId = if (paymentDeltas.isNotEmpty()) kongPaymentCounter.incrementAndGet() else 0L
                _uiState.update { ui ->
                    ui.copy(
                        playerStates = updatedPlayers,
                        selectedTileIds = emptySet(),
                        lastKongPaymentDeltas = if (paymentDeltas.isNotEmpty()) paymentDeltas else ui.lastKongPaymentDeltas,
                        kongPaymentEventId = if (paymentDeltas.isNotEmpty()) eventId else ui.kongPaymentEventId
                    )
                }
            }
            is TurnAction.AddedKong -> {
                val tile = kongAction.tile
                val targetPung = kongAction.targetPung
                awaitMeldAnimation(tile, playerIndex, seat, AnimationType.KONG_TILES, isFromDiscard = false)

                val updatedHand = playerState.hand - tile
                val upgradedMeld = Meld(MeldType.KONG, targetPung.tiles + tile)
                val updatedMelds = playerState.revealedMelds.map { if (it == targetPung) upgradedMeld else it }
                val updatedPlayer = playerState.copy(hand = updatedHand, revealedMelds = updatedMelds)

                val updatedPlayers = stateMachine.currentState.players.mapValues { (pid, p) ->
                    val delta = paymentDeltas[pid] ?: 0
                    val basePlayer = if (pid == playerIndex) updatedPlayer else p
                    basePlayer.copy(score = (basePlayer.score + delta).coerceAtLeast(0))
                }

                stateMachine.updateState { state ->
                    state.copy(players = updatedPlayers)
                }
                val eventId = if (paymentDeltas.isNotEmpty()) kongPaymentCounter.incrementAndGet() else 0L
                _uiState.update { ui ->
                    ui.copy(
                        playerStates = updatedPlayers,
                        selectedTileIds = emptySet(),
                        lastKongPaymentDeltas = if (paymentDeltas.isNotEmpty()) paymentDeltas else ui.lastKongPaymentDeltas,
                        kongPaymentEventId = if (paymentDeltas.isNotEmpty()) eventId else ui.kongPaymentEventId
                    )
                }
            }
            else -> return null
        }

        if (paymentDeltas.isNotEmpty()) {
            stateMachine.emitUiEffect(UiEffect.ShowKongPayment(paymentDeltas))
            delay(1200.milliseconds) // Pause so kong payment animation manifests clearly before replacement draw
        }

        val replacement = drawReplacementTile(playerIndex)
        stateMachine.emitUiEffect(UiEffect.RefreshUI)
        return replacement
    }

    suspend fun drawRegularTile(playerIndex: Int): MahjongTile? {
        var drawnTile: MahjongTile? = null
        stateMachine.updateState { currentState ->
            val wallManager = PreciseWallManager(currentState.playerWalls).apply {
                setStartingPosition(currentState.activeLiveWallIndex)
            }
            val (tiles, _) = wallManager.drawSingleTiles(1)
            val updatedWalls = wallManager.getUpdatedWalls()
            val nextWallIndex = wallManager.currentActiveWallIndex
            drawnTile = tiles.firstOrNull()
            if (drawnTile != null) {
                Logger.d(tag, "Drawn Regular Tile (Live Wall) for Player $playerIndex from Wall $nextWallIndex: $drawnTile")
            }
            currentState.copy(
                playerWalls = updatedWalls,
                activeLiveWallIndex = nextWallIndex,
                isLastDrawReplacement = false
            )
        }
        drawnTile?.let { tile ->
            val seat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = playerIndex)
            awaitDrawAnimation(tile, playerIndex, seat, 540f, 150f, 540f, 1600f)
        } ?: run {
            Logger.w(tag, "Live wall exhausted while Player $playerIndex attempted to draw. Round ends in Draw (Lau Kuk).")
            stateMachine.updateState { state ->
                state.copy(
                    roundEndData = RoundEndData(
                        winnerIndex = null,
                        discarderIndex = null,
                        winningTile = null,
                        isSelfDraw = false,
                        isDraw = true,
                        scoringResult = null
                    )
                )
            }
            stateMachine.emitUiEffect(UiEffect.RefreshUI)
            stateMachine.transitionTo(GamePhase.ROUND_END)
        }
        return drawnTile
    }

    suspend fun drawReplacementTile(playerIndex: Int): MahjongTile? {
        var drawnTile: MahjongTile? = null
        stateMachine.updateState { currentState ->
            var currentDeadWallPlayer = currentState.deadWallPlayer
            var currentDeadWallPointer = currentState.deadWallStackPointer
            var updatedWalls = currentState.playerWalls

            // Traverse dead wall backwards until we find an undrawn tile
            repeat(72) {
                val wall = updatedWalls[currentDeadWallPlayer]
                if (wall != null) {
                    val stack = wall.stacks.getOrNull(currentDeadWallPointer)
                    if (stack != null && !stack.isFullyDepleted) {
                        val result = stack.drawNextTile()
                        if (result != null) {
                            val (tile, updatedStack) = result
                            val newStacks = wall.stacks.toMutableList()
                            newStacks[currentDeadWallPointer] = updatedStack
                            val newWall = wall.copy(stacks = newStacks)
                            updatedWalls = updatedWalls + (currentDeadWallPlayer to newWall)
                            drawnTile = tile

                            // If this stack is now fully depleted, advance dead wall pointer backwards
                            val nextDeadWallPointer: Int
                            val nextDeadWallPlayer: Int
                            if (updatedStack.isFullyDepleted) {
                                if (currentDeadWallPointer - 1 < 0) {
                                    nextDeadWallPlayer = (currentDeadWallPlayer + 1) % 4
                                    nextDeadWallPointer = 17
                                } else {
                                    nextDeadWallPlayer = currentDeadWallPlayer
                                    nextDeadWallPointer = currentDeadWallPointer - 1
                                }
                            } else {
                                nextDeadWallPlayer = currentDeadWallPlayer
                                nextDeadWallPointer = currentDeadWallPointer
                            }

                            Logger.d(tag, "Drawn Replacement Tile from Dead Wall (Wall $currentDeadWallPlayer, Stack $currentDeadWallPointer) for Player $playerIndex: $tile")
                            return@updateState currentState.copy(
                                playerWalls = updatedWalls,
                                deadWallPlayer = nextDeadWallPlayer,
                                deadWallStackPointer = nextDeadWallPointer,
                                isLastDrawReplacement = true
                            )
                        }
                    }
                }

                // If current stack was empty, step backwards
                if (currentDeadWallPointer - 1 < 0) {
                    currentDeadWallPlayer = (currentDeadWallPlayer + 1) % 4
                    currentDeadWallPointer = 17
                } else {
                    currentDeadWallPointer--
                }
            }
            currentState
        }
        drawnTile?.let { tile ->
            val seat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = playerIndex)
            awaitDrawAnimation(tile, playerIndex, seat, 540f, 150f, 540f, 1600f)
        } ?: run {
            Logger.w(tag, "All dead wall tiles depleted while attempting to draw replacement tile for Player $playerIndex. Round ends in Draw (Lau Kuk).")
            if (playerIndex == 0) {
                humanBonusDeferred?.complete(Unit)
            }
            stateMachine.updateState { state ->
                state.copy(
                    roundEndData = RoundEndData(
                        winnerIndex = null,
                        discarderIndex = null,
                        winningTile = null,
                        isSelfDraw = false,
                        isDraw = true,
                        scoringResult = null
                    )
                )
            }
            stateMachine.emitUiEffect(UiEffect.RefreshUI)
            stateMachine.transitionTo(GamePhase.ROUND_END)
        }
        return drawnTile
    }

    fun sortBySuit() {
        val startTime = System.currentTimeMillis()
        stateMachine.updateState { state ->
            val playerState = state.players[0]
            if (playerState == null) {
                state
            } else {
                val hand = playerState.hand
                val sortedHand = if (hand.size % 3 == 2) {
                    suitSort(hand.dropLast(1)) + hand.last()
                } else {
                    suitSort(hand)
                }
                val updatedPlayerState = playerState.copy(hand = sortedHand)
                state.copy(players = state.players + (0 to updatedPlayerState))
            }
        }
        _uiState.update { currentUi ->
            val playerState = currentUi.playerStates[0]
            if (playerState == null) {
                currentUi
            } else {
                val hand = playerState.hand
                val sortedHand = if (hand.size % 3 == 2) {
                    suitSort(hand.dropLast(1)) + hand.last()
                } else {
                    suitSort(hand)
                }
                val updatedPlayerState = playerState.copy(hand = sortedHand)
                currentUi.copy(playerStates = currentUi.playerStates + (0 to updatedPlayerState))
            }
        }
        val duration = System.currentTimeMillis() - startTime
        Logger.d(tag, "sortBySuit completed in ${duration}ms")
    }

    /**
     * OPTIMIZATION 1: Uses pre-allocated static TILE_COMPARATOR to avoid repeated comparator allocations.
     */
    private fun suitSort(tiles: List<MahjongTile>): List<MahjongTile> {
        return tiles.sortedWith(TILE_COMPARATOR)
    }

    private fun completeDrawAnimation(playerIndex: Int, tile: MahjongTile) {
        distributionCoordinator.updateUiAfterDeal(playerIndex, listOf(tile))
        drawAnimationDeferred?.complete(Unit)
        drawAnimationDeferred = null
    }

    private fun completeBonusAnimation(playerIndex: Int, tile: MahjongTile) {
        stateMachine.updateState { state ->
            val playerState = state.players[playerIndex]
            if (playerState == null) {
                state
            } else {
                val updatedHand = playerState.hand - tile
                val updatedBonus = playerState.bonusTiles + tile
                val updatedPlayerState = playerState.copy(hand = updatedHand, bonusTiles = updatedBonus)
                state.copy(players = state.players + (playerIndex to updatedPlayerState))
            }
        }
        _uiState.update { currentUi ->
            val playerState = currentUi.playerStates[playerIndex]
            if (playerState == null) {
                currentUi
            } else {
                val updatedHand = playerState.hand - tile
                val updatedBonus = playerState.bonusTiles + tile
                currentUi.copy(
                    playerStates = currentUi.playerStates + (playerIndex to playerState.copy(hand = updatedHand, bonusTiles = updatedBonus)),
                    selectedTileIds = emptySet()
                )
            }
        }
        bonusAnimationDeferred?.complete(Unit)
        bonusAnimationDeferred = null
    }

    private fun completeMeldAnimation(playerIndex: Int, tile: MahjongTile) {
        meldAnimationDeferred?.complete(Unit)
        meldAnimationDeferred = null
    }

    private fun completeDiscardAnimation(playerIndex: Int, tile: MahjongTile) {
        executeDiscard(playerIndex, tile)
        discardAnimationDeferred?.complete(Unit)
        discardAnimationDeferred = null
    }

    fun executeDiscard(playerIndex: Int, tile: MahjongTile) {
        val playerState = stateMachine.currentState.players[playerIndex]
        val updatedPlayerState = playerState?.copy(
            hand = playerState.hand - tile,
            discards = playerState.discards + tile
        )

        stateMachine.updateState { currentState ->
            val updatedPublicDiscards = currentState.publicDiscards + tile
            val updatedPlayers = if (updatedPlayerState != null) {
                currentState.players + (playerIndex to updatedPlayerState)
            } else {
                currentState.players
            }

            currentState.copy(
                players = updatedPlayers,
                publicDiscards = updatedPublicDiscards,
                lastDiscardedTile = tile,
                lastDiscarderIndex = playerIndex
            )
        }

        _uiState.update { currentUi ->
            val updatedPlayerStates = if (updatedPlayerState != null) {
                currentUi.playerStates + (playerIndex to updatedPlayerState)
            } else {
                currentUi.playerStates
            }

            currentUi.copy(
                playerStates = updatedPlayerStates,
                selectedTileIds = if (playerIndex == 0) emptySet() else currentUi.selectedTileIds
            )
        }
        Logger.d(tag, "Player $playerIndex successfully committed discard to board: $tile")

        viewModelScope.launch {
            stateMachine.handleInput(GameInput.ExecuteClaimInterception)
        }
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        drawAnimationDeferred?.cancel()
        drawAnimationDeferred = null
        discardAnimationDeferred?.cancel()
        discardAnimationDeferred = null
        bonusAnimationDeferred?.cancel()
        bonusAnimationDeferred = null
        meldAnimationDeferred?.cancel()
        meldAnimationDeferred = null
        humanBonusDeferred?.cancel()
        humanBonusDeferred = null
        stateMachine.waitForHumanFlower = {}
        stateMachine.waitForAnimation = {}
    }
}
