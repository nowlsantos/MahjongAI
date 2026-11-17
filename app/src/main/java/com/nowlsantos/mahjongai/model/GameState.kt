package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.presentation.DiceResult

enum class Wind(val turnOffset: Int, val displayName: String) {
    EAST(0, "East"),
    SOUTH(1, "South"),
    WEST(2, "West"),
    NORTH(3, "North")
}

@Immutable
data class GameState(
    val gamePhase: GamePhase = GamePhase.SETUP,
    val roundNumber: Int = 1,
    val dealerIndex: Int = 0,
    val activePlayerIndex: Int = 0,
    val isGameActive: Boolean = false,

    // Board & Wall (Pure Domain)
    val playerWalls: Map<Int, PlayerWall> = emptyMap(),
    val targetWallPlayer: Int = 0,
    val eastWindPlayerIndex: Int = 0,
    val prevalentWind: HonorType = HonorType.EAST,
    val publicDiscards: List<MahjongTile> = emptyList(),
    val lastDiscardedTile: MahjongTile? = null,
    val lastDiscarderIndex: Int? = null,
    val activeLiveWallIndex: Int = 0,
    val isLastDrawReplacement: Boolean = false,
    val deadWallPlayer: Int = 0,
    val deadWallStackPointer: Int = 0,

    // Players
    val players: Map<Int, PlayerState> = emptyMap(),

    // Dice rolling
    val diceResult: DiceResult? = null,
    val firstRollResult: DiceResult? = null,
    val secondRollResult: DiceResult? = null,

    // Round End & Match Completion
    val roundEndData: RoundEndData? = null,
    val gameOverData: GameOverData? = null,
    val roundHistory: List<RoundRecord> = emptyList()
) {
    /**
     * Resolves the dynamic Seat Wind of a player relative to the round's dealer.
     * In Mahjong rules, the Dealer is always East Wind (莊家為東), and seat winds
     * rotate counter-clockwise: Dealer = East, Next = South, Opposite = West, Previous = North.
     */
    fun getPlayerSeatWind(playerIndex: Int): HonorType {
        return getSeatWind(playerIndex, dealerIndex)
    }

    companion object {
        fun getSeatWind(playerIndex: Int, dealerIndex: Int): HonorType {
            val offset = (playerIndex - dealerIndex + 4) % 4
            return when (offset) {
                0 -> HonorType.EAST
                1 -> HonorType.SOUTH
                2 -> HonorType.WEST
                else -> HonorType.NORTH
            }
        }

        fun initializePlayers(localPlayerId: Int = 0): Map<Int, PlayerState> {
            return (0..3).associateWith { playerId ->
                val seat = PlayerState.getMahjongSeat(localPlayerId, playerId)
                PlayerState(
                    playerId = playerId,
                    playerName = PlayerState.getDefaultPlayerName(playerId),
                    isAi = playerId != localPlayerId,
                    seat = seat,
                    screenPosition = seat
                )
            }
        }
    }
}
