package com.nowlsantos.mahjongai.ui.components.walls

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
fun HorizontalWallStack(
    modifier: Modifier = Modifier,
    stack: WallStack,
    showDepleted: Boolean = false,
    hasShadow: Boolean = false,
    onPositioned: (Offset) -> Unit = {},
    onTilePositioned: (tileId: Int, offset: IntOffset) -> Unit = { _, _ -> }
) {
    val drawableRes = R.drawable.horizontal_wall
    val tileWidth = GameConstants.HORIZONTAL_TILE_WIDTH
    val tileHeight = GameConstants.HORIZONTAL_TILE_HEIGHT
    val verticalOffset = GameConstants.VERTICAL_OFFSET_TOP_BOTTOM

    if (stack.isFullyDepleted && !showDepleted) {
        Spacer(
            modifier = modifier
                .size(width = tileWidth, height = tileHeight)
                .onGloballyPositioned { coordinate ->
                    onPositioned(coordinate.positionInRoot())
                }
        )
        return
    }

    val shouldShowShadow = hasShadow || (stack.isTopDrawn && !stack.isBottomDrawn)

    Box(
        modifier = modifier
            .size(width = tileWidth, height = tileHeight + verticalOffset)
            .onGloballyPositioned { coordinate ->
                onPositioned(coordinate.positionInRoot())
            }
    ) {
        // Bottom tile: show if not bottom drawn, or if shown as a fully depleted dealt batch
        if (!stack.isBottomDrawn || (stack.isFullyDepleted && showDepleted)) {
            val bottomTileId = stack.bottomTile.id
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Wall Stack Bottom",
                modifier = Modifier
                    .padding(end = 1.dp)
                    .size(width = tileWidth, height = tileHeight)
                    .then(if (shouldShowShadow) Modifier.tileDropShadow() else Modifier)
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coordinate ->
                        val pos = coordinate.positionInRoot()
                        onTilePositioned(bottomTileId, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()))
                    }
            )
        }
        // Top tile: show if not top drawn, and NOT fully depleted when showDepleted is active
        if (!stack.isTopDrawn && (!stack.isFullyDepleted || !showDepleted)) {
            val topTileId = stack.topTile.id
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Wall Stack Top",
                modifier = Modifier
                    .padding(end = 1.dp)
                    .size(width = tileWidth, height = tileHeight)
                    .align(Alignment.BottomCenter)
                    .offset(y = -verticalOffset)
                    .onGloballyPositioned { coordinate ->
                        val pos = coordinate.positionInRoot()
                        onTilePositioned(topTileId, IntOffset(pos.x.roundToInt(), pos.y.roundToInt()))
                    },
                alignment = Alignment.Center,
                contentScale = ContentScale.Fit
            )
        }
    }
}
