package com.nowlsantos.mahjongai.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

enum class Suit {
    CHARACTER, BAMBOO, DOT, HONOR, FLOWER, SEASON
}

enum class HonorType {
    EAST, SOUTH, WEST, NORTH, RED, GREEN, WHITE
}

enum class FlowerType {
    PLUM, ORCHID, CHRYSANTHEMUM, BAMBOO_FLOWER
}

enum class SeasonType {
    SPRING, SUMMER, AUTUMN, WINTER
}

@Immutable
data class MahjongTile(
    val id: Int, // Unique identifier 0..143
    val suit: Suit,
    val value: Int, // 1..9 for suits (1..9), 1..7 for Honors, 1..4 for Flowers/Seasons
    val honorType: HonorType? = null,
    val flowerType: FlowerType? = null,
    val seasonType: SeasonType? = null
) : Comparable<MahjongTile> {

    init {
        require(id in 0..143) { "Tile ID must be between 0 and 143. Found: $id" }
        when (suit) {
            Suit.HONOR -> require(honorType != null) { "Honor tile (ID $id) must have a non-null honorType." }
            Suit.FLOWER -> require(flowerType != null) { "Flower tile (ID $id) must have a non-null flowerType." }
            Suit.SEASON -> require(seasonType != null) { "Season tile (ID $id) must have a non-null seasonType." }
            else -> {}
        }
    }

    val isSuitTile: Boolean get() = suit == Suit.CHARACTER || suit == Suit.BAMBOO || suit == Suit.DOT
    val isHonor: Boolean get() = suit == Suit.HONOR
    val isBonus: Boolean get() = suit == Suit.FLOWER || suit == Suit.SEASON

    val isTerminal: Boolean get() = isSuitTile && (value == 1 || value == 9)
    val isSimple: Boolean get() = isSuitTile && value in 2..8

    val displayName: String
        get() = when (suit) {
            Suit.CHARACTER -> "$value Character"
            Suit.BAMBOO -> "$value Bamboo"
            Suit.DOT -> "$value Dot"
            Suit.HONOR -> when (honorType) {
                HonorType.EAST -> "East Wind"
                HonorType.SOUTH -> "South Wind"
                HonorType.WEST -> "West Wind"
                HonorType.NORTH -> "North Wind"
                HonorType.RED -> "Red Dragon"
                HonorType.GREEN -> "Green Dragon"
                HonorType.WHITE -> "White Dragon"
                null -> "Honor"
            }
            Suit.FLOWER -> when (flowerType) {
                FlowerType.PLUM -> "Plum Flower"
                FlowerType.ORCHID -> "Orchid Flower"
                FlowerType.CHRYSANTHEMUM -> "Chrysanthemum Flower"
                FlowerType.BAMBOO_FLOWER -> "Bamboo Flower"
                null -> "Flower"
            }
            Suit.SEASON -> when (seasonType) {
                SeasonType.SPRING -> "Spring Season"
                SeasonType.SUMMER -> "Summer Season"
                SeasonType.AUTUMN -> "Autumn Season"
                SeasonType.WINTER -> "Winter Season"
                null -> "Season"
            }
        }

    override fun toString(): String = displayName

    override fun compareTo(other: MahjongTile): Int {
        if (this.suit != other.suit) return this.suit.ordinal.compareTo(other.suit.ordinal)
        if (this.value != other.value) return this.value.compareTo(other.value)
        return this.id.compareTo(other.id)
    }
}

/**
 * Extension helper to look up a tile's screen position using its ID.
 */
fun MahjongTile.getScreenPosition(positionsMap: Map<Int, IntOffset>): IntOffset? {
    return positionsMap[this.id]
}

fun MahjongTile.getTilePosition(positionsMap: Map<Int, IntOffset>):IntOffset? {
    return positionsMap[this.id]
}

/**
 * Extension helper to look up a screen position directly by tile ID.
 */
fun Map<Int, IntOffset>.getTilePosition(tileId: Int?): IntOffset? {
    return tileId?.let { this[it] }
}
