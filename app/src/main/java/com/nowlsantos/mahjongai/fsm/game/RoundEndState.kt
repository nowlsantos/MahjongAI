package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.engine.rl.RewardCalculator
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class RoundEndState : GamePhaseState {
    private val tag = "RoundEndState"

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: RoundEndState")

        val state = machine.currentState
        val existingData = state.roundEndData

        if (existingData != null && !existingData.isDraw && existingData.winnerIndex != null && existingData.chipTransactions.isEmpty()) {
            finalizeWinningRound(machine, state, existingData)
        } else if (existingData?.isDraw == true) {
            finalizeDrawRound(machine, state)
        }

        // Force UI refresh so all hands are revealed face-up and victory sheet shows
        machine.emitUiEffect(UiEffect.RefreshUI)
    }

    private suspend fun finalizeWinningRound(
        machine: GameStateMachine,
        state: com.nowlsantos.mahjongai.model.GameState,
        existingData: com.nowlsantos.mahjongai.model.RoundEndData
    ) {
        val winnerIndex = existingData.winnerIndex ?: return
        val winner = state.players[winnerIndex] ?: return

        val scoringResult = existingData.scoringResult ?: calculateWinnerFanScore(winner, state, existingData)
        if (scoringResult.totalFan < GameConstants.MIN_FAN_REQUIRED) {
            Logger.w(tag, "Player $winnerIndex declared a win with insufficient Fan (${scoringResult.totalFan} < ${GameConstants.MIN_FAN_REQUIRED}). Zeroing payment.")
        }

        val basePoints = FanCalculator.calculatePoints(scoringResult.totalFan)
        val chipTransactions = computeChipTransactions(
            winnerIndex = winnerIndex,
            isSelfDraw = existingData.isSelfDraw,
            discarderIndex = existingData.discarderIndex,
            basePoints = basePoints
        )

        // Update players' scores
        val updatedPlayers = state.players.mapValues { (pid, player) ->
            val delta = chipTransactions[pid] ?: 0
            player.copy(
                score = (player.score + delta).coerceAtLeast(0),
                isGameActive = false
            )
        }

        val finalizedData = existingData.copy(
            scoringResult = scoringResult,
            chipTransactions = chipTransactions,
            totalFan = scoringResult.totalFan
        )

        val record = com.nowlsantos.mahjongai.model.RoundRecord(
            roundNumber = state.roundNumber,
            prevalentWind = state.prevalentWind,
            dealerIndex = state.dealerIndex,
            winnerIndex = winnerIndex,
            discarderIndex = existingData.discarderIndex,
            isDraw = false,
            isSelfDraw = existingData.isSelfDraw,
            totalFan = scoringResult.totalFan,
            chipTransactions = chipTransactions
        )

        machine.updateState {
            it.copy(
                players = updatedPlayers,
                roundEndData = finalizedData,
                roundHistory = it.roundHistory + record,
                isGameActive = false
            )
        }
        machine.emitUiEffect(UiEffect.RefreshUI)

        Logger.d(tag, "Round End finalized: Winner=$winnerIndex, Fan=${scoringResult.totalFan}, Patterns=${scoringResult.patternNames}, Transactions=$chipTransactions")

        // Log terminal RL rewards for each AI player
        logAiTerminalRewards(
            updatedPlayers = updatedPlayers,
            winnerIndex = winnerIndex,
            isSelfDraw = existingData.isSelfDraw,
            discarderIndex = existingData.discarderIndex,
            scoringResult = scoringResult
        )

        // If any player is bankrupt (zero points), transition to GAME_OVER after a couple of seconds
        val isBankrupt = updatedPlayers.values.any { it.score <= 0 }
        if (isBankrupt) {
            Logger.d(tag, "Player bankruptcy detected. Transitioning to GAME_OVER in 2.5s...")
            delay(2500.milliseconds)
            machine.transitionTo(GamePhase.GAME_OVER)
        }
    }

    private fun calculateWinnerFanScore(
        winner: com.nowlsantos.mahjongai.model.PlayerState,
        state: com.nowlsantos.mahjongai.model.GameState,
        existingData: com.nowlsantos.mahjongai.model.RoundEndData
    ): com.nowlsantos.mahjongai.engine.scoring.ScoringResult {
        val seatWind = state.getPlayerSeatWind(winner.playerId)

        // The winning hand includes concealed hand plus winning tile if Ron
        val fullHand = if (existingData.isSelfDraw) {
            winner.hand
        } else if (existingData.winningTile != null && !winner.hand.any { it.id == existingData.winningTile.id }) {
            winner.hand + existingData.winningTile
        } else {
            winner.hand
        }

        return FanCalculator.calculateFan(
            hand = fullHand,
            revealedMelds = winner.revealedMelds,
            bonusTiles = winner.bonusTiles,
            isSelfDraw = existingData.isSelfDraw,
            seatWind = seatWind,
            prevalentWind = state.prevalentWind,
            isWinOnReplacement = existingData.isWinOnReplacement,
            isLastTileWall = existingData.isLastTileWall,
            isRobbingKong = existingData.isRobbingKong,
            turnNumber = winner.discards.size
        )
    }

    private fun computeChipTransactions(
        winnerIndex: Int,
        isSelfDraw: Boolean,
        discarderIndex: Int?,
        basePoints: Int
    ): Map<Int, Int> {
        val chipTransactions = mutableMapOf<Int, Int>()
        if (isSelfDraw) {
            // Self-Draw: All 3 opponents pay basePoints, winner collects 3 * basePoints
            var totalCollected = 0
            for (pid in 0..3) {
                if (pid != winnerIndex) {
                    chipTransactions[pid] = -basePoints
                    totalCollected += basePoints
                }
            }
            chipTransactions[winnerIndex] = totalCollected
        } else {
            // Win on Discard: Discarder pays 3 * basePoints to winner (shooter pays all)
            val discarder = discarderIndex ?: ((winnerIndex + 3) % 4)
            for (pid in 0..3) {
                chipTransactions[pid] = when (pid) {
                    winnerIndex -> 3 * basePoints
                    discarder -> -3 * basePoints
                    else -> 0
                }
            }
        }
        return chipTransactions
    }

    private fun logAiTerminalRewards(
        updatedPlayers: Map<Int, com.nowlsantos.mahjongai.model.PlayerState>,
        winnerIndex: Int,
        isSelfDraw: Boolean,
        discarderIndex: Int?,
        scoringResult: com.nowlsantos.mahjongai.engine.scoring.ScoringResult?
    ) {
        for (aiIndex in 1..3) {
            val pScore = updatedPlayers[aiIndex]?.score ?: GameConstants.STARTING_POINTS
            val isWinner = winnerIndex == aiIndex
            val isDealtInto = !isSelfDraw && discarderIndex == aiIndex
            val terminalReward = RewardCalculator.calculateTerminalReward(
                isWinner = isWinner,
                isSelfDraw = isSelfDraw,
                isDealtInto = isDealtInto,
                scoringResult = scoringResult
            )
            Logger.d(tag, "RL Terminal Reward for AI Player $aiIndex (${updatedPlayers[aiIndex]?.playerName}): reward=$terminalReward, score=$pScore")
        }
    }

    private fun finalizeDrawRound(
        machine: GameStateMachine,
        state: com.nowlsantos.mahjongai.model.GameState
    ) {
        Logger.d(tag, "Round End finalized as Exhaustive Draw (Lau Kuk). No score change.")
        for (aiIndex in 1..3) {
            val pScore = state.players[aiIndex]?.score ?: GameConstants.STARTING_POINTS
            val terminalReward = RewardCalculator.calculateTerminalReward(
                isWinner = false,
                isSelfDraw = false,
                isDealtInto = false,
                scoringResult = null
            )
            Logger.d(tag, "RL Terminal Reward for AI Player $aiIndex: reward=$terminalReward, score=$pScore")
        }
        val record = com.nowlsantos.mahjongai.model.RoundRecord(
            roundNumber = state.roundNumber,
            prevalentWind = state.prevalentWind,
            dealerIndex = state.dealerIndex,
            winnerIndex = null,
            discarderIndex = null,
            isDraw = true,
            isSelfDraw = false,
            totalFan = 0,
            chipTransactions = mapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
        )
        machine.updateState {
            it.copy(
                roundHistory = it.roundHistory + record,
                isGameActive = false
            )
        }
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when (input) {
            is GameInput.StartNextRound -> {
                handleStartNextRound(machine)
            }
            is GameInput.RestartMatch -> {
                val resetPlayers = machine.currentState.players.mapValues { (_, player) ->
                    player.copy(
                        hand = emptyList(),
                        revealedMelds = emptyList(),
                        bonusTiles = emptyList(),
                        discards = emptyList(),
                        targetClaimTile = null,
                        targetClaimDiscarderIndex = null,
                        isTing = false,
                        isGameActive = false,
                        score = GameConstants.STARTING_POINTS
                    )
                }
                machine.updateState { s ->
                    s.copy(
                        roundNumber = 1,
                        dealerIndex = 0,
                        prevalentWind = HonorType.EAST,
                        players = resetPlayers,
                        roundEndData = null,
                        roundHistory = emptyList(),
                        gameOverData = null,
                        isGameActive = false,
                        publicDiscards = emptyList(),
                        lastDiscardedTile = null,
                        lastDiscarderIndex = null,
                        diceResult = null,
                        firstRollResult = null,
                        secondRollResult = null
                    )
                }
                machine.transitionTo(GamePhase.SETUP)
            }
            else -> {}
        }
    }

    private suspend fun handleStartNextRound(machine: GameStateMachine) {
        val state = machine.currentState
        val isGameOver = state.players.values.any { it.score <= 0 }

        if (isGameOver) {
            Logger.d(tag, "Match termination criteria met (player bankrupt). Transitioning to GAME_OVER.")
            machine.transitionTo(GamePhase.GAME_OVER)
            return
        }

        val winnerIndex = state.roundEndData?.winnerIndex
        val isDealerWin = winnerIndex != null && winnerIndex == state.dealerIndex

        // Dealer rotation: Dealer stays if they won (Ren-zhuang); otherwise rotates counter-clockwise
        val nextDealer = if (isDealerWin) {
            Logger.d(tag, "Dealer won: Dealer stays as Player ${state.dealerIndex} (Ren-zhuang).")
            state.dealerIndex
        } else {
            val rotated = (state.dealerIndex + 1) % 4
            Logger.d(tag, "Dealer rotates from Player ${state.dealerIndex} to Player $rotated.")
            rotated
        }

        val nextRound = state.roundNumber + 1
        val nextPrevalentWind = when {
            nextRound <= 4 -> HonorType.EAST
            nextRound <= 8 -> HonorType.SOUTH
            nextRound <= 12 -> HonorType.WEST
            else -> HonorType.NORTH
        }

        Logger.d(tag, "Starting Round $nextRound with Dealer=$nextDealer, PrevalentWind=$nextPrevalentWind")

        val resetPlayers = state.players.mapValues { (_, player) ->
            player.copy(
                hand = emptyList(),
                revealedMelds = emptyList(),
                bonusTiles = emptyList(),
                discards = emptyList(),
                targetClaimTile = null,
                targetClaimDiscarderIndex = null,
                isTing = false,
                isGameActive = false
            )
        }

        machine.updateState { s ->
            s.copy(
                roundNumber = nextRound,
                dealerIndex = nextDealer,
                prevalentWind = nextPrevalentWind,
                players = resetPlayers,
                roundEndData = null,
                isGameActive = false,
                publicDiscards = emptyList(),
                lastDiscardedTile = null,
                lastDiscarderIndex = null,
                diceResult = null,
                firstRollResult = null,
                secondRollResult = null
            )
        }

        machine.transitionTo(GamePhase.SETUP)
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "Exit:: RoundEndState")
    }
}
