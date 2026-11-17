package com.nowlsantos.mahjongai.ui.components.walls

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nowlsantos.mahjongai.R
import com.nowlsantos.mahjongai.model.WallStack
import com.nowlsantos.mahjongai.util.GameConstants
import kotlin.math.roundToInt

import com.nowlsantos.mahjongai.ui.components.tileDropShadow

@Composable
fun VerticalWallStack(
    modifier: Modifier = Modifier,
    stack: WallStack,
    showDepleted: Boolean = false,
    onPositioned: (Offset) -> Unit = {},
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    val drawableRes = R.drawable.vertical_wall_right
    val tileWidth = GameConstants.VERTICAL_TILE_WIDTH
    val tileHeight = GameConstants.VERTICAL_TILE_HEIGHT
    val verticalOverlap = 25.dp

    if (stack.isFullyDepleted && !showDepleted) {
        Spacer(
            modifier = modifier
                .size(width = tileWidth, height = tileHeight)
                .onGloballyPositioned { coordinate -> onPositioned(coordinate.positionInRoot()) }
        )
        return
    }

    Box(
        modifier = modifier
            .size(width = tileWidth, height = tileHeight + verticalOverlap)
            .onGloballyPositioned { coordinate -> onPositioned(coordinate.positionInRoot()) }
    ) {
        // Bottom tile: show if not bottom drawn, or if shown as a fully depleted dealt batch
        if (!stack.isBottomDrawn || (stack.isFullyDepleted && showDepleted)) {
            val bottomTileId = stack.bottomTile.id
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Wall Stack Bottom",
                modifier = Modifier
                    .size(width = tileWidth, height = tileHeight)
                    .tileDropShadow()
                    .onGloballyPositioned { coordinate ->
                        val pos = coordinate.positionInRoot()
                        onTilePositioned(bottomTileId, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()))
                    },
                contentScale = ContentScale.Fit
            )
        }

        // Top tile: show if not top drawn, and NOT fully depleted when showDepleted is active
        if (!stack.isTopDrawn && (!stack.isFullyDepleted || !showDepleted)) {
            val topTileId = stack.topTile.id
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Wall Stack Top",
                modifier = Modifier
                    .size(width = tileWidth, height = tileHeight)
                    .offset(y = -verticalOverlap)
                    .onGloballyPositioned { coordinate ->
                        val pos = coordinate.positionInRoot()
                        onTilePositioned(topTileId, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()))
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
