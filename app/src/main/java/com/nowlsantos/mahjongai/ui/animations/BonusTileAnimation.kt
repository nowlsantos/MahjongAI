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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.util.GameSoundManager
import com.nowlsantos.mahjongai.util.TileOrientation

@Composable
fun BonusTileAnimation(
    startOffset: IntOffset,
    endOffset: IntOffset,
    tile: MahjongTile,
    seat: MahjongSeat = MahjongSeat.BOTTOM,
    animationType: AnimationType = AnimationType.BONUS_TILE,
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
    val orientation = when (seat) {
        MahjongSeat.BOTTOM -> TileOrientation.DOWN
        MahjongSeat.TOP -> TileOrientation.DOWN
        MahjongSeat.LEFT -> TileOrientation.LEFT
        MahjongSeat.RIGHT -> TileOrientation.RIGHT
    }

    LaunchedEffect(tile.id, seat, animationType) {
        effectiveSoundManager?.playTileBonus()
        animatableOffset.snapTo(startOffset)
        animatableOffset.animateTo(
            targetValue = endOffset,
            animationSpec = tween(
                durationMillis = 260,
                easing = FastOutSlowInEasing
            )
        )
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .offset { animatableOffset.value }
            .zIndex(100f)
    ) {
        TileView(
            tile = tile,
            orientation = orientation,
            isAnimating = true
        )
    }
}
