package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable

/**
 * Represents a single vertical stack of two tiles in a Mahjong wall segment.
 */
@Immutable
data class WallStack(
    val stackIndex: Int, // Position in wall (0..17)
    val topTile: MahjongTile,
    val bottomTile: MahjongTile,
    val isTopDrawn: Boolean = false,
    val isBottomDrawn: Boolean = false
) {
    val isFullyDepleted: Boolean get() = isTopDrawn && isBottomDrawn
    val remainingCount: Int get() = (if (!isTopDrawn) 1 else 0) + (if (!isBottomDrawn) 1 else 0)

    /**
     * Draws the next available tile from this stack (top layer first, then bottom).
     * Returns a pair containing the drawn tile and the updated immutable stack.
     */
    fun drawNextTile(): Pair<MahjongTile, WallStack>? {
        return when {
            !isTopDrawn -> topTile to copy(isTopDrawn = true)
            !isBottomDrawn -> bottomTile to copy(isBottomDrawn = true)
            else -> null // Stack is empty
        }
    }
}

/**
 * Represents one of the four player walls (East, South, West, North) consisting of 18 stacks.
 */
@Immutable
data class PlayerWall(
    val playerIndex: Int, // Seat wind index (0 = East, 1 = South, 2 = West, 3 = North)
    val stacks: List<WallStack>, // Exactly 18 stacks
    val currentStackPointer: Int = 0, // Tracks current active stack position
    val isAscending: Boolean = true // Direction of traversing the wall
) {
    val isFullyDepleted: Boolean get() = stacks.all { it.isFullyDepleted }
    val currentSize: Int get() = stacks.sumOf { it.remainingCount }

    /**
     * Checks if the current stack pointer has reached or passed the end of this wall segment.
     */
    val isPointerAtEnd: Boolean
        get() = if (isAscending) currentStackPointer >= stacks.size else currentStackPointer < 0

    /**
     * Draws a single tile from the current active stack pointer.
     * Returns the tile, the updated wall, and a boolean indicating if the pointer needs to advance.
     */
    fun drawTile(): Triple<MahjongTile, PlayerWall, Boolean>? {
        val targetStack = stacks.getOrNull(currentStackPointer) ?: return null
        val result = targetStack.drawNextTile() ?: return null

        val (tile, updatedStack) = result
        val updatedStacks = stacks.toMutableList().apply { set(currentStackPointer, updatedStack) }

        // Determine if we need to advance the pointer
        val shouldAdvance = updatedStack.isFullyDepleted

        return Triple(
            tile,
            copy(stacks = updatedStacks),
            shouldAdvance
        )
    }

    fun advancePointer(): PlayerWall {
        val nextPointer = if (isAscending) currentStackPointer + 1 else currentStackPointer - 1
        return copy(currentStackPointer = nextPointer)
    }
}
