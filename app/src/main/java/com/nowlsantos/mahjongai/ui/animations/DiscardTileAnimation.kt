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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.util.GameSoundManager
import com.nowlsantos.mahjongai.util.TileOrientation

@Composable
fun DiscardTileAnimation(
    startOffset: IntOffset,
    endOffset: IntOffset,
    tile: MahjongTile,
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
    val scale = remember { Animatable(1f) }

    LaunchedEffect(tile.id) {
        animatableOffset.snapTo(startOffset)
        animatableOffset.animateTo(
            targetValue = endOffset,
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        )
        // Play the tile discard clack the exact moment it touches the central discard pool
        effectiveSoundManager?.playTileDiscard()

        // Subtle tactile impact micro-bounce upon striking the table/pool
        scale.animateTo(1.08f, tween(30))
        scale.animateTo(1.0f, tween(30))

        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .offset { animatableOffset.value }
            .scale(scale.value)
            .zIndex(100f)
    ) {
        TileView(
            tile = tile,
            orientation = TileOrientation.DOWN,
            isAnimating = true
        )
    }
}
