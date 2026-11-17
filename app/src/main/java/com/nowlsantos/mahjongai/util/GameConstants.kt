package com.nowlsantos.mahjongai.util

import androidx.compose.ui.unit.dp

object GameConstants {
    /**
     * Minimum Fan score required to declare a win (Hu), whether via Self-Draw or Discard (雞胡起胡 / 零番起胡).
     * Set to 0 so that all complete hands (including basic Chicken Hands, Seven Pairs, etc.)
     * are legally permitted to declare a win for human players.
     */
    const val MIN_FAN_REQUIRED = 0

    /*
    *Standard starting points
    */
    const val STARTING_POINTS = 20000

    /**
     * Minimum Fan score required for AI players to declare a win (Hu / Tsumo / Ron).
     * AI players enforce a 1-Fan minimum (一番起胡), completely eliminating 0-Fan
     * Chicken Hands (雞胡) while maintaining natural, exciting Mahjong pacing.
     */
    const val AI_MIN_FAN_REQUIRED = 1

    val HORIZONTAL_TILE_WIDTH = 44.dp
    val HORIZONTAL_TILE_HEIGHT = 73.dp

    val VERTICAL_TILE_WIDTH = 66.dp
    val VERTICAL_TILE_HEIGHT = 58.dp

    val VERTICAL_OFFSET_TOP_BOTTOM = 24.dp
    val VERTICAL_OFFSET_RIGHT_LEFT = 36.dp
    val VERTICAL_OFFSET_UP = 39.dp
    val VERTICAL_OFFSET_DOWN = 40.dp

    val HUMAN_TILE_WIDTH_DOWN = 50.dp
    val HUMAN_TILE_HEIGHT_DOWN = 84.dp
    val HUMAN_TILE_WIDTH_UP = 56.dp
    val HUMAN_TILE_HEIGHT_UP = 92.dp

    val AI_TILE_WIDTH_DOWN = 76.dp
    val AI_TILE_HEIGHT_DOWN = 66.dp
    val AI_TILE_WIDTH_UP = 50.dp    //46.dp
    val AI_TILE_HEIGHT_UP = 80.dp    //74.dp

    val VERTICAL_TILE_WIDTH_UP = 44.dp
    val VERTICAL_TILE_HEIGHT_UP = 84.dp
}
