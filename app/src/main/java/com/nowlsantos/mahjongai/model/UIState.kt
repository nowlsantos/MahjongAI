package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.presentation.UiEffect

@Immutable
data class UIState(
    val playerStates: Map<Int, PlayerState> = emptyMap(),
    val dealtBatches: List<WallStack> = emptyList(),
    val dealtBatchesRight: List<WallStack> = emptyList(),
    val dealtBatchesTop: List<WallStack> = emptyList(),
    val dealtBatchesLeft: List<WallStack> = emptyList(),
    val eastWindPlayerIndex: Int = 0,
    val isRollingDice: Boolean = false,
    val eastWindBadgePosition: MahjongSeat? = null,
    val isDistributionComplete: Boolean = false,
    val uiEffect: UiEffect? = null,
    val selectedTileIds: Set<Int> = emptySet(),
    val availableClaims: Set<ClaimType> = emptySet(),
    val legalClaimIntents: List<com.nowlsantos.mahjongai.engine.flow.ClaimIntent> = emptyList(),
    val lastKongPaymentDeltas: Map<Int, Int>? = null,
    val kongPaymentEventId: Long = 0L
)
