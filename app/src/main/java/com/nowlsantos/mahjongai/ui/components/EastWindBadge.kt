package com.nowlsantos.mahjongai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.FlatBlack

@Composable
fun EastWindBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BrightYellow, RoundedCornerShape(4.dp))
            .padding(6.dp, vertical = 3.dp)
    ) {
        Text(
            text = "東 East",
            color = FlatBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}