package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.engine.scoring.ScoringResult

@Immutable
data class RoundEndData(
    val winnerIndex: Int? = null,
    val discarderIndex: Int? = null,
    val winningTile: MahjongTile? = null,
    val isSelfDraw: Boolean = false,
    val isWinOnReplacement: Boolean = false,
    val isLastTileWall: Boolean = false,
    val isRobbingKong: Boolean = false,
    val isDraw: Boolean = false,
    val scoringResult: ScoringResult? = null,
    val chipTransactions: Map<Int, Int> = emptyMap(),
    val totalFan: Int = scoringResult?.totalFan ?: 0
)

@Immutable
data class GameOverData(
    val rankedPlayers: List<PlayerState> = emptyList()
)
