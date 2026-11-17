package com.nowlsantos.mahjongai.ui.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.TileOrientation

/**
 * Generic reusable composable for rendering player melds (Chow, Pung, Kong)
 * and bonus/flower tiles as distinct grouped clusters across all 4 seats.
 */
@Composable
fun PlayerMelds(
    modifier: Modifier = Modifier,
    seat: MahjongSeat,
    bonusTiles: List<MahjongTile> = emptyList(),
    revealedMelds: List<Meld> = emptyList(),
    isRevealed: Boolean = false,
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    val posCache = remember { mutableMapOf<Int, IntOffset>() }
    val isVertical = (seat == MahjongSeat.LEFT || seat == MahjongSeat.RIGHT)
    val tileOrientation = when (seat) {
        MahjongSeat.BOTTOM -> TileOrientation.DOWN
        MahjongSeat.TOP -> TileOrientation.DOWN
        MahjongSeat.LEFT -> TileOrientation.LEFT
        MahjongSeat.RIGHT -> TileOrientation.RIGHT
    }

    if (bonusTiles.isEmpty() && revealedMelds.isEmpty()) return

    if (isVertical) {
        // Vertical Seats (Left & Right): Column of distinct grouped meld clusters
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Grouped Bonus/Flower Tiles
            if (bonusTiles.isNotEmpty()) {
                val verticalOffset = GameConstants.VERTICAL_OFFSET_DOWN
                val tileHeight = GameConstants.AI_TILE_HEIGHT_DOWN
                val totalHeight = verticalOffset * (bonusTiles.size - 1) + tileHeight

                Box(
                    modifier = Modifier
                        .width(GameConstants.AI_TILE_WIDTH_DOWN)
                        .height(totalHeight)
                ) {
                    bonusTiles.forEachIndexed { index, tile ->
                        TileView(
                            tile = tile,
                            orientation = tileOrientation,
                            modifier = Modifier
                                .offset(y = verticalOffset * index)
                                .zIndex(index.toFloat())
                                .onGloballyPositioned { coordinates ->
                                    val pos = coordinates.positionInRoot()
                                    val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                    if (posCache[tile.id] != offset) {
                                        posCache[tile.id] = offset
                                        onTilePositioned(tile.id, offset)
                                    }
                                }
                        )
                    }
                }
            }

            // 2. Individual Revealed Melds (Chow, Pung, Kong as separate groups)
            for (meld in revealedMelds) {
                val isConcealedKong = (meld.type == MeldType.CONCEALED_KONG) && !isRevealed
                val meldTiles = if (meld.type == MeldType.CHOW) meld.tiles.sortedBy { it.value } else meld.tiles
                if (meldTiles.isNotEmpty()) {
                    val verticalOffset = GameConstants.VERTICAL_OFFSET_DOWN
                    val tileHeight = GameConstants.AI_TILE_HEIGHT_DOWN
                    val totalHeight = verticalOffset * (meldTiles.size - 1) + tileHeight

                    Box(
                        modifier = Modifier
                            .width(GameConstants.AI_TILE_WIDTH_DOWN)
                            .height(totalHeight)
                    ) {
                        meldTiles.forEachIndexed { index, tile ->
                            TileView(
                                tile = tile,
                                orientation = tileOrientation,
                                isFacedown = isConcealedKong,
                                modifier = Modifier
                                    .offset(y = verticalOffset * index)
                                    .zIndex(index.toFloat())
                                    .onGloballyPositioned { coordinates ->
                                        val pos = coordinates.positionInRoot()
                                        val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                        if (posCache[tile.id] != offset) {
                                            posCache[tile.id] = offset
                                            onTilePositioned(tile.id, offset)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Horizontal Seats (Bottom & Top): Row of distinct grouped meld clusters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // 1. Bonus/Flower Tiles Group
            if (bonusTiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    for (tile in bonusTiles) {
                        TileView(
                            tile = tile,
                            orientation = tileOrientation,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInRoot()
                                val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                if (posCache[tile.id] != offset) {
                                    posCache[tile.id] = offset
                                    onTilePositioned(tile.id, offset)
                                }
                            }
                        )
                    }
                }
            }

            // 2. Individual Revealed Melds (Chow, Pung, Kong as separate groups)
            for (meld in revealedMelds) {
                val isConcealedKong = (meld.type == MeldType.CONCEALED_KONG) && !isRevealed
                val meldTiles = if (meld.type == MeldType.CHOW) meld.tiles.sortedBy { it.value } else meld.tiles
                if (meldTiles.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        for (tile in meldTiles) {
                            TileView(
                                tile = tile,
                                orientation = tileOrientation,
                                isFacedown = isConcealedKong,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    val pos = coordinates.positionInRoot()
                                    val offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                    if (posCache[tile.id] != offset) {
                                        posCache[tile.id] = offset
                                        onTilePositioned(tile.id, offset)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
