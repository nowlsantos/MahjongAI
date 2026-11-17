package com.nowlsantos.mahjongai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.TileOrientation
import kotlin.math.roundToInt

@Composable
fun CentralDiscardPool(
    modifier: Modifier = Modifier,
    discards: List<MahjongTile> = emptyList(),
    onTargetCalculated: (IntOffset) -> Unit = {},
    onTilePositioned: (Int, IntOffset) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current

    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var containerRootPos by remember { mutableStateOf(IntOffset.Zero) }

    // Variable vertical offset for discard rows - change or update this value as needed
    val verticalOffset = 28.dp

    // Variable vertical offset for succeeding layers stacked on top of existing tiles - change or update as needed
    val layerVerticalOffset = 4.dp

    // Tile dimensions and layout configuration
    val baseWidthDp = GameConstants.HUMAN_TILE_WIDTH_DOWN
    val baseHeightDp = GameConstants.HUMAN_TILE_HEIGHT_DOWN
    val baseSpacingDp = 1.dp
    val maxItemsInEachRow = 14
    val maxRowsPerLayer = 3
    val maxTilesPerLayer = maxItemsInEachRow * maxRowsPerLayer

    val tileWidthPx = with(density) { baseWidthDp.roundToPx() }
    val tileHeightPx = with(density) { baseHeightDp.roundToPx() }
    val spacingPx = with(density) { baseSpacingDp.roundToPx() }
    val verticalOffsetPx = with(density) { verticalOffset.toPx() }
    val layerVerticalOffsetPx = with(density) { layerVerticalOffset.toPx() }

    val baseWPx = tileWidthPx.toFloat()
    val baseHPx = tileHeightPx.toFloat()
    val baseSpacingPx = spacingPx.toFloat()

    // Fixed dimensions of the complete discard grid block computed from discrete tile pixels
    val gridWidthPx = (tileWidthPx * maxItemsInEachRow) + (spacingPx * (maxItemsInEachRow - 1))
    val gridHeightPx = tileHeightPx.toFloat() + (tileHeightPx.toFloat() - verticalOffsetPx) * (maxRowsPerLayer - 1)
    val gridWidthDp = with(density) { gridWidthPx.toDp() }
    val gridHeightDp = with(density) { gridHeightPx.toDp() }

    // Track measured positions of the base layer slots to ensure upper layers stack exactly on top
    val slotPositions = remember { mutableMapOf<Int, IntOffset>() }
    // Local cache prevents redundant coordinate emissions on layout passes
    val positionCache = remember { mutableMapOf<Int, IntOffset>() }

    // Pure mathematical function to compute exact grid slot coordinates for any discard index fallback
    val calculateTargetOffset: (Int) -> IntOffset = { globalIndex ->
        val layerIndex = globalIndex / maxTilesPerLayer
        val slotInLayer = globalIndex % maxTilesPerLayer
        val rowIndex = slotInLayer / maxItemsInEachRow
        val colIndex = slotInLayer % maxItemsInEachRow

        val gridStartX = containerRootPos.x + (containerWidth - gridWidthPx) / 2f
        val gridStartY = containerRootPos.y + (containerHeight - gridHeightPx) / 2f

        // Use measured origin of slot 0 if available to eliminate sub-pixel rounding drift
        val originX = slotPositions[0]?.x?.toFloat() ?: gridStartX
        val originY = slotPositions[0]?.y?.toFloat() ?: gridStartY

        if (layerIndex > 0 && slotPositions.containsKey(slotInLayer)) {
            // Upper layer: stack directly on top of the base layer's measured slot position
            val basePos = slotPositions[slotInLayer]!!
            IntOffset(
                x = basePos.x,
                y = (basePos.y - layerVerticalOffsetPx * layerIndex).roundToInt()
            )
        } else {
            val targetX = originX + colIndex * (baseWPx + baseSpacingPx)
            val targetY = originY + rowIndex * (baseHPx - verticalOffsetPx) - layerIndex * layerVerticalOffsetPx
            IntOffset(targetX.roundToInt(), targetY.roundToInt())
        }
    }

    // Unified target offset calculator: computes the exact target offset for the NEXT incoming discard tile.
    // - If pool is empty -> Slot 0 (top-left of layer 0).
    // - Whenever a claim is made and a tile is removed from discards -> updates target offset to last index + tile width.
    // - When a row reaches its 15-tile limit -> wraps to column 0 of the next row.
    // - When layer 0 reaches its 45-tile limit -> wraps to top-left of the SECOND LAYER.
    // - Otherwise -> advances horizontally from the last tile's measured position: lastTilePos.x + tile width + spacing.
    val computeTargetOffset: () -> IntOffset = {
        val gridStartX = containerRootPos.x + (containerWidth - gridWidthPx) / 2f
        val gridStartY = containerRootPos.y + (containerHeight - gridHeightPx) / 2f
        val originX = slotPositions[0]?.x?.toFloat() ?: gridStartX
        val originY = slotPositions[0]?.y?.toFloat() ?: gridStartY

        if (discards.isEmpty()) {
            IntOffset(originX.roundToInt(), originY.roundToInt())
        } else {
            val lastIndex = discards.lastIndex
            val lastTile = discards.last()
            val lastTilePos = positionCache[lastTile.id] ?: slotPositions[lastIndex % maxTilesPerLayer]

            val nextGlobalIndex = discards.size
            val nextLayerIndex = nextGlobalIndex / maxTilesPerLayer
            val nextSlotInLayer = nextGlobalIndex % maxTilesPerLayer
            val nextRowIndex = nextSlotInLayer / maxItemsInEachRow

            val currentLayerIndex = lastIndex / maxTilesPerLayer
            val currentSlotInLayer = lastIndex % maxTilesPerLayer
            val currentRowIndex = currentSlotInLayer / maxItemsInEachRow

            when {
                // 1. Layer limit reached (45 tiles: 15 items x 3 rows) -> Wrap to top-left of the SECOND LAYER (elevated by layerVerticalOffset)
                nextLayerIndex > currentLayerIndex -> {
                    IntOffset(
                        x = originX.roundToInt(),
                        y = (originY - layerVerticalOffsetPx * nextLayerIndex).roundToInt()
                    )
                }
                // 2. Row limit reached (15 tiles in row) -> Wrap to column 0 of the next row (far left position)
                nextRowIndex > currentRowIndex -> {
                    IntOffset(
                        x = originX.roundToInt(),
                        y = (originY + nextRowIndex * (baseHPx - verticalOffsetPx) - nextLayerIndex * layerVerticalOffsetPx).roundToInt()
                    )
                }
                // 3. Normal horizontal advance in the same row -> last index + tile width + spacing
                lastTilePos != null -> {
                    IntOffset(
                        x = (lastTilePos.x + baseWPx + baseSpacingPx).roundToInt(),
                        y = lastTilePos.y
                    )
                }
                // Fallback using pure mathematical slot calculation
                else -> {
                    calculateTargetOffset(nextGlobalIndex)
                }
            }
        }
    }

    // Automatically and dynamically update target offset whenever discards change (added OR removed via claim)
    // or when layout bounds settle.
    LaunchedEffect(discards, containerWidth, containerHeight, containerRootPos) {
        if (discards.isEmpty()) {
            slotPositions.clear()
            positionCache.clear()
        } else {
            // Clean up cached positions for any tiles that were claimed and removed from discards
            val activeTileIds = discards.map { it.id }.toSet()
            positionCache.keys.retainAll(activeTileIds)
            slotPositions.keys.retainAll((0 until discards.size.coerceAtMost(maxTilesPerLayer)).toSet())
        }
        if (containerWidth > 0f && containerHeight > 0f) {
            onTargetCalculated(computeTargetOffset())
        }
    }

    val layers = remember(discards, maxTilesPerLayer) {
        if (discards.isEmpty()) emptyList() else discards.chunked(maxTilesPerLayer)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            //.background(TealDrop)
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width.toFloat()
                containerHeight = coordinates.size.height.toFloat()
                val pos = coordinates.positionInRoot()
                containerRootPos = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
            },
        contentAlignment = Alignment.Center,
    ) {
        layers.forEachIndexed { layerIndex, layerDiscards ->
            key(layerIndex) {
                Box(
                    modifier = Modifier
                        .width(gridWidthDp)
                        .height(gridHeightDp)
                        .zIndex((layerIndex + 1) * 100f)
                        .offset(y = -layerVerticalOffset * layerIndex),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier.wrapContentSize(align = Alignment.TopStart, unbounded = true),
                        verticalArrangement = Arrangement.spacedBy(-verticalOffset, Alignment.Top)
                    ) {
                        layerDiscards.chunked(maxItemsInEachRow).forEachIndexed { rowIndex, rowTiles ->
                            Row(
                                modifier = Modifier
                                    .zIndex(rowIndex.toFloat())
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true),
                                horizontalArrangement = Arrangement.spacedBy(baseSpacingDp, Alignment.Start)
                            ) {
                                rowTiles.forEachIndexed { colIndex, tile ->
                                    key(tile.id) {
                                        val itemIndexInLayer = rowIndex * maxItemsInEachRow + colIndex
                                        val globalIndex = layerIndex * maxTilesPerLayer + itemIndexInLayer
                                        val isLatestDiscard = globalIndex == discards.lastIndex

                                        Box(
                                            modifier = Modifier
                                                .onGloballyPositioned { coordinates ->
                                                    val rootPos = coordinates.positionInRoot()
                                                    val offset = IntOffset(rootPos.x.roundToInt(), rootPos.y.roundToInt())
                                                    if (positionCache[tile.id] != offset) {
                                                        positionCache[tile.id] = offset
                                                        onTilePositioned(tile.id, offset)
                                                    }

                                                    if (layerIndex == 0) {
                                                        slotPositions[itemIndexInLayer] = offset
                                                    }

                                                    // Automatically update the next target offset based on the latest discard.
                                                    // When a row reaches 15 tiles, it wraps to column 0 of the next row (far left position).
                                                    // When the first layer reaches 45 tiles, it wraps to top-left of the SECOND LAYER.
                                                    // Otherwise, it advances horizontally: last index + tile width + spacing.
                                                    if (isLatestDiscard) {
                                                        onTargetCalculated(computeTargetOffset())
                                                    }
                                                }
                                        ) {
                                            TileView(
                                                tile = tile,
                                                orientation = TileOrientation.DOWN,
                                                customWidth = baseWidthDp,
                                                customHeight = baseHeightDp
                                            )

                                            if (isLatestDiscard && discards.size > 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .border(3.dp, BrightYellow, RoundedCornerShape(6.dp))
                                                        //.background(BrightYellow.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
