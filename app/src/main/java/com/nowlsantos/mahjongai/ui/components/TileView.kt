package com.nowlsantos.mahjongai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nowlsantos.mahjongai.R
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.TileImageMapper
import com.nowlsantos.mahjongai.util.TileOrientation

/**
 * Creates a rectangular drop shadow matching the tile geometry.
 * Uses drawWithCache to cache Paint and pixel metric conversions,
 * completely eliminating frame-by-frame memory allocations across all tiles.
 */
fun Modifier.tileDropShadow(
    color: Color = FlatBlack,
    alpha: Float = 0.25f,
    shadowBlurRadius: Dp = 6.dp,
    offsetX: Dp = 14.dp,
    offsetY: Dp = 1.dp,
    cornerRadius: Dp = 4.dp
): Modifier = this.drawWithCache {
    val shadowColorArgb = color.copy(alpha = alpha).toArgb()
    val shadowBlurPx = shadowBlurRadius.toPx()
    val offsetXPx = offsetX.toPx()
    val offsetYPx = offsetY.toPx()
    val cornerRadiusPx = cornerRadius.toPx()

    val frameworkPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = 0
        setShadowLayer(
            shadowBlurPx,
            offsetXPx,
            offsetYPx,
            shadowColorArgb
        )
    }

    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                cornerRadiusPx,
                cornerRadiusPx,
                frameworkPaint
            )
        }
    }
}

@Composable
fun TileView(
    tile: MahjongTile,
    modifier: Modifier = Modifier,
    orientation: TileOrientation = TileOrientation.DOWN,
    isFacedown: Boolean = false,
    isAnimating: Boolean = false,
    hasShadow: Boolean = !isAnimating,
    customWidth: Dp? = null,
    customHeight: Dp? = null
) {
    val resId = if (isFacedown) {
        when (orientation) {
            TileOrientation.LEFT -> R.drawable.vertical_wall_left
            TileOrientation.RIGHT -> R.drawable.vertical_wall_right
            TileOrientation.UP, TileOrientation.DOWN -> R.drawable.horizontal_wall
        }
    } else {
        TileImageMapper.getTileDrawableRes(tile, orientation)
    }

    val defaultSize = when (orientation) {
        TileOrientation.UP, TileOrientation.DOWN -> DpSize(
            width = GameConstants.HUMAN_TILE_WIDTH_UP,
            height = GameConstants.HUMAN_TILE_HEIGHT_UP
        )
        TileOrientation.LEFT, TileOrientation.RIGHT -> DpSize(
            width = GameConstants.AI_TILE_WIDTH_DOWN,
            height = GameConstants.AI_TILE_HEIGHT_DOWN
        )
    }

    val finalWidth = customWidth ?: defaultSize.width
    val finalHeight = customHeight ?: defaultSize.height

    val baseModifier = modifier
        .size(width = finalWidth, height = finalHeight)
        .then(
            if (hasShadow) {
                Modifier.tileDropShadow()
            } else {
                Modifier
            }
        )

    Box(modifier = baseModifier) {
        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = if (isFacedown) "Facedown Tile" else "Mahjong Tile ${tile.suit} ${tile.value}",
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
