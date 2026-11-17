package com.nowlsantos.mahjongai.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.util.GameSoundManager
import com.nowlsantos.mahjongai.util.TileOrientation
import kotlinx.coroutines.launch

@Composable
fun MeldTileAnimation(
    startOffset: IntOffset,
    endOffset: IntOffset,
    tile: MahjongTile,
    seat: MahjongSeat = MahjongSeat.BOTTOM,
    animationType: AnimationType = AnimationType.PUNG_TILES,
    isFacedown: Boolean = false,
    soundManager: GameSoundManager? = null,
    onAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val fallbackSoundManager = remember { if (soundManager == null) GameSoundManager(context) else null }
    val effectiveSoundManager = soundManager ?: fallbackSoundManager

    DisposableEffect(fallbackSoundManager) {
        onDispose { fallbackSoundManager?.release() }
    }

    val animatableOffset = remember(tile.id) { Animatable(startOffset, IntOffset.VectorConverter) }
    val scaleAnim = remember(tile.id) { Animatable(1.0f) }

    val orientation = when (seat) {
        MahjongSeat.BOTTOM -> TileOrientation.DOWN
        MahjongSeat.TOP -> TileOrientation.DOWN
        MahjongSeat.LEFT -> TileOrientation.LEFT
        MahjongSeat.RIGHT -> TileOrientation.RIGHT
    }

    LaunchedEffect(tile.id, seat, animationType) {
        when (animationType) {
            AnimationType.CHOW_TILES -> effectiveSoundManager?.playTileChow()
            AnimationType.PUNG_TILES -> effectiveSoundManager?.playTilePung()
            AnimationType.KONG_TILES -> effectiveSoundManager?.playTileKong()
            else -> effectiveSoundManager?.playTilePung()
        }
        animatableOffset.snapTo(startOffset)

        // Arc & scale up in mid-flight to emphasize the claimed tile being lifted
        launch {
            scaleAnim.animateTo(1.15f, tween(180, easing = FastOutSlowInEasing))
            scaleAnim.animateTo(1.0f, tween(200, easing = FastOutSlowInEasing))
        }

        animatableOffset.animateTo(
            targetValue = endOffset,
            animationSpec = tween(
                durationMillis = 380,
                easing = FastOutSlowInEasing
            )
        )
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .offset { animatableOffset.value }
            .zIndex(1500f)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
    ) {
        TileView(
            tile = tile,
            orientation = orientation,
            isFacedown = isFacedown,
            isAnimating = true
        )
    }
}
