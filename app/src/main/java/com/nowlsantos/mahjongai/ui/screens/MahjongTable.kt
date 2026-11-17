package com.nowlsantos.mahjongai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameViewModel
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.PlayerWall
import com.nowlsantos.mahjongai.ui.animations.GameAnimator
import com.nowlsantos.mahjongai.ui.components.CentralDiscardPool
import com.nowlsantos.mahjongai.ui.components.dice.DiceView
import com.nowlsantos.mahjongai.ui.components.EastWindBadge
import com.nowlsantos.mahjongai.ui.components.player.PlayerScoreBadge
import com.nowlsantos.mahjongai.ui.components.dialogs.DialogManager
import com.nowlsantos.mahjongai.ui.components.player.PlayerController
import com.nowlsantos.mahjongai.ui.components.player.PlayerHand
import com.nowlsantos.mahjongai.ui.components.player.PlayerMelds
import com.nowlsantos.mahjongai.ui.components.walls.HorizontalWallStack
import com.nowlsantos.mahjongai.ui.components.walls.VerticalWallStack
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.PaleYellow
import com.nowlsantos.mahjongai.ui.theme.PureWhite
import com.nowlsantos.mahjongai.ui.theme.TealDrop
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.GameSoundManager
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun MahjongTable(
    viewModel: GameViewModel = koinViewModel()
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mahjongSeat = uiState.eastWindBadgePosition
    val playerWalls: Map<Int, PlayerWall> = gameState.playerWalls
    val isSetting = gameState.gamePhase == GamePhase.SETUP
    val isDiceRolling = gameState.gamePhase == GamePhase.ROLLING_DICE
    val isDistributing = gameState.gamePhase == GamePhase.DISTRIBUTION
    val isRoundEnd = gameState.gamePhase == GamePhase.ROUND_END || gameState.gamePhase == GamePhase.GAME_OVER
    val isPlaying = gameState.gamePhase == GamePhase.PLAYING || gameState.gamePhase == GamePhase.CLAIM_INTERCEPTION || isRoundEnd
    val winnerIndex = gameState.roundEndData?.winnerIndex
    val winningTileId = gameState.roundEndData?.winningTile?.id
    val bottomWall = playerWalls[0]
    val rightWall = playerWalls[1]
    val topWall = playerWalls[2]
    val leftWall = playerWalls[3]

    var centralPoolOffset by remember { mutableStateOf(IntOffset.Zero) }
    var selectedTileOffset by remember { mutableStateOf(IntOffset.Zero) }

    // Use mutableStateMapOf to modify entries in-place without reallocating Map objects or triggering table-wide recomposition cascades
    val tilePositions = remember { mutableStateMapOf<Int, IntOffset>() }
    val meldPositions = remember { mutableStateMapOf<MahjongSeat, IntOffset>() }
    val meldSizes = remember { mutableStateMapOf<MahjongSeat, IntSize>() }
    val density = LocalDensity.current

    val context = LocalContext.current
    val soundManager = remember { GameSoundManager(context) }
    DisposableEffect(soundManager) {
        onDispose { soundManager.release() }
    }

    LaunchedEffect(uiState.kongPaymentEventId) {
        if (uiState.kongPaymentEventId > 0 && !uiState.lastKongPaymentDeltas.isNullOrEmpty()) {
            soundManager.playTileClaim()
        }
    }

    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.SETUP) {
            tilePositions.clear()
            meldPositions.clear()
            meldSizes.clear()
            centralPoolOffset = IntOffset.Zero
            selectedTileOffset = IntOffset.Zero
        }
    }

    val updateTilePosition = remember {
        { tileId: Int, offset: IntOffset ->
            if (tilePositions[tileId] != offset) {
                tilePositions[tileId] = offset
            }
        }
    }
    val updateMeldPosition = remember {
        { seat: MahjongSeat, offset: IntOffset, size: IntSize ->
            if (meldPositions[seat] != offset) {
                meldPositions[seat] = offset
            }
            if (meldSizes[seat] != size) {
                meldSizes[seat] = size
            }
        }
    }

    // Main box frame
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Player (NorthWind) Score Badge
        gameState.players[2]?.let { player ->
            PlayerScoreBadge(
                score = player.score,
                seat = MahjongSeat.TOP,
                chipDelta = uiState.lastKongPaymentDeltas?.get(2),
                deltaKey = uiState.kongPaymentEventId,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1100f)
                    .padding(start = 78.dp, top = 40.dp)
            )
        }

        // Left Player (Dot Fury) Score Badge
        gameState.players[3]?.let { player ->
            PlayerScoreBadge(
                score = player.score,
                seat = MahjongSeat.LEFT,
                chipDelta = uiState.lastKongPaymentDeltas?.get(3),
                deltaKey = uiState.kongPaymentEventId,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(1100f)
                    .padding(start = 45.dp, bottom = 12.dp)
            )
        }

        // Right Player (NineGates) Score Badge
        gameState.players[1]?.let { player ->
            PlayerScoreBadge(
                score = player.score,
                seat = MahjongSeat.RIGHT,
                chipDelta = uiState.lastKongPaymentDeltas?.get(1),
                deltaKey = uiState.kongPaymentEventId,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1100f)
                    .padding(end = 44.dp, top = 34.dp)
            )
        }

        // Bottom Player (RedDragon) Score Badge
        gameState.players[0]?.let { player ->
            PlayerScoreBadge(
                score = player.score,
                seat = MahjongSeat.BOTTOM,
                chipDelta = uiState.lastKongPaymentDeltas?.get(0),
                deltaKey = uiState.kongPaymentEventId,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(1100f)
                    .padding(end = 18.dp, bottom = 38.dp)
            )
        }

        // First Row (Left Player and Left melds)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Player (Player 3)
            BoxWithConstraints(modifier = Modifier
                .weight(0.75f)
                .fillMaxHeight(),
                //.background(DarkGray),
                contentAlignment = Alignment.TopCenter) {
                Row(modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth)) {
                    // Left Player Hand
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                        //.background(TealDrop),
                        contentAlignment = if (isDistributing) Alignment.TopCenter else Alignment.Center
                    ) {
                        if (isDistributing) {
                            val containerHeight = this@BoxWithConstraints.maxHeight
                            if (uiState.dealtBatchesLeft.isNotEmpty()) {
                                val verticalOffset = GameConstants.VERTICAL_OFFSET_RIGHT_LEFT
                                val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT
                                val totalHeight = verticalOffset * (uiState.dealtBatchesLeft.size - 1) + tileHeight
                                val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

                                uiState.dealtBatchesLeft.forEachIndexed { index, stack ->
                                    val tileModifier = Modifier
                                        .offset(y = startY + (verticalOffset * index))
                                        .zIndex(index.toFloat())
                                    VerticalWallStack(
                                        modifier = tileModifier,
                                        stack = stack,
                                        showDepleted = true
                                    )
                                }
                            }
                        }

                        if (isPlaying) {
                            uiState.playerStates[3]?.let { playerState ->
                                PlayerHand(
                                    playerState = playerState,
                                    seat = MahjongSeat.LEFT,
                                    isRevealed = isRoundEnd && winnerIndex == 3,
                                    winningTileId = if (winnerIndex == 3) winningTileId else null,
                                    onTilePositioned = updateTilePosition
                                )
                            }
                        }
                    }

                    // Left Player Melds & Flower/Bonus tiles
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        //.background(TealDrop.copy(alpha = 0.5f))
                        .onGloballyPositioned { coordinates ->
                            val pos = coordinates.positionInRoot()
                            updateMeldPosition(MahjongSeat.LEFT, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()), coordinates.size)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        uiState.playerStates[3]?.let { playerState ->
                            PlayerMelds(
                                seat = MahjongSeat.LEFT,
                                bonusTiles = playerState.bonusTiles,
                                revealedMelds = playerState.revealedMelds,
                                isRevealed = isRoundEnd,
                                onTilePositioned = updateTilePosition
                            )
                        }
                    }
                }
            }

            // Left Wall
            BoxWithConstraints(modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                //.background(TealDrop),
            ) {
                val containerHeight = maxHeight
                leftWall?.let { wall ->
                    val verticalOffset = GameConstants.VERTICAL_OFFSET_RIGHT_LEFT
                    val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT
                    val totalHeight = verticalOffset * (wall.stacks.size - 1) + tileHeight
                    val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

                    wall.stacks.forEachIndexed { index, stack ->
                        key(stack.stackIndex, stack.isTopDrawn, stack.isBottomDrawn) {
                            val visualIndex = wall.stacks.size - 1 - index
                            val breakShift = if (gameState.diceResult != null && gameState.targetWallPlayer == 3 && index >= gameState.diceResult!!.sum) 16.dp else 0.dp
                            val tileModifier = Modifier
                                .fillMaxWidth()
                                .offset(x = 5.dp)
                                .offset(y = startY + (verticalOffset * visualIndex) + breakShift)
                                .zIndex(visualIndex.toFloat())
                            VerticalWallStack(
                                modifier = tileModifier,
                                stack = stack,
                                onTilePositioned = updateTilePosition
                            )
                        }
                    }
                }
            }

            // Middle Panel
            Column(modifier = Modifier
                .weight(3.5f)
                .fillMaxHeight()
                //.background(DarkGray),
            ) {
                // Top Player (Player 2) and melds
                Box(
                    modifier = Modifier
                        .weight(1.75f)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Player hand
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.5f),
                            //.background(TealDrop),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (isDistributing) {
                                if (uiState.dealtBatchesTop.isNotEmpty()) {
                                    for (stack in uiState.dealtBatchesTop) {
                                        HorizontalWallStack(stack = stack, showDepleted = true)
                                    }
                                }
                            }

                            if (isPlaying) {
                                uiState.playerStates[2]?.let { playerState ->
                                    PlayerHand(
                                        playerState = playerState,
                                        seat = MahjongSeat.TOP,
                                        isRevealed = isRoundEnd && winnerIndex == 2,
                                        winningTileId = if (winnerIndex == 2) winningTileId else null,
                                        onTilePositioned = updateTilePosition
                                    )
                                }
                            }
                        }

                        // Top Player Melds
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.5f)
                            //.background(TealDrop)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                updateMeldPosition(MahjongSeat.TOP, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()), coordinates.size)
                            },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            uiState.playerStates[2]?.let { playerState ->
                                PlayerMelds(
                                    seat = MahjongSeat.TOP,
                                    bonusTiles = playerState.bonusTiles,
                                    revealedMelds = playerState.revealedMelds,
                                    isRevealed = isRoundEnd,
                                    onTilePositioned = updateTilePosition
                                )
                            }
                        }
                    }
                }

                // Top Wall (Player 2)
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                    //.background(TealDrop),
                    contentAlignment = Alignment.Center) {
                    topWall?.let { wall ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            wall.stacks.forEachIndexed { index, stack ->
                                key(stack.stackIndex, stack.isTopDrawn, stack.isBottomDrawn) {
                                    val isLastStack = index == wall.stacks.lastIndex
                                    HorizontalWallStack(
                                        stack = stack,
                                        hasShadow = isLastStack,
                                        onTilePositioned = updateTilePosition
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Panel
                Box(modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    if (isSetting || isDiceRolling) {
                        DiceView(
                            gameState = gameState,
                            uiState = uiState,
                            onRollDealerFinished = { result -> viewModel.onEvent(GameInput.RollForDealer(result)) },
                            onRollTargetWallFinished = { result -> viewModel.onEvent(GameInput.RollForTargetWall(result)) },
                            onRollBreakFinished = { result -> viewModel.onEvent(GameInput.RollForWallBreak(result)) }
                        )
                    } else {
                        if (isPlaying) {
                            Text(
                                text = "Round ${gameState.roundNumber}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                                color = BrightYellow.copy(alpha = 0.4f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        // The discarded central pool of tiles
                        CentralDiscardPool(modifier = Modifier
                            .align(Alignment.Center),
                            discards = gameState.publicDiscards,
                            onTargetCalculated = { lastTileOffset ->
                                centralPoolOffset = lastTileOffset
                            },
                            onTilePositioned = updateTilePosition
                        )
                    }
                }

                // Bottom Wall (Player 0)
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                    //.background(TealDrop),
                    contentAlignment = Alignment.Center) {
                    bottomWall?.let { wall ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val reversedStacks = wall.stacks.reversed()
                            reversedStacks.forEachIndexed { index, stack ->
                                key(stack.stackIndex, stack.isTopDrawn, stack.isBottomDrawn) {
                                    val isLastStack = index == reversedStacks.lastIndex
                                    HorizontalWallStack(
                                        stack = stack,
                                        hasShadow = isLastStack,
                                        onTilePositioned = updateTilePosition
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Player (Player 0) and his melds
                Box(modifier = Modifier
                    .weight(1.9f)
                    .fillMaxWidth(),
                    contentAlignment = Alignment.Center) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Bottom Player Melds & Flower/Bonus tiles
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.5f)
                            //.background(TealDrop)
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                updateMeldPosition(MahjongSeat.BOTTOM, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()), coordinates.size)
                            },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            uiState.playerStates[0]?.let { playerState ->
                                PlayerMelds(
                                    seat = MahjongSeat.BOTTOM,
                                    bonusTiles = playerState.bonusTiles,
                                    revealedMelds = playerState.revealedMelds,
                                    isRevealed = isRoundEnd,
                                    onTilePositioned = updateTilePosition
                                )
                            }
                        }

                        // Bottom Player hand
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.5f),
                            //.background(TealDrop),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {

                            if (isDistributing) {
                                if (uiState.dealtBatches.isNotEmpty()) {
                                    for (stack in uiState.dealtBatches) {
                                        HorizontalWallStack(
                                            stack = stack, showDepleted = true,
                                        )
                                    }
                                }
                            }

                            if (isPlaying) {
                                uiState.playerStates[0]?.let { playerState ->
                                    PlayerHand(
                                        playerState = playerState,
                                        seat = MahjongSeat.BOTTOM,
                                        selectedTileIds = uiState.selectedTileIds,
                                        isRevealed = isRoundEnd && winnerIndex == 0,
                                        winningTileId = if (winnerIndex == 0) winningTileId else null,
                                        onTileClick = { tile ->
                                            viewModel.onEvent(GameInput.ToggleTileSelection(tile.id))
                                        },
                                        onReorder = { newHand ->
                                            viewModel.onEvent(GameInput.ReorderHand(0, newHand))
                                        },
                                        onSelectedTileCoordinate = { offset ->
                                            selectedTileOffset = offset
                                        },
                                        onTilePositioned = updateTilePosition
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Wall (Player 1)
            BoxWithConstraints(modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                //.background(TealDrop)
            ) {
                val containerHeight = maxHeight
                rightWall?.let { wall ->
                    val verticalOffset = GameConstants.VERTICAL_OFFSET_RIGHT_LEFT
                    val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT
                    val totalHeight = verticalOffset * (wall.stacks.size - 1) + tileHeight
                    val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

                    wall.stacks.forEachIndexed { index, stack ->
                        key(stack.stackIndex, stack.isTopDrawn, stack.isBottomDrawn) {
                            val breakShift = if (gameState.diceResult != null && gameState.targetWallPlayer == 1 && index >= gameState.diceResult!!.sum) 16.dp else 0.dp
                            val tileModifier = Modifier
                                .offset(x = 5.dp)
                                .offset(y = startY + (verticalOffset * index) + breakShift)
                                .zIndex(index.toFloat())
                            VerticalWallStack(
                                modifier = tileModifier,
                                stack = stack,
                                onTilePositioned = updateTilePosition
                            )
                        }
                    }
                }
            }

            // Right Player (Player 1) and Melds
            BoxWithConstraints(modifier = Modifier
                .weight(0.75f)
                .fillMaxHeight()
                //.background(TealDrop)
            ) {
                Row(modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth)) {

                    // Right Player Melds & Flower/Bonus tiles
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        //.background(TealDrop)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            updateMeldPosition(MahjongSeat.RIGHT, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()), coords.size)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        uiState.playerStates[1]?.let { playerState ->
                            PlayerMelds(
                                seat = MahjongSeat.RIGHT,
                                bonusTiles = playerState.bonusTiles,
                                revealedMelds = playerState.revealedMelds,
                                isRevealed = isRoundEnd,
                                onTilePositioned = updateTilePosition
                            )
                        }
                    }

                    // Right Player Hand
                    Box(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                        //.background(TealDrop),
                        contentAlignment = if (isDistributing) Alignment.TopCenter else Alignment.Center) {

                        if (isDistributing) {
                            val containerHeight = this@BoxWithConstraints.maxHeight
                            if (uiState.dealtBatchesRight.isNotEmpty()) {
                                val verticalOffset = GameConstants.VERTICAL_OFFSET_RIGHT_LEFT
                                val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT
                                val totalHeight = verticalOffset * (uiState.dealtBatchesRight.size - 1) + tileHeight
                                val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

                                uiState.dealtBatchesRight.forEachIndexed { index, stack ->
                                    val tileModifier = Modifier
                                        .offset(y = startY + (verticalOffset * index))
                                        .zIndex(index.toFloat())
                                    VerticalWallStack(
                                        modifier = tileModifier,
                                        stack = stack,
                                        showDepleted = true
                                    )
                                }
                            }
                        }

                        if (isPlaying) {
                            uiState.playerStates[1]?.let { playerState ->
                                PlayerHand(
                                    playerState = playerState,
                                    seat = MahjongSeat.RIGHT,
                                    isRevealed = isRoundEnd && winnerIndex == 1,
                                    winningTileId = if (winnerIndex == 1) winningTileId else null,
                                    onTilePositioned = updateTilePosition
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isPlaying) {
            PlayerController(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-198).dp),
                gameState = gameState,
                uiState = uiState,
                onAction = { viewModel.onEvent(it) }
            )
        }

        // 6. GameAnimator Overlay (Handles all dynamic flying tiles)
        GameAnimator(
            uiState = uiState,
            viewModel = viewModel,
            tilePositions = tilePositions,
            meldPositions = meldPositions,
            meldSizes = meldSizes,
            centralPoolOffset = centralPoolOffset,
            density = density
        )

        if (mahjongSeat != null) {
            val badgePadding = when (mahjongSeat) {
                MahjongSeat.BOTTOM -> PaddingValues(end = 20.dp, bottom = 75.dp)
                MahjongSeat.RIGHT  -> PaddingValues(end = 46.dp, top = 70.dp)
                MahjongSeat.TOP    -> PaddingValues(start = 18.dp, top = 75.dp)
                MahjongSeat.LEFT   -> PaddingValues(start = 44.dp, bottom = 75.dp)
            }

            EastWindBadge(
                modifier = Modifier
                    .align(
                        when (mahjongSeat) {
                            MahjongSeat.BOTTOM -> Alignment.BottomEnd
                            MahjongSeat.RIGHT  -> Alignment.TopEnd
                            MahjongSeat.TOP    -> Alignment.TopStart
                            MahjongSeat.LEFT   -> Alignment.BottomStart
                        }
                    )
                    .padding(badgePadding)
            )
        }

        DialogManager(
            gameState = gameState,
            onStartNextRound = { viewModel.onEvent(GameInput.StartNextRound) },
            onRestartMatch = { viewModel.onEvent(GameInput.RestartMatch) }
        )
    }
}
