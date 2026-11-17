package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.presentation.DiceResult

@Immutable
sealed interface GameInput {
    data class StartNewRound(val shuffledDeck: List<MahjongTile>) : GameInput
    data object GoDistribute : GameInput
    data object DistributionComplete : GameInput

    data class RollForDealer(val result: DiceResult? = null) : GameInput
    data class RollForTargetWall(val result: DiceResult? = null) : GameInput
    data class RollForWallBreak(val result: DiceResult? = null) : GameInput

    data class DrawRegularTile(val playerIndex: Int) : GameInput
    data class DrawReplacementTile(val playerIndex: Int) : GameInput

    data class StartDrawAnimation(
        val tile: MahjongTile,
        val playerIndex: Int,
        val seat: MahjongSeat,
        val startX: Float,
        val startY: Float,
        val targetX: Float,
        val targetY: Float
    ) : GameInput    data class CompleteDrawAnimation(
        val playerId: Int,
        val tile: MahjongTile
    ) : GameInput    data class StartDiscardAnimation(
        val tile: MahjongTile,
        val playerIndex: Int,
        val seat: MahjongSeat
    ) : GameInput    data class CompleteDiscardAnimation(
        val playerId: Int,
        val tile: MahjongTile
    ) : GameInput

    data class CompleteBonusAnimation(
        val playerId: Int,
        val tile: MahjongTile
    ) : GameInput

    data class CompleteMeldAnimation(
        val playerId: Int,
        val tile: MahjongTile
    ) : GameInput

    data class PerformInTurnKong(
        val playerIndex: Int
    ) : GameInput

    data class PerformBonusMeld(
        val tile: MahjongTile,
        val playerIndex: Int,
        val seat: MahjongSeat
    ) : GameInput

    data object ExecuteBonusExchange : GameInput
    data object ExecuteBonusExchangeComplete : GameInput
    data object SortBySuit: GameInput

    data class ExecuteDiscard(
        val playerId: Int,
        val tile: MahjongTile
    ) : GameInput    data class SubmitClaim(val claimIntent: ClaimIntent) : GameInput
    data class ClaimResolved(val activePlayerIndex: Int) : GameInput
    data object ExecuteClaimInterception : GameInput
    data class PauseGame(val isPaused: Boolean) : GameInput
    data class ToggleTileSelection(val tileId: Int) : GameInput

    data class ReorderHand(
        val playerId: Int,
        val newHand: List<MahjongTile>
    ) : GameInput

    data object StartNextRound : GameInput
    data object RestartMatch : GameInput
    data class DeclareSelfDrawWin(
        val playerIndex: Int,
        val winningTile: MahjongTile
    ) : GameInput
}
