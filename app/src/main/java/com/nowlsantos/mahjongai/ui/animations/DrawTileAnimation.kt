package com.nowlsantos.mahjongai.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.GameSoundManager
import com.nowlsantos.mahjongai.util.TileImageMapper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DrawTileAnimation(
    startOffset: IntOffset,
    endOffset: IntOffset,
    tile: MahjongTile,
    seat: MahjongSeat = MahjongSeat.BOTTOM,
    soundManager: GameSoundManager? = null,
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val fallbackSoundManager = remember { if (soundManager == null) GameSoundManager(context) else null }
    val effectiveSoundManager = soundManager ?: fallbackSoundManager

    DisposableEffect(fallbackSoundManager) {
        onDispose { fallbackSoundManager?.release() }
    }

    val animatableOffset = remember { Animatable(startOffset, IntOffset.VectorConverter) }
    val progress = remember { Animatable(0f) }
    val drawableRes = TileImageMapper.getPlayerTileDrawable(seat)

    LaunchedEffect(tile.id, seat) {
        // Trigger tile draw sound as the tile separates from the wall stack
        effectiveSoundManager?.playTileDraw()

        animatableOffset.snapTo(startOffset)
        progress.snapTo(0f)

        coroutineScope {
            launch {
                animatableOffset.animateTo(
                    targetValue = endOffset,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 360,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
        onAnimationFinished()
    }

    // Parabolic elevation arc: lifts into the air above adjacent tiles, then settles into hand
    val arc = sin(progress.value * PI.toFloat())
    val scale = 1f + 0.14f * arc
    val liftY = (-22f * arc).toInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(animatableOffset.value.x, animatableOffset.value.y + liftY) }
            .scale(scale)
            .zIndex(100f)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "Drawing Tile Back",
            modifier = Modifier.size(width = GameConstants.AI_TILE_WIDTH_UP, height = GameConstants.AI_TILE_HEIGHT_UP)
        )
    }
}
