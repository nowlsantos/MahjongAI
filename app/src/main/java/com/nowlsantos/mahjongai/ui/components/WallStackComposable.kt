package com.nowlsantos.mahjongai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nowlsantos.mahjongai.model.WallStack

@Composable
private fun WallStackComposable(
    stack: WallStack,
    isHorizontal: Boolean,
    modifier: Modifier = Modifier
) {
    val tag = "WallStackComposable"

    if (stack.isFullyDepleted) {
        // Render empty slot space when depleted
        Spacer(
            modifier = modifier.size(
                if (isHorizontal) 20.dp else 28.dp,
                if (isHorizontal) 28.dp else 20.dp
            )
        )
        return
    }

    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(2.dp))
    ) {
        // Renders visual offset simulating top/bottom tile layers
        Column(
            modifier = Modifier.padding(1.dp)
        ) {
            if (!stack.isTopDrawn) {
                Box(
                    modifier = Modifier
                        .size(if (isHorizontal) 18.dp else 24.dp, if (isHorizontal) 24.dp else 18.dp)
                        .background(Color(0xFF4CAF50))
                )
            }
            if (!stack.isBottomDrawn) {
                Box(
                    modifier = Modifier
                        .size(if (isHorizontal) 18.dp else 24.dp, if (isHorizontal) 24.dp else 18.dp)
                        .background(Color(0xFFC8E6C9)) // Base grey/light green layer
                )
            }
        }
    }
}