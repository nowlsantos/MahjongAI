package com.nowlsantos.mahjongai.model

class PreciseWallManager(private var walls: Map<Int, PlayerWall>) {

    // Clockwise tracking: 0 (Bottom) -> 3 (Left) -> 2 (Top) -> 1 (Right)
    private var activeWallIndex = 0

    val currentActiveWallIndex: Int get() = activeWallIndex

    /**
     * Sets the starting wall based on the dice roll relative to the dealer.
     */
    fun setStartingPosition(wallIndex: Int) {
        activeWallIndex = wallIndex.coerceIn(0, 3)
    }

    /**
     * Draws [count] full stacks (2 tiles per stack) strictly CLOCKWISE around the 4 player walls.
     */
    fun drawFullStacks(count: Int): Pair<List<MahjongTile>, List<WallStack>> {
        val drawnTiles = mutableListOf<MahjongTile>()
        val drawnBatches = mutableListOf<WallStack>()
        var remainingStacksToDraw = count

        var safetyCounter = 0
        val maxLoops = 100

        while (remainingStacksToDraw > 0 && safetyCounter < maxLoops) {
            safetyCounter++

            check(activeWallIndex in 0..3) { "Critical error: activeWallIndex out of bounds ($activeWallIndex)" }
            var wall = walls[activeWallIndex] ?: break

            if (wall.isFullyDepleted || wall.isPointerAtEnd) {
                // If this wall reached its end (pointer >= 18), but still contains undrawn stacks at the start
                // (because drawing started at a break index > 0), wrap the pointer to the first available undrawn stack
                // so these remaining tiles can be drawn when the live wall circles back to this wall.
                if (!wall.isFullyDepleted && wall.isPointerAtEnd) {
                    val firstUndrawnIndex = wall.stacks.indexOfFirst { !it.isFullyDepleted }
                    if (firstUndrawnIndex != -1) {
                        val resetWall = wall.copy(currentStackPointer = firstUndrawnIndex)
                        walls = walls + (activeWallIndex to resetWall)
                    }
                }

                activeWallIndex = (activeWallIndex - 1 + 4) % 4

                if (walls.values.all { it.isFullyDepleted }) {
                    break
                }
                continue
            }

            val updatedStacks = wall.stacks.toMutableList()
            val currentStack = updatedStacks.getOrNull(wall.currentStackPointer)

            if (currentStack != null && !currentStack.isFullyDepleted) {
                if (!currentStack.isTopDrawn) drawnTiles.add(currentStack.topTile)
                if (!currentStack.isBottomDrawn) drawnTiles.add(currentStack.bottomTile)

                drawnBatches.add(currentStack)

                val fullyDrawnStack = currentStack.copy(isTopDrawn = true, isBottomDrawn = true)
                updatedStacks[wall.currentStackPointer] = fullyDrawnStack

                val nextPointer = if (wall.isAscending) wall.currentStackPointer + 1 else wall.currentStackPointer - 1

                val advancedWall = wall.copy(
                    stacks = updatedStacks,
                    currentStackPointer = nextPointer
                )

                if (advancedWall.isPointerAtEnd) {
                    if (!advancedWall.isFullyDepleted) {
                        val firstUndrawnIndex = advancedWall.stacks.indexOfFirst { !it.isFullyDepleted }
                        val resetWall = if (firstUndrawnIndex != -1) advancedWall.copy(currentStackPointer = firstUndrawnIndex) else advancedWall
                        walls = walls + (activeWallIndex to resetWall)
                    } else {
                        walls = walls + (activeWallIndex to advancedWall)
                    }
                    activeWallIndex = (activeWallIndex - 1 + 4) % 4
                } else {
                    walls = walls + (activeWallIndex to advancedWall)
                }
                remainingStacksToDraw--
            } else {
                val nextPointer = if (wall.isAscending) wall.currentStackPointer + 1 else wall.currentStackPointer - 1
                val advancedWall = wall.copy(currentStackPointer = nextPointer)
                if (advancedWall.isPointerAtEnd) {
                    if (!advancedWall.isFullyDepleted) {
                        val firstUndrawnIndex = advancedWall.stacks.indexOfFirst { !it.isFullyDepleted }
                        val resetWall = if (firstUndrawnIndex != -1) advancedWall.copy(currentStackPointer = firstUndrawnIndex) else advancedWall
                        walls = walls + (activeWallIndex to resetWall)
                    } else {
                        walls = walls + (activeWallIndex to advancedWall)
                    }
                    activeWallIndex = (activeWallIndex - 1 + 4) % 4
                } else {
                    walls = walls + (activeWallIndex to advancedWall)
                }
            }
        }

        return drawnTiles to drawnBatches
    }

