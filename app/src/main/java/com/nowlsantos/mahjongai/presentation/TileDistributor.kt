package com.nowlsantos.mahjongai.presentation

import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PreciseWallManager
import com.nowlsantos.mahjongai.model.WallStack

class TileDistributor {

    data class DealResult(
        val updatedGameState: GameState,
        val nextWallPlayer: Int,
        val drawnTiles: List<MahjongTile>,
        val dealtBatch: List<WallStack>
    )

    /**
     * Deals [stacksCount] full stacks (2 tiles per stack) starting from [targetWallPlayer].
     */
    fun dealFullStacks(
        currentState: GameState,
        targetWallPlayer: Int,
        stacksCount: Int = 2
    ): DealResult {
        val wallManager = PreciseWallManager(currentState.playerWalls).apply {
            setStartingPosition(targetWallPlayer)
        }

        val (drawnTiles, drawnBatches) = wallManager.drawFullStacks(stacksCount)
        val updatedWalls = wallManager.getUpdatedWalls()
        val nextWallPlayer = wallManager.currentActiveWallIndex

        val updatedGameState = currentState.copy(playerWalls = updatedWalls)

        return DealResult(
            updatedGameState = updatedGameState,
            nextWallPlayer = nextWallPlayer,
            drawnTiles = drawnTiles,
            dealtBatch = drawnBatches
        )
    }

    /**
     * Deals [tilesCount] individual single tiles starting from [targetWallPlayer].
     */
    fun dealSingleTiles(
        currentState: GameState,
        targetWallPlayer: Int,
        tilesCount: Int = 1
    ): DealResult {
        val wallManager = PreciseWallManager(currentState.playerWalls).apply {
            setStartingPosition(targetWallPlayer)
        }

        val (drawnTiles, drawnBatches) = wallManager.drawSingleTiles(tilesCount)
        val updatedWalls = wallManager.getUpdatedWalls()
        val nextWallPlayer = wallManager.currentActiveWallIndex

        val updatedGameState = currentState.copy(playerWalls = updatedWalls)

        return DealResult(
            updatedGameState = updatedGameState,
            nextWallPlayer = nextWallPlayer,
            drawnTiles = drawnTiles,
            dealtBatch = drawnBatches
        )
    }
}
