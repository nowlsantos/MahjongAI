package com.nowlsantos.mahjongai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseWallManagerTest {

    private fun createTestWalls(targetBreakWall: Int, breakIndex: Int): Map<Int, PlayerWall> {
        var tileId = 0
        return (0..3).associateWith { playerIndex ->
            val stacks = (0 until 18).map { stackIdx ->
                WallStack(
                    stackIndex = stackIdx,
                    topTile = MahjongTile(tileId++, Suit.BAMBOO, (stackIdx % 9) + 1),
                    bottomTile = MahjongTile(tileId++, Suit.BAMBOO, (stackIdx % 9) + 1),
                    isTopDrawn = false,
                    isBottomDrawn = false
                )
            }
            PlayerWall(
                playerIndex = playerIndex,
                stacks = stacks,
                currentStackPointer = if (playerIndex == targetBreakWall) breakIndex else 0,
                isAscending = true
            )
        }
    }

    @Test
    fun testBrokenWallWrapsAroundToDrawBeginningStacks() {
        // Wall 2 starts at stack 8 (so stacks 0..7 are remaining on Wall 2)
        val walls = createTestWalls(targetBreakWall = 2, breakIndex = 8)
        val manager = PreciseWallManager(walls)
        manager.setStartingPosition(2)

        // Draw stacks 8..17 on Wall 2 (10 stacks * 2 = 20 tiles)
        val (tilesFromWall2FirstPass, _) = manager.drawSingleTiles(20)
        assertEquals(20, tilesFromWall2FirstPass.size)

        // Next wall clockwise should be Wall 1
        assertEquals(1, manager.currentActiveWallIndex)

        // Draw entire Wall 1 (18 stacks * 2 = 36 tiles)
        val (tilesFromWall1, _) = manager.drawSingleTiles(36)
        assertEquals(36, tilesFromWall1.size)
        assertEquals(0, manager.currentActiveWallIndex)

        // Draw entire Wall 0 (36 tiles)
        val (tilesFromWall0, _) = manager.drawSingleTiles(36)
        assertEquals(36, tilesFromWall0.size)
        assertEquals(3, manager.currentActiveWallIndex)

        // Draw entire Wall 3 (36 tiles)
        val (tilesFromWall3, _) = manager.drawSingleTiles(36)
        assertEquals(36, tilesFromWall3.size)
        assertEquals(2, manager.currentActiveWallIndex)

        // Now we arrive back at Wall 2. Stacks 0..7 (16 tiles) should still be available!
        val (tilesFromWall2SecondPass, _) = manager.drawSingleTiles(16)
        assertEquals("Remaining 16 tiles (stacks 0..7) on Wall 2 must be drawn without getting stuck", 16, tilesFromWall2SecondPass.size)

        // Now all 144 tiles from the 4 walls must be depleted
        val updatedWalls = manager.getUpdatedWalls()
        assertTrue(updatedWalls.values.all { it.isFullyDepleted })

        // Attempting to draw further must return 0 tiles cleanly without infinite looping
        val (emptyDraw, _) = manager.drawSingleTiles(1)
        assertTrue(emptyDraw.isEmpty())
    }
}