    /**
     * Draws [tilesToDraw] individual single tiles strictly CLOCKWISE around the 4 player walls.
     */
    fun drawSingleTiles(tilesToDraw: Int): Pair<List<MahjongTile>, List<WallStack>> {
        val drawnTiles = mutableListOf<MahjongTile>()
        val drawnBatches = mutableListOf<WallStack>()
        var remainingTiles = tilesToDraw

        var safetyCounter = 0
        val maxLoops = 288

        while (remainingTiles > 0 && safetyCounter < maxLoops) {
            safetyCounter++

            check(activeWallIndex in 0..3) { "Critical error: activeWallIndex out of bounds ($activeWallIndex)" }
            var wall = walls[activeWallIndex] ?: break

            if (wall.isFullyDepleted || wall.isPointerAtEnd) {
                // If this wall reached its end (pointer >= 18), but still contains undrawn stacks at the start
                // (because drawing started at a break index > 0), wrap the pointer to the first available undrawn stack
                // so these remaining tiles can be drawn when the live wall circles back to this wall.
                if (!wall.isFullyDepleted && wall.isPointerAtEnd) {
                    val firstUndrawnIndex = wall.stacks.indexOfFirst { !it.isFullyDepleted }
                    if (firstUndrawnIndex != -1) {
                        val resetWall = wall.copy(currentStackPointer = firstUndrawnIndex)
                        walls = walls + (activeWallIndex to resetWall)
                    }
                }

                activeWallIndex = (activeWallIndex - 1 + 4) % 4

                if (walls.values.all { it.isFullyDepleted }) {
                    break
                }
                continue
            }

            val drawResult = wall.drawTile()
            if (drawResult != null) {
                val (tile, updatedWall, shouldAdvance) = drawResult
                drawnTiles.add(tile)

                val currentStack = updatedWall.stacks.getOrNull(wall.currentStackPointer)
                if (currentStack != null) {
                    drawnBatches.add(currentStack)
                }

                val finalWall = if (shouldAdvance) updatedWall.advancePointer() else updatedWall
                if (shouldAdvance && finalWall.isPointerAtEnd) {
                    if (!finalWall.isFullyDepleted) {
                        val firstUndrawnIndex = finalWall.stacks.indexOfFirst { !it.isFullyDepleted }
                        val resetWall = if (firstUndrawnIndex != -1) finalWall.copy(currentStackPointer = firstUndrawnIndex) else finalWall
                        walls = walls + (activeWallIndex to resetWall)
                    } else {
                        walls = walls + (activeWallIndex to finalWall)
                    }
                    activeWallIndex = (activeWallIndex - 1 + 4) % 4
                } else {
                    walls = walls + (activeWallIndex to finalWall)
                }
                remainingTiles--
            } else {
                val advancedWall = wall.advancePointer()
                if (advancedWall.isPointerAtEnd) {
                    if (!advancedWall.isFullyDepleted) {
                        val firstUndrawnIndex = advancedWall.stacks.indexOfFirst { !it.isFullyDepleted }
                        val resetWall = if (firstUndrawnIndex != -1) advancedWall.copy(currentStackPointer = firstUndrawnIndex) else advancedWall
                        walls = walls + (activeWallIndex to resetWall)
                    } else {
                        walls = walls + (activeWallIndex to advancedWall)
                    }
                    activeWallIndex = (activeWallIndex - 1 + 4) % 4
                } else {
                    walls = walls + (activeWallIndex to advancedWall)
                }
            }
        }

        return drawnTiles to drawnBatches
    }

    fun getUpdatedWalls(): Map<Int, PlayerWall> = walls
}
