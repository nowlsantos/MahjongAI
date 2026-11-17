package com.nowlsantos.mahjongai.validation

import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.engine.logic.HandEvaluator
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.util.GameConstants

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    data class SuccessWithData<T>(val data: T) : ValidationResult()
}

fun ValidationResult.isError(): Boolean = this is ValidationResult.Error
fun ValidationResult.isSuccess(): Boolean = this is ValidationResult.Success

class GameValidator {

    fun validateDiscard(
        gameState: GameState,
        playerIndex: Int = 0,
        discardedTile: MahjongTile,
        selectedTiles: List<MahjongTile>
    ): ValidationResult {
        if (gameState.gamePhase != GamePhase.PLAYING) {
            return ValidationResult.Error("Cannot discard outside of playing phase.")
        }
        if (gameState.activePlayerIndex != playerIndex) {
            return ValidationResult.Error("It is not your turn to discard.")
        }

        val playerState = gameState.players[playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        val has14Tiles = (playerState.hand.size + playerState.revealedMelds.size * 3) % 3 == 2
        if (!has14Tiles) {
            return ValidationResult.Error("You cannot discard until you draw a tile.")
        }

        if (selectedTiles.isEmpty()) {
            return ValidationResult.Error("No selected tile to discard.")
        }

        if (selectedTiles.size != 1) {
            return ValidationResult.Error("You can only discard one tile.")
        }

        if (discardedTile.isBonus) {
            return ValidationResult.Error("You cannot discard a Bonus Tile.")
        }

        if (!playerState.hand.any { it.id == discardedTile.id }) {
            return ValidationResult.Error("The tile to discard is not in your hand.")
        }

        return ValidationResult.Success
    }

    fun validateDraw(
        gameState: GameState,
        playerIndex: Int
    ): ValidationResult {
        if (gameState.gamePhase != GamePhase.PLAYING) {
            return ValidationResult.Error("Cannot draw tile outside of playing phase.")
        }
        if (gameState.activePlayerIndex != playerIndex) {
            return ValidationResult.Error("It is not your turn to draw.")
        }

        val playerState = gameState.players[playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        val has13Tiles = (playerState.hand.size + playerState.revealedMelds.size * 3) % 3 == 1
        if (!has13Tiles) {
            return ValidationResult.Error("Cannot draw: You already hold 14 tiles.")
        }

        return ValidationResult.Success
    }

    fun validateClaim(
        gameState: GameState,
        claimIntent: ClaimIntent
    ): ValidationResult {
        if (gameState.gamePhase != GamePhase.CLAIM_INTERCEPTION) {
            return ValidationResult.Error("Cannot claim outside of claim interception phase.")
        }

        // Pass is always valid
        if (claimIntent.claimType == ClaimType.PASS) {
            return ValidationResult.Success
        }

        val targetTile = gameState.lastDiscardedTile
            ?: return ValidationResult.Error("No discarded tile to claim.")

        val discarderIndex = gameState.lastDiscarderIndex
            ?: return ValidationResult.Error("No discarder recorded.")

        val playerState = gameState.players[claimIntent.playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        val seatWind = gameState.getPlayerSeatWind(claimIntent.playerIndex)

        val legalClaims = MoveGenerator.getLegalClaims(
            playerIndex = claimIntent.playerIndex,
            playerHand = playerState.hand,
            revealedMelds = playerState.revealedMelds,
            bonusTiles = playerState.bonusTiles,
            discarderIndex = discarderIndex,
            discardedTile = targetTile,
            seatWind = seatWind,
            prevalentWind = gameState.prevalentWind
        )

        val isLegal = legalClaims.any { legal ->
            legal.claimType == claimIntent.claimType &&
            (claimIntent.tilesToMelded.isEmpty() ||
                legal.tilesToMelded.map { it.suit to it.value }.sortedBy { it.second } ==
                claimIntent.tilesToMelded.map { it.suit to it.value }.sortedBy { it.second })
        }

        if (!isLegal) {
            return ValidationResult.Error("Illegal claim: ${claimIntent.claimType} is not valid for this discard.")
        }

        return ValidationResult.Success
    }

    fun validateInTurnKong(
        gameState: GameState,
        playerIndex: Int
    ): ValidationResult {
        if (gameState.gamePhase != GamePhase.PLAYING) {
            return ValidationResult.Error("Cannot declare Kong outside of playing phase.")
        }
        if (gameState.activePlayerIndex != playerIndex) {
            return ValidationResult.Error("It is not your turn to declare Kong.")
        }

        val playerState = gameState.players[playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        val seatWind = gameState.getPlayerSeatWind(playerIndex)

        val legalActions = MoveGenerator.getLegalTurnActions(
            playerHand = playerState.hand,
            revealedMelds = playerState.revealedMelds,
            bonusTiles = playerState.bonusTiles,
            seatWind = seatWind,
            prevalentWind = gameState.prevalentWind
        )

        val hasLegalKong = legalActions.any {
            it is TurnAction.ConcealedKong || it is TurnAction.AddedKong
        }

        if (!hasLegalKong) {
            return ValidationResult.Error("You do not hold a legal Concealed or Added Kong.")
        }

        return ValidationResult.Success
    }

    fun validateBonusMeld(
        gameState: GameState,
        playerIndex: Int,
        tile: MahjongTile
    ): ValidationResult {
        if (!tile.isBonus) {
            return ValidationResult.Error("Selected tile is not a bonus tile.")
        }

        val playerState = gameState.players[playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        if (!playerState.hand.any { it.id == tile.id }) {
            return ValidationResult.Error("The bonus tile is not in your hand.")
        }

        return ValidationResult.Success
    }

    fun validateSelfDrawWin(
        gameState: GameState,
        playerIndex: Int,
        winningTile: MahjongTile
    ): ValidationResult {
        if (gameState.gamePhase != GamePhase.PLAYING) {
            return ValidationResult.Error("Cannot declare win outside of playing phase.")
        }
        if (gameState.activePlayerIndex != playerIndex) {
            return ValidationResult.Error("It is not your turn to declare win.")
        }

        val playerState = gameState.players[playerIndex]
            ?: return ValidationResult.Error("Player not found.")

        if (!HandEvaluator.isWinningHand(playerState.hand, playerState.revealedMelds.size)) {
            return ValidationResult.Error("Hand is not a valid winning hand.")
        }

        val seatWind = gameState.getPlayerSeatWind(playerIndex)

        val fanScore = FanCalculator.calculateFan(
            hand = playerState.hand,
            revealedMelds = playerState.revealedMelds,
            bonusTiles = playerState.bonusTiles,
            isSelfDraw = true,
            seatWind = seatWind,
            prevalentWind = gameState.prevalentWind
        )

        if (fanScore.totalFan < GameConstants.MIN_FAN_REQUIRED) {
            return ValidationResult.Error("Hand does not satisfy minimum Fan requirement (${GameConstants.MIN_FAN_REQUIRED} Fan).")
        }

        return ValidationResult.Success
    }
}
