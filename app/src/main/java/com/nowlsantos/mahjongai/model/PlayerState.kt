package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import com.nowlsantos.mahjongai.util.GameConstants

@Immutable
data class Meld(
    val type: MeldType,
    val tiles: List<MahjongTile>
)

enum class MeldType {
    CHOW, PUNG, KONG, CONCEALED_KONG, FLOWER
}

enum class MahjongSeat {
    TOP, LEFT, BOTTOM, RIGHT
}

enum class PlayerType {
    HUMAN, AI
}

@Immutable
data class PlayerState(
    val playerId: Int,
    val playerName: String,
    val isAi: Boolean,
    val seat: MahjongSeat,
    val hand: List<MahjongTile> = emptyList(),
    val revealedMelds: List<Meld> = emptyList(),
    val bonusTiles: List<MahjongTile> = emptyList(), // Flowers and Seasons in HK Mahjong
    val discards: List<MahjongTile> = emptyList(),
    val targetClaimTile: MahjongTile? = null,
    val targetClaimDiscarderIndex: Int? = null,
    val isTing: Boolean = false,
    val isGameActive: Boolean = false,
    val score: Int = GameConstants.STARTING_POINTS,
    val screenPosition: MahjongSeat = MahjongSeat.BOTTOM
) {
    companion object {
        /**
         * Calculates the screen position of any target player relative to the local human player.
         * This allows the local player to always sit at the BOTTOM of the UI screen.
         *
         * @param localPlayerId The seat index of the human player (e.g., 0).
         * @param targetPlayerId The seat index of the player being evaluated (0..3).
         */
        fun getMahjongSeat(localPlayerId: Int, targetPlayerId: Int): MahjongSeat {
            val relativeOffset = (targetPlayerId - localPlayerId + 4) % 4
            return when (relativeOffset) {
                0 -> MahjongSeat.BOTTOM
                1 -> MahjongSeat.RIGHT
                2 -> MahjongSeat.TOP
                3 -> MahjongSeat.LEFT
                else -> MahjongSeat.BOTTOM
            }
        }

        fun getDefaultPlayerName(playerId: Int): String {
            return when (playerId) {
                0 -> "RedDragon"
                1 -> "NineGates"
                2 -> "NorthWind"
                3 -> "DotFury"
                else -> "Player $playerId"
            }
        }
    }
}
