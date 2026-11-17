package com.nowlsantos.mahjongai.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerScoreBadge(
    modifier: Modifier = Modifier,
    score: Int,
    seat: MahjongSeat = MahjongSeat.BOTTOM,
    chipDelta: Int? = null,
    deltaKey: Long = 0L
) {
    var visibleDelta by remember(deltaKey) { mutableStateOf(if (deltaKey > 0L) chipDelta else null) }
    val offsetX = remember(deltaKey) { Animatable(0f) }
    val offsetY = remember(deltaKey) { Animatable(0f) }
    val alpha = remember(deltaKey) { Animatable(1f) }
    val scale = remember(deltaKey) { Animatable(0.7f) }

    // Seat-aware float trajectory: float toward open table viewport, avoiding screen edges/notches
    val (targetOffsetX, targetOffsetY) = when (seat) {
        MahjongSeat.TOP -> 0f to 36f       // Float downward into open table
        MahjongSeat.BOTTOM -> 0f to -36f   // Float upward into open table
        MahjongSeat.LEFT -> 32f to -18f    // Float rightward & upward into table
        MahjongSeat.RIGHT -> -32f to 18f   // Float leftward & downward into table
    }

    LaunchedEffect(deltaKey, chipDelta) {
        if (deltaKey > 0L && chipDelta != null && chipDelta != 0) {
            visibleDelta = chipDelta
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            alpha.snapTo(1f)
            scale.snapTo(0.7f)

            launch {
                scale.animateTo(1.25f, tween(160, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, tween(120, easing = LinearOutSlowInEasing))
            }
            launch {
                offsetX.animateTo(targetOffsetX, tween(2000, easing = LinearOutSlowInEasing))
            }
            launch {
                offsetY.animateTo(targetOffsetY, tween(2000, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(1400.milliseconds)
                alpha.animateTo(0f, tween(600, easing = FastOutLinearInEasing))
                visibleDelta = null
            }
        } else {
            visibleDelta = null
        }
    }

    Box(
        modifier = modifier.zIndex(1100f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%,d".format(score),
                color = BrightYellow,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        visibleDelta?.let { delta ->
            if (delta != 0) {
                val isPositive = delta > 0
                val text = if (isPositive) "+%,d".format(delta) else "%,d".format(delta)
                val textColor = if (isPositive) Color(0xFF4ADE80) else Color(0xFFFF6B6B)
                val bgColor = if (isPositive) Color(0xF0064E3B) else Color(0xF07F1D1D)
                val borderColor = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(1.5.dp, borderColor),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(if (seat == MahjongSeat.TOP) Alignment.BottomCenter else Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                x = offsetX.value.roundToInt().dp.roundToPx(),
                                y = offsetY.value.roundToInt().dp.roundToPx()
                            )
                        }
                        .graphicsLayer {
                            this.alpha = alpha.value
                            this.scaleX = scale.value
                            this.scaleY = scale.value
                        }
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
