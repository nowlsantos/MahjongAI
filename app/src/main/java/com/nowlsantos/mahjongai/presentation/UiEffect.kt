package com.nowlsantos.mahjongai.presentation

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile

@Immutable
sealed interface UiEffect {
    data class ShowEastWindBadge(val mahjongSeat: MahjongSeat): UiEffect

    data class ExecuteDistribution(val tileDistributor: TileDistributor): UiEffect

    data class StartDiscardTileAnimation(val tile: MahjongTile, val playerIndex: Int, val seat: MahjongSeat) : UiEffect

    data class StartDrawTileAnimation(
        val tile: MahjongTile,
        val playerIndex: Int,
        val seat: MahjongSeat,
        val startX: Float,
        val startY: Float,
        val targetX: Float,
        val targetY: Float
    ) : UiEffect

    data class StartBonusTileAnimation(val tile: MahjongTile, val playerIndex: Int, val seat: MahjongSeat) : UiEffect

    data class StartMeldTileAnimation(
        val tile: MahjongTile,
        val playerIndex: Int,
        val seat: MahjongSeat,
        val animationType: com.nowlsantos.mahjongai.ui.animations.AnimationType,
        val isFromDiscard: Boolean = true,
        val isConcealed: Boolean = false
    ) : UiEffect

    data class TriggerAiDrawRegularTile(val playerIndex: Int) : UiEffect

    data class PromptHumanClaim(val legalClaims: List<com.nowlsantos.mahjongai.engine.flow.ClaimIntent>) : UiEffect

    data object ClearHumanClaim : UiEffect

    data object RefreshUI : UiEffect

    data class ShowKongPayment(val deltas: Map<Int, Int>) : UiEffect

    data class ShowError(val error: String): UiEffect
}
