package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable

@Immutable
data class RoundRecord(
    val roundNumber: Int,
    val prevalentWind: HonorType = HonorType.EAST,
    val dealerIndex: Int = 0,
    val winnerIndex: Int? = null,
    val discarderIndex: Int? = null,
    val isDraw: Boolean = false,
    val isSelfDraw: Boolean = false,
    val totalFan: Int = 0,
    val chipTransactions: Map<Int, Int> = emptyMap()
)
