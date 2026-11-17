package com.nowlsantos.mahjongai.ui.components.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.ui.components.tileDropShadow
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.TrueBlue
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.Logger
import com.nowlsantos.mahjongai.util.TileImageMapper
import com.nowlsantos.mahjongai.util.TileOrientation
import kotlin.math.abs
import kotlin.math.roundToInt

// Reusable static shapes and colors to eliminate allocation churn in tile render loops
private val TileShape4 = RoundedCornerShape(4.dp)
private val TileShape3 = RoundedCornerShape(3.dp)
private val WinningBorderColor = BrightYellow
private val WinningBgColor = BrightYellow.copy(alpha = 0.25f)
private val DraggingOverlayBg = BrightYellow.copy(alpha = 0.35f)
private val HoverOverlayBg = TrueBlue.copy(alpha = 0.35f)

/**
 * Reusable composable blueprint (Seat Shell pattern) for rendering player hands
 * across all seat positions (BOTTOM, RIGHT, TOP, LEFT) and orientations.
 */
@Composable
fun PlayerHand(
    modifier: Modifier = Modifier,
    playerState: PlayerState,
    seat: MahjongSeat = playerState.seat,
    isVertical: Boolean = (seat == MahjongSeat.LEFT || seat == MahjongSeat.RIGHT),
    selectedTileIds: Set<Int> = emptySet(),
    isRevealed: Boolean = false,
    winningTileId: Int? = null,
    onTileClick: (MahjongTile) -> Unit = {},
    onReorder: (List<MahjongTile>) -> Unit = {},
    onSelectedTileCoordinate: (IntOffset) -> Unit = {},
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    Box(modifier = modifier
        .fillMaxHeight()
        .fillMaxWidth()) {
        if (isVertical) {
            // Vertical Layout for Left & Right AI Seats
            if (playerState.hand.isNotEmpty()) {
                AiVerticalHiddenHand(
                    seat = seat,
                    tiles = playerState.hand,
                    winningTileId = winningTileId,
                    isRevealed = isRevealed,
                    onTilePositioned = onTilePositioned
                )
            }
        } else {
            // Horizontal Layout for Bottom (Human) & Top (AI) Seats
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playerState.hand.isNotEmpty()) {
                    if (!playerState.isAi) {
                        HumanHand(
                            tiles = playerState.hand,
                            selectedTileIds = selectedTileIds,
                            winningTileId = winningTileId,
                            isRevealed = isRevealed,
                            onTileClick = onTileClick,
                            onReorder = onReorder,
                            onSelectedTileCoordinate = onSelectedTileCoordinate,
                            onTilePositioned = onTilePositioned
                        )
                    } else {
                        AiHorizontalHiddenHand(
                            seat = seat,
                            tiles = playerState.hand,
                            winningTileId = winningTileId,
                            isRevealed = isRevealed,
                            onTilePositioned = onTilePositioned
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-COMPONENTS & HELPERS
// ==========================================

@Composable 
private fun AiHorizontalHand(
    seat: MahjongSeat,
    tiles: List<MahjongTile>,
    winningTileId: Int? = null,
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    // Memoize regular and winning tiles to avoid repeated list allocation during recomposition
    val regularTiles = remember(tiles, winningTileId) {
        if (winningTileId != null) tiles.filter { it.id != winningTileId } else tiles
    }
    val winningTile = remember(tiles, winningTileId) {
        if (winningTileId != null) tiles.find { it.id == winningTileId } else null
    }
    // Local cache prevents redundant coordinate emissions on layout passes
    val positionCache = remember { mutableMapOf<Int, IntOffset>() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        regularTiles.forEachIndexed { index, tile ->
            val isLastInHand = (index == regularTiles.lastIndex && winningTile == null)
            TileView(
                tile = tile,
                orientation = TileOrientation.DOWN,
                hasShadow = isLastInHand,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        if (positionCache[tile.id] != offset) {
                            positionCache[tile.id] = offset
                            onTilePositioned(tile.id, offset)
                        }
                    }
            )
        }

        if (winningTile != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .border(2.dp, WinningBorderColor, TileShape4)
                    .background(WinningBgColor, TileShape3)
                    .padding(1.dp)
            ) {
                TileView(
                    tile = winningTile,
                    orientation = TileOrientation.DOWN,
                    hasShadow = true,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        if (positionCache[winningTile.id] != offset) {
                            positionCache[winningTile.id] = offset
                            onTilePositioned(winningTile.id, offset)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AiHorizontalHiddenHand(
    seat: MahjongSeat,
    tiles: List<MahjongTile>,
    winningTileId: Int? = null,
    isRevealed: Boolean = false,
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    if (isRevealed) {
        AiHorizontalHand(
            seat = seat,
            tiles = tiles,
            winningTileId = winningTileId,
            onTilePositioned = onTilePositioned
        )
        return
    }

    val drawableRes = TileImageMapper.getPlayerTileDrawable(seat)
    val tileWidth = GameConstants.AI_TILE_WIDTH_UP
    val tileHeight = GameConstants.AI_TILE_HEIGHT_UP

    // Memoize tile partitions to eliminate list filtering overhead
    val regularTiles = remember(tiles, winningTileId) {
        if (winningTileId != null) tiles.filter { it.id != winningTileId } else tiles
    }
    val winningTile = remember(tiles, winningTileId) {
        if (winningTileId != null) tiles.find { it.id == winningTileId } else null
    }
    // Local cache prevents redundant coordinate emissions on layout passes
    val positionCache = remember { mutableMapOf<Int, IntOffset>() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        regularTiles.forEachIndexed { index, tile ->
            val isLastInHand = (index == regularTiles.lastIndex && winningTile == null)
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "AI Tile Back",
                modifier = Modifier
                    .size(width = tileWidth, height = tileHeight)
                    .then(if (isLastInHand) Modifier.tileDropShadow() else Modifier)
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        if (positionCache[tile.id] != offset) {
                            positionCache[tile.id] = offset
                            onTilePositioned(tile.id, offset)
                        }
                    }
            )
        }

        if (winningTile != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .border(2.dp, WinningBorderColor, TileShape4)
                    .background(WinningBgColor, TileShape3)
                    .padding(1.dp)
            ) {
                TileView(
                    tile = winningTile,
                    orientation = TileOrientation.DOWN,
                    hasShadow = true,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                        if (positionCache[winningTile.id] != offset) {
                            positionCache[winningTile.id] = offset
                            onTilePositioned(winningTile.id, offset)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AiVerticalHand(
    tiles: List<MahjongTile>,
    seat: MahjongSeat,
    winningTileId: Int? = null,
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    val orientation = when (seat) {
        MahjongSeat.LEFT -> TileOrientation.LEFT
        MahjongSeat.RIGHT -> TileOrientation.RIGHT
        else -> TileOrientation.DOWN
    }
    val verticalOffset = GameConstants.VERTICAL_OFFSET_DOWN
    val tileHeight = GameConstants.AI_TILE_HEIGHT_DOWN
    // Local cache prevents redundant coordinate emissions on layout passes
    val positionCache = remember { mutableMapOf<Int, IntOffset>() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val containerHeight = maxHeight
        val totalHeight = verticalOffset * (tiles.size - 1) + tileHeight
        val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

        tiles.forEachIndexed { index, tile ->
            val isWinning = tile.id == winningTileId
            val isDrawnTile = (index == tiles.lastIndex && tiles.size % 3 == 2 && winningTileId == null)
            val drawnOffset = 0.dp
            val tileModifier = Modifier
                .offset(y = startY + (verticalOffset * index) + (if (isWinning) 8.dp else 0.dp) + drawnOffset)
                .zIndex(if (isWinning || isDrawnTile) 100f else index.toFloat())
                .then(
                    if (isWinning) {
                        Modifier
                            .border(2.dp, WinningBorderColor, TileShape4)
                            .background(WinningBgColor, TileShape3)
                    } else {
                        Modifier
                    }
                )
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInRoot()
                    val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    if (positionCache[tile.id] != offset) {
                        positionCache[tile.id] = offset
                        onTilePositioned(tile.id, offset)
                    }
                }

            TileView(
                tile = tile,
                orientation = orientation,
                modifier = tileModifier
            )
        }
    }
}

@Composable
private fun AiVerticalHiddenHand(
    seat: MahjongSeat,
    tiles: List<MahjongTile>,
    winningTileId: Int? = null,
    isRevealed: Boolean = false,
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    if (isRevealed) {
        AiVerticalHand(
            tiles = tiles,
            seat = seat,
            winningTileId = winningTileId,
            onTilePositioned = onTilePositioned
        )
        return
    }

    val orientation = when (seat) {
        MahjongSeat.LEFT -> TileOrientation.LEFT
        MahjongSeat.RIGHT -> TileOrientation.RIGHT
        else -> TileOrientation.DOWN
    }
    val drawableRes = TileImageMapper.getPlayerTileDrawable(seat)
    val tileWidth = GameConstants.VERTICAL_TILE_WIDTH_UP
    val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT_UP
    val verticalOffset = GameConstants.VERTICAL_OFFSET_UP
    // Local cache prevents redundant coordinate emissions on layout passes
    val positionCache = remember { mutableMapOf<Int, IntOffset>() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val containerHeight = maxHeight
        val totalHeight = verticalOffset * (tiles.size - 1) + tileHeight
        val startY = ((containerHeight - totalHeight) / 2).coerceAtLeast(0.dp)

        tiles.forEachIndexed { index, tile ->
            val isWinning = tile.id == winningTileId
            val isDrawnTile = (index == tiles.lastIndex && tiles.size % 3 == 2 && winningTileId == null)
            val drawnOffset = 0.dp
            val tileModifier = Modifier
                .offset(y = startY + (verticalOffset * index) + (if (isWinning) 8.dp else 0.dp) + drawnOffset)
                .zIndex(if (isWinning || isDrawnTile) 100f else index.toFloat())
                .then(
                    if (isWinning) {
                        Modifier
                            .border(2.dp, WinningBorderColor, TileShape4)
                            .background(WinningBgColor, TileShape3)
                    } else {
                        Modifier
                    }
                )
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInRoot()
                    val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    if (positionCache[tile.id] != offset) {
                        positionCache[tile.id] = offset
                        onTilePositioned(tile.id, offset)
                    }
                }

            if (isWinning) {
                TileView(
                    tile = tile,
                    orientation = orientation,
                    modifier = tileModifier
                )
            } else {
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = "AI Vertical Tile Back",
                    modifier = tileModifier
                        .size(width = tileWidth, height = tileHeight)
                        .tileDropShadow(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun HumanHand(
    modifier: Modifier = Modifier,
    tiles: List<MahjongTile>,
    selectedTileIds: Set<Int> = emptySet(),
    winningTileId: Int? = null,
    isRevealed: Boolean = false,
    onTileClick: (MahjongTile) -> Unit,
    onReorder: (List<MahjongTile>) -> Unit,
    onSelectedTileCoordinate: (IntOffset) -> Unit = {},
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    val tag = "HumanHand"
    val tileSpacing: Dp = 2.dp
    val density = LocalDensity.current
    val spacingPx = with(density) { tileSpacing.toPx() }

    // Gestural state: dragged tile identifier and hover slot index
    var draggedTileId by remember { mutableStateOf<Int?>(null) }
    var hoveredTargetIndex by remember { mutableStateOf<Int?>(null) }
    // Drag offset is consumed exclusively in graphicsLayer lambda to prevent 60-120fps recomposition storm
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    // Plain mutable ref to store tile width without triggering layout recomposition on initial render
    val tileWidthRef = remember { floatArrayOf(0f) }

    // Non-reactive coordinate map: avoids Compose snapshot mutation during layout passes
    val tileCoordinatesMap = remember { mutableMapOf<Int, IntOffset>() }

    // Stable callback and state wrappers for pointer input coroutines
    val currentOnTileClick by rememberUpdatedState(onTileClick)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnSelectedTileCoordinate by rememberUpdatedState(onSelectedTileCoordinate)
    val currentOnTilePositioned by rememberUpdatedState(onTilePositioned)
    val currentTiles by rememberUpdatedState(tiles)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tiles.forEachIndexed { index, tile ->
            val isSelected = selectedTileIds.contains(tile.id)
            val currentIsSelected by rememberUpdatedState(isSelected)
            val isDragging = draggedTileId == tile.id
            val isWinning = tile.id == winningTileId
            val isHoveredTarget = !isDragging && draggedTileId != null && hoveredTargetIndex == index
            val orientation = if (isSelected || isWinning || isRevealed) TileOrientation.DOWN else TileOrientation.UP

            val tileModifier = Modifier
                .padding(end = 1.dp)
                .then(
                    if (isWinning) {
                        Modifier
                            .border(2.dp, WinningBorderColor, TileShape4)
                            .background(WinningBgColor, TileShape3)
                    } else {
                        Modifier
                    }
                )
                .onGloballyPositioned { coordinates ->
                    if (tileWidthRef[0] == 0f) {
                        tileWidthRef[0] = coordinates.size.width.toFloat()
                    }
                    val rootPos = coordinates.positionInRoot()
                    val posOffset = IntOffset(rootPos.x.roundToInt(), rootPos.y.roundToInt())
                    if (tileCoordinatesMap[tile.id] != posOffset) {
                        tileCoordinatesMap[tile.id] = posOffset
                        currentOnTilePositioned(tile.id, posOffset)
                    }
                }
                .zIndex(if (isDragging) 1000f else if (isWinning) 100f else index.toFloat())
                .graphicsLayer {
                    if (isDragging) {
                        translationX = dragOffsetX
                        scaleX = 1.05f
                        scaleY = 1.05f
                        shadowElevation = 6.dp.toPx()
                    }
                }
                .pointerInput(tile.id) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id

                            // Selected/Down Lockout: Tapping selected tile triggers clean deselect
                            if (currentIsSelected) {
                                var released = false
                                try {
                                    while (!released) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed) {
                                            released = true
                                        }
                                    }
                                } finally {
                                    val rootOffset = tileCoordinatesMap[tile.id] ?: IntOffset.Zero
                                    Logger.d(tag, "Tapped To Deselect:: ${tile.id} | Root Position -> X: ${rootOffset.x}, Y: ${rootOffset.y}")
                                    currentOnTileClick(tile)
                                }
                                continue // Keep loop alive for subsequent taps
                            }

                            var totalDrag = 0f
                            var isDraggingGesture = false

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                    if (change.pressed) {
                                        val dragAmount = change.positionChange().x
                                        totalDrag += dragAmount

                                        if (!isDraggingGesture && abs(totalDrag) > touchSlop) {
                                            isDraggingGesture = true
                                            draggedTileId = tile.id
                                        }

                                        if (isDraggingGesture) {
                                            change.consume()
                                            dragOffsetX = totalDrag

                                            // Only update hoveredTargetIndex when a slot boundary is crossed
                                            val slotWidthPx = tileWidthRef[0] + spacingPx
                                            val currentIdx = currentTiles.indexOfFirst { it.id == tile.id }
                                            if (currentIdx != -1 && slotWidthPx > 0f) {
                                                val steps = (totalDrag / slotWidthPx).roundToInt()
                                                val targetIdx = (currentIdx + steps).coerceIn(0, currentTiles.size - 1)
                                                if (hoveredTargetIndex != targetIdx) {
                                                    hoveredTargetIndex = targetIdx
                                                }
                                            }
                                        }
                                    } else {
                                        // Finger released
                                        if (isDraggingGesture) {
                                            change.consume()
                                            val currentIdx = currentTiles.indexOfFirst { it.id == tile.id }
                                            val slotWidthPx = tileWidthRef[0] + spacingPx
                                            val steps = if (slotWidthPx > 0f) (totalDrag / slotWidthPx).roundToInt() else 0

                                            if (currentIdx != -1 && steps != 0) {
                                                val newIndex = (currentIdx + steps).coerceIn(0, currentTiles.size - 1)
                                                if (currentIdx != newIndex) {
                                                    val reordered = currentTiles.toMutableList().apply {
                                                        val item = removeAt(currentIdx)
                                                        add(newIndex, item)
                                                    }
                                                    currentOnReorder(reordered)
                                                }
                                            }
                                        } else {
                                            val rootOffset = tileCoordinatesMap[tile.id] ?: IntOffset.Zero
                                            Logger.d(tag, "Tapped To Select:: ${tile.displayName} | Root Position -> X: ${rootOffset.x}, Y: ${rootOffset.y}")
                                            currentOnSelectedTileCoordinate(rootOffset)
                                            currentOnTileClick(tile)
                                        }
                                        break
                                    }
                                }
                            } finally {
                                draggedTileId = null
                                dragOffsetX = 0f
                                hoveredTargetIndex = null
                            }
                        }
                    }
                }

            Box(modifier = tileModifier) {
                val isLastTileInHand = (index == tiles.lastIndex)
                TileView(
                    tile = tile,
                    orientation = orientation,
                    hasShadow = isLastTileInHand
                )

                // Visual Overlays for Drag / Hover Tinting
                when {
                    isDragging -> {
                        Box(modifier = Modifier
                            .matchParentSize()
                            .background(DraggingOverlayBg, TileShape4)
                            .border(2.dp, WinningBorderColor, TileShape4)
                        )
                    }
                    isHoveredTarget -> {
                        Box(modifier = Modifier
                            .matchParentSize()
                            .background(HoverOverlayBg, TileShape4)
                        )
                    }
                }
            }
        }
    }
}
