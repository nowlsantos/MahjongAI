package com.nowlsantos.mahjongai.ui.animations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameViewModel
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.UIState
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.GameSoundManager
import kotlin.math.roundToInt

@Composable
fun GameAnimator(
    uiState: UIState,
    viewModel: GameViewModel,
    tilePositions: Map<Int, IntOffset>,
    meldPositions: Map<MahjongSeat, IntOffset>,
    meldSizes: Map<MahjongSeat, androidx.compose.ui.unit.IntSize> = emptyMap(),
    centralPoolOffset: IntOffset,
    density: Density
) {
    val context = LocalContext.current
    val soundManager = remember { GameSoundManager(context) }

    DisposableEffect(soundManager) {
        onDispose { soundManager.release() }
    }

    val effectQueue = remember { androidx.compose.runtime.mutableStateListOf<UiEffect>() }
    
    val currentEffect = effectQueue.firstOrNull()

    var drawStartX by remember { mutableFloatStateOf(0f) }
    var drawStartY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            // Enqueue only animation effects
            if (effect is UiEffect.StartDiscardTileAnimation ||
                effect is UiEffect.StartDrawTileAnimation ||
                effect is UiEffect.StartBonusTileAnimation ||
                effect is UiEffect.StartMeldTileAnimation) {
                effectQueue.add(effect)
            }
        }
    }

    val currentTile = when (currentEffect) {
        is UiEffect.StartDiscardTileAnimation -> currentEffect.tile
        is UiEffect.StartDrawTileAnimation -> currentEffect.tile
        is UiEffect.StartBonusTileAnimation -> currentEffect.tile
        is UiEffect.StartMeldTileAnimation -> currentEffect.tile
        else -> null
    }

    val currentSeat = when (currentEffect) {
        is UiEffect.StartDiscardTileAnimation -> currentEffect.seat
        is UiEffect.StartDrawTileAnimation -> currentEffect.seat
        is UiEffect.StartBonusTileAnimation -> currentEffect.seat
        is UiEffect.StartMeldTileAnimation -> currentEffect.seat
        else -> MahjongSeat.BOTTOM
    }

    val currentPlayerIndex = when (currentEffect) {
        is UiEffect.StartDiscardTileAnimation -> currentEffect.playerIndex
        is UiEffect.StartDrawTileAnimation -> currentEffect.playerIndex
        is UiEffect.StartBonusTileAnimation -> currentEffect.playerIndex
        is UiEffect.StartMeldTileAnimation -> currentEffect.playerIndex
        else -> 0
    }

    val animationType = when (currentEffect) {
        is UiEffect.StartDiscardTileAnimation -> AnimationType.DISCARD
        is UiEffect.StartDrawTileAnimation -> {
            drawStartX = currentEffect.startX
            drawStartY = currentEffect.startY
            AnimationType.DRAW_TILE
        }
        is UiEffect.StartBonusTileAnimation -> AnimationType.BONUS_TILE
        is UiEffect.StartMeldTileAnimation -> currentEffect.animationType
        else -> null
    }

    val popAnimAndComplete: (GameInput) -> Unit = { input ->
        viewModel.onEvent(input)
        if (effectQueue.isNotEmpty()) {
            effectQueue.removeAt(0)
        }
    }

    currentTile?.let { tile ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
        ) {
            when (animationType) {
                AnimationType.DISCARD -> {
                    val startOffset = tilePositions[tile.id] ?: when (currentSeat) {
                        MahjongSeat.BOTTOM -> meldPositions[MahjongSeat.BOTTOM] ?: IntOffset(540, 1600)
                        MahjongSeat.TOP -> meldPositions[MahjongSeat.TOP] ?: IntOffset(540, 200)
                        MahjongSeat.LEFT -> meldPositions[MahjongSeat.LEFT] ?: IntOffset(100, 960)
                        MahjongSeat.RIGHT -> meldPositions[MahjongSeat.RIGHT] ?: IntOffset(1800, 960)
                    }
                    DiscardTileAnimation(
                        startOffset = startOffset,
                        endOffset = centralPoolOffset,
                        tile = tile,
                        soundManager = soundManager,
                        onAnimationFinished = {
                            popAnimAndComplete(GameInput.CompleteDiscardAnimation(currentPlayerIndex, tile))
                        }
                    )
                }
                AnimationType.DRAW_TILE -> {
                    val startOffset = tilePositions[tile.id] ?: IntOffset(drawStartX.roundToInt(), drawStartY.roundToInt())
                    val playerState = uiState.playerStates[currentPlayerIndex]
                    val lastExistingTile = playerState?.hand?.lastOrNull { it.id != tile.id }
                    val lastTilePos = lastExistingTile?.let { tilePositions[it.id] }

                    val endOffset = when (currentSeat) {
                        MahjongSeat.BOTTOM, MahjongSeat.TOP -> {
                            val tileWidthPx = with(density) { GameConstants.HUMAN_TILE_WIDTH_UP.toPx() }
                            lastTilePos?.let { pos -> IntOffset((pos.x + tileWidthPx).roundToInt(), pos.y) }
                                ?: meldPositions[currentSeat]
                                ?: IntOffset(540, if (currentSeat == MahjongSeat.BOTTOM) 1600 else 200)
                        }
                        MahjongSeat.LEFT, MahjongSeat.RIGHT -> {
                            val verticalOffsetPx = with(density) { GameConstants.VERTICAL_OFFSET_UP.toPx() }
                            lastTilePos?.let { pos -> IntOffset(pos.x, (pos.y + verticalOffsetPx).roundToInt()) }
                                ?: meldPositions[currentSeat]
                                ?: IntOffset(if (currentSeat == MahjongSeat.LEFT) 100 else 1800, 960)
                        }
                    }

                    DrawTileAnimation(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        tile = tile,
                        seat = currentSeat,
                        soundManager = soundManager,
                        onAnimationFinished = {
                            popAnimAndComplete(GameInput.CompleteDrawAnimation(currentPlayerIndex, tile))
                        }
                    )
                }
                AnimationType.BONUS_TILE -> {
                    val startOffset = tilePositions[tile.id] ?: IntOffset.Zero
                    val meldRoot = meldPositions[currentSeat] ?: startOffset
                    val meldContainerSize = meldSizes[currentSeat] ?: androidx.compose.ui.unit.IntSize.Zero
                    val playerState = uiState.playerStates[currentPlayerIndex]
                    val existingBonus = playerState?.bonusTiles ?: emptyList()
                    val lastBonusTile = existingBonus.lastOrNull()
                    val lastBonusPos = lastBonusTile?.let { tilePositions[it.id] }

                    val isVertical = currentSeat == MahjongSeat.LEFT || currentSeat == MahjongSeat.RIGHT
                    val endOffset = if (isVertical) {
                        val verticalOffsetPx = with(density) { GameConstants.VERTICAL_OFFSET_DOWN.toPx() }
                        val tileWidthPx = with(density) { GameConstants.AI_TILE_WIDTH_DOWN.toPx() }
                        val tileHeightPx = with(density) { GameConstants.AI_TILE_HEIGHT_DOWN.toPx() }

                        if (lastBonusPos != null) {
                            // Already has melded bonus tile(s): place directly below the last one
                            IntOffset(lastBonusPos.x, (lastBonusPos.y + verticalOffsetPx).roundToInt())
                        } else {
                            // First bonus tile: land at the center of the meld container
                            val centerX = if (meldContainerSize.width > 0) {
                                meldRoot.x + (meldContainerSize.width - tileWidthPx) / 2f
                            } else {
                                meldRoot.x.toFloat()
                            }
                            val centerY = if (meldContainerSize.height > 0) {
                                meldRoot.y + (meldContainerSize.height - tileHeightPx) / 2f
                            } else {
                                meldRoot.y.toFloat()
                            }
                            IntOffset(centerX.roundToInt(), centerY.roundToInt())
                        }
                    } else {
                        val tileWidthPx = with(density) { GameConstants.HUMAN_TILE_WIDTH_UP.toPx() }
                        val tileHeightPx = with(density) { GameConstants.HUMAN_TILE_HEIGHT_UP.toPx() }

                        if (lastBonusPos != null) {
                            // Already has melded bonus tile(s): place directly to the right of the last one
                            IntOffset((lastBonusPos.x + tileWidthPx).roundToInt(), lastBonusPos.y)
                        } else {
                            // First bonus tile: land at the center of the meld container
                            val centerX = if (meldContainerSize.width > 0) {
                                meldRoot.x + (meldContainerSize.width - tileWidthPx) / 2f
                            } else {
                                meldRoot.x.toFloat()
                            }
                            val centerY = if (meldContainerSize.height > 0) {
                                meldRoot.y + (meldContainerSize.height - tileHeightPx) / 2f
                            } else {
                                meldRoot.y.toFloat()
                            }
                            IntOffset(centerX.roundToInt(), centerY.roundToInt())
                        }
                    }

                    BonusTileAnimation(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        tile = tile,
                        seat = currentSeat,
                        soundManager = soundManager,
                        onAnimationFinished = {
                            popAnimAndComplete(GameInput.CompleteBonusAnimation(currentPlayerIndex, tile))
                        }
                    )
                }
                AnimationType.CHOW_TILES, AnimationType.PUNG_TILES, AnimationType.KONG_TILES -> {
                    val meldEffect = currentEffect as? UiEffect.StartMeldTileAnimation
                    val isFromDiscard = meldEffect?.isFromDiscard ?: true
                    val isConcealed = meldEffect?.isConcealed ?: false
                    val startOffset = if (isFromDiscard) {
                        tilePositions[tile.id] ?: centralPoolOffset
                    } else {
                        tilePositions[tile.id] ?: when (currentSeat) {
                            MahjongSeat.BOTTOM -> meldPositions[MahjongSeat.BOTTOM] ?: IntOffset(540, 1600)
                            MahjongSeat.TOP -> meldPositions[MahjongSeat.TOP] ?: IntOffset(540, 200)
                            MahjongSeat.LEFT -> meldPositions[MahjongSeat.LEFT] ?: IntOffset(100, 960)
                            MahjongSeat.RIGHT -> meldPositions[MahjongSeat.RIGHT] ?: IntOffset(1800, 960)
                        }
                    }

                    val meldRoot = meldPositions[currentSeat] ?: startOffset
                    val meldContainerSize = meldSizes[currentSeat] ?: androidx.compose.ui.unit.IntSize.Zero
                    val playerState = uiState.playerStates[currentPlayerIndex]
                    val existingMelds = playerState?.revealedMelds ?: emptyList()
                    val existingBonus = playerState?.bonusTiles ?: emptyList()
                    val lastMeldTile = existingMelds.lastOrNull()?.tiles?.lastOrNull()
                    val lastMeldPos = lastMeldTile?.let { tilePositions[it.id] }
                    val lastBonusTile = existingBonus.lastOrNull()
                    val lastBonusPos = lastBonusTile?.let { tilePositions[it.id] }
                    val isVertical = currentSeat == MahjongSeat.LEFT || currentSeat == MahjongSeat.RIGHT
                    val groupSpacingPx = with(density) { 8.dp.toPx() }

                    val endOffset = if (isVertical) {
                        val tileWidthPx = with(density) { GameConstants.AI_TILE_WIDTH_DOWN.toPx() }
                        val tileHeightPx = with(density) { GameConstants.AI_TILE_HEIGHT_DOWN.toPx() }

                        if (lastMeldPos != null) {
                            IntOffset(lastMeldPos.x, (lastMeldPos.y + tileHeightPx + groupSpacingPx).roundToInt())
                        } else if (lastBonusPos != null) {
                            IntOffset(lastBonusPos.x, (lastBonusPos.y + tileHeightPx + groupSpacingPx).roundToInt())
                        } else {
                            val centerX = if (meldContainerSize.width > 0) {
                                meldRoot.x + (meldContainerSize.width - tileWidthPx) / 2f
                            } else {
                                meldRoot.x.toFloat()
                            }
                            val centerY = if (meldContainerSize.height > 0) {
                                meldRoot.y + (meldContainerSize.height - tileHeightPx) / 2f
                            } else {
                                meldRoot.y.toFloat()
                            }
                            IntOffset(centerX.roundToInt(), centerY.roundToInt())
                        }
                    } else {
                        val tileWidthPx = with(density) { GameConstants.HUMAN_TILE_WIDTH_UP.toPx() }
                        val tileHeightPx = with(density) { GameConstants.HUMAN_TILE_HEIGHT_UP.toPx() }

                        if (lastMeldPos != null) {
                            IntOffset((lastMeldPos.x + tileWidthPx + groupSpacingPx).roundToInt(), lastMeldPos.y)
                        } else if (lastBonusPos != null) {
                            IntOffset((lastBonusPos.x + tileWidthPx + groupSpacingPx).roundToInt(), lastBonusPos.y)
                        } else {
                            val centerX = if (meldContainerSize.width > 0) {
                                meldRoot.x + (meldContainerSize.width - tileWidthPx) / 2f
                            } else {
                                meldRoot.x.toFloat()
                            }
                            val centerY = if (meldContainerSize.height > 0) {
                                meldRoot.y + (meldContainerSize.height - tileHeightPx) / 2f
                            } else {
                                meldRoot.y.toFloat()
                            }
                            IntOffset(centerX.roundToInt(), centerY.roundToInt())
                        }
                    }

                    MeldTileAnimation(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        tile = tile,
                        seat = currentSeat,
                        animationType = animationType,
                        isFacedown = isConcealed,
                        soundManager = soundManager,
                        onAnimationFinished = {
                            popAnimAndComplete(GameInput.CompleteMeldAnimation(currentPlayerIndex, tile))
                        }
                    )
                }
                else -> {}
            }
        }
    }
}
