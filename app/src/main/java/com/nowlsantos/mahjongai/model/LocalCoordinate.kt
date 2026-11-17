package com.nowlsantos.mahjongai.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

data class LocalCoordinate(
    val horizontalSize: IntSize = IntSize.Companion.Zero, // Size of Top/Bottom player hand containers
    val verticalSize: IntSize = IntSize.Companion.Zero, // Size of Right/Left player hand containers
    val leftOffset: Offset = Offset.Companion.Zero,
    val rightOffset: Offset = Offset.Companion.Zero,
    val topOffset: Offset = Offset.Companion.Zero,
    val bottomOffset: Offset = Offset.Companion.Zero,
    val centerOffset: Offset = Offset.Companion.Zero, // Center of the playing area (needed for Played Card animation)
    val containerSize: IntSize = IntSize.Companion.Zero // Size of the root container (needed for Orchestrators)
) {
    companion object {
        fun initial(): LocalCoordinate = LocalCoordinate(
            horizontalSize = IntSize(0, 0),
            verticalSize = IntSize(0, 0),
            leftOffset = Offset(0f, 0f),
            rightOffset = Offset(0f, 0f),
            topOffset = Offset(0f, 0f),
            bottomOffset = Offset(0f, 0f),
            centerOffset = Offset(0f, 0f), // Initialize all properties
            containerSize = IntSize(0, 0)  // Initialize all properties
        )
    }
}