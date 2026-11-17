package com.nowlsantos.mahjongai.presentation

import com.nowlsantos.mahjongai.fsm.game.GameStateMachine
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.model.PlayerWall
import com.nowlsantos.mahjongai.model.UIState
import com.nowlsantos.mahjongai.model.WallStack
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coordinates multi-round tile dealing from live walls to players during the distribution phase.
 * Extracted from GameViewModel to isolate sequential deal sequencing and atomic UI updates.
 */
class TileDistributionCoordinator(
    private val stateMachine: GameStateMachine,
    private val uiStateFlow: MutableStateFlow<UIState>,
    private val tileSortFunction: (List<MahjongTile>) -> List<MahjongTile>
) {
    private val tag = "TileDistCoordinator"

    suspend fun execute(distributor: TileDistributor, gameState: GameState) {
        val startTime = System.currentTimeMillis()
        val dealerIndex = gameState.dealerIndex
        var currentWallPlayer = gameState.targetWallPlayer
        val stacksPerMainBatch = 2 // 2 stacks = 4 tiles (2 top, 2 bottom)
        val totalRounds = 3 // 3 rounds = 12 tiles total per player

        // Phase 1: 3 rounds of 4-tile blocks (2 full stacks) to all 4 players (12 tiles each)
        for (round in 0 until totalRounds) {
            for (seatOffset in 0 until 4) {
                val currentPlayerIndex = (dealerIndex + seatOffset) % 4
                delay(500.milliseconds)

                val dealResult = distributor.dealFullStacks(stateMachine.currentState, currentWallPlayer, stacksCount = stacksPerMainBatch)
                currentWallPlayer = dealResult.nextWallPlayer
                updateUiAfterDeal(
                    currentPlayerIndex = currentPlayerIndex,
                    drawnTiles = dealResult.drawnTiles,
                    dealtBatches = dealResult.dealtBatch,
                    updatedWalls = dealResult.updatedGameState.playerWalls
                )
                Logger.d(tag, "Round ${round + 1}, Player $currentPlayerIndex: Dealt 4-tile block from wall.")
            }
        }

        // Phase 2: Final Single Tile Phase Part 1 -> Deals 1 single tile to each of the 4 players (Everyone has 13 tiles)
        for (seatOffset in 0 until 4) {
            val currentPlayerIndex = (dealerIndex + seatOffset) % 4
            delay(500.milliseconds)

            val dealResult = distributor.dealSingleTiles(stateMachine.currentState, currentWallPlayer, tilesCount = 1)
            currentWallPlayer = dealResult.nextWallPlayer
            updateUiAfterDeal(
                currentPlayerIndex = currentPlayerIndex,
                drawnTiles = dealResult.drawnTiles,
                dealtBatches = dealResult.dealtBatch,
                updatedWalls = dealResult.updatedGameState.playerWalls
            )
            Logger.d(tag, "13th Tile Deal: Player $currentPlayerIndex received 1 single tile (13 tiles total).")
        }

        // Phase 2: Final Single Tile Phase Part 2 -> Deals 1 extra single tile to the Dealer (Dealer has 14 tiles)
        delay(500.milliseconds)
        val dealerExtraResult = distributor.dealSingleTiles(stateMachine.currentState, currentWallPlayer, tilesCount = 1)
        currentWallPlayer = dealerExtraResult.nextWallPlayer
        updateUiAfterDeal(
            currentPlayerIndex = dealerIndex,
            drawnTiles = dealerExtraResult.drawnTiles,
            dealtBatches = dealerExtraResult.dealtBatch,
            updatedWalls = dealerExtraResult.updatedGameState.playerWalls
        )
        Logger.d(tag, "14th Extra Tile Deal: Dealer (Player $dealerIndex) received 1 extra tile (14 tiles total).")

        // Phase 3: Update player hands and preserve exact active live wall index and stack pointers
        stateMachine.updateState { state ->
            val sortedPlayers = state.players.mapValues { (idx, player) ->
                if (idx != 0) {
                    if (player.hand.size == 14) {
                        val first13 = tileSortFunction(player.hand.take(13))
                        player.copy(hand = first13 + player.hand.drop(13))
                    } else {
                        player.copy(hand = tileSortFunction(player.hand))
                    }
                } else player
            }
            state.copy(
                players = sortedPlayers,
                activeLiveWallIndex = currentWallPlayer
            )
        }

        // Complete distribution phase
        stateMachine.handleInput(GameInput.DistributionComplete)

        uiStateFlow.update { currentUi ->
            val sortedPlayerStates = currentUi.playerStates.mapValues { (idx, player) ->
                if (idx != 0) {
                    if (player.hand.size == 14) {
                        val first13 = tileSortFunction(player.hand.take(13))
                        player.copy(hand = first13 + player.hand.drop(13))
                    } else {
                        player.copy(hand = tileSortFunction(player.hand))
                    }
                } else player
            }
            currentUi.copy(
                playerStates = sortedPlayerStates,
                isDistributionComplete = true
            )
        }

        val totalDuration = System.currentTimeMillis() - startTime
        Logger.d(tag, "executeDistribution completed in ${totalDuration}ms.")
    }

    fun updateUiAfterDeal(
        currentPlayerIndex: Int,
        drawnTiles: List<MahjongTile>,
        dealtBatches: List<WallStack> = emptyList(),
        updatedWalls: Map<Int, PlayerWall>? = null
    ) {
        if (drawnTiles.isEmpty()) return

        stateMachine.updateState { state ->
            val targetSeat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = currentPlayerIndex)
            val playerState = state.players[currentPlayerIndex] ?: PlayerState(
                playerId = currentPlayerIndex,
                playerName = PlayerState.getDefaultPlayerName(currentPlayerIndex),
                isAi = currentPlayerIndex != 0,
                seat = targetSeat,
                screenPosition = targetSeat
            )
            val newHand = playerState.hand + drawnTiles
            val updatedPlayerState = playerState.copy(hand = newHand)
            state.copy(
                players = state.players + (currentPlayerIndex to updatedPlayerState),
                playerWalls = updatedWalls ?: state.playerWalls
            )
        }

        uiStateFlow.update { currentUi ->
            val targetSeat = PlayerState.getMahjongSeat(localPlayerId = 0, targetPlayerId = currentPlayerIndex)
            val playerState = currentUi.playerStates[currentPlayerIndex] ?: PlayerState(
                playerId = currentPlayerIndex,
                playerName = PlayerState.getDefaultPlayerName(currentPlayerIndex),
                isAi = currentPlayerIndex != 0,
                seat = targetSeat,
                screenPosition = targetSeat
            )
            val newHand = playerState.hand + drawnTiles
            val updatedPlayerState = playerState.copy(hand = newHand)

            val newDealtBatches = when (currentPlayerIndex) {
                0 -> currentUi.dealtBatches + dealtBatches
                1 -> currentUi.dealtBatchesRight + dealtBatches
                2 -> currentUi.dealtBatchesTop + dealtBatches
                3 -> currentUi.dealtBatchesLeft + dealtBatches
                else -> currentUi.dealtBatches
            }

            currentUi.copy(
                playerStates = currentUi.playerStates + (currentPlayerIndex to updatedPlayerState),
                dealtBatches = if (currentPlayerIndex == 0) newDealtBatches else currentUi.dealtBatches,
                dealtBatchesRight = if (currentPlayerIndex == 1) newDealtBatches else currentUi.dealtBatchesRight,
                dealtBatchesTop = if (currentPlayerIndex == 2) newDealtBatches else currentUi.dealtBatchesTop,
                dealtBatchesLeft = if (currentPlayerIndex == 3) newDealtBatches else currentUi.dealtBatchesLeft
            )
        }
    }
}
