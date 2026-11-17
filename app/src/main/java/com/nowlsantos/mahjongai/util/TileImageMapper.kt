package com.nowlsantos.mahjongai.util

import com.nowlsantos.mahjongai.R
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.FlowerType
import com.nowlsantos.mahjongai.model.SeasonType

enum class TileOrientation {
    DOWN, LEFT, UP, RIGHT
}

object TileImageMapper {

    /**
     * Maps a MahjongTile and orientation directly to compile-time R.drawable resource IDs.
     */
    fun getTileDrawableRes(tile: MahjongTile, orientation: TileOrientation = TileOrientation.DOWN): Int {
        return when (tile.suit) {
            Suit.CHARACTER -> when (tile.value) {
                1 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_1_character_down
                    TileOrientation.LEFT -> R.drawable.tile_1_character_left
                    TileOrientation.UP -> R.drawable.tile_1_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_1_character_right
                }
                2 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_2_character_down
                    TileOrientation.LEFT -> R.drawable.tile_2_character_left
                    TileOrientation.UP -> R.drawable.tile_2_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_2_character_right
                }
                3 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_3_character_down
                    TileOrientation.LEFT -> R.drawable.tile_3_character_left
                    TileOrientation.UP -> R.drawable.tile_3_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_3_character_right
                }
                4 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_4_character_down
                    TileOrientation.LEFT -> R.drawable.tile_4_character_left
                    TileOrientation.UP -> R.drawable.tile_4_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_4_character_right
                }
                5 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_5_character_down
                    TileOrientation.LEFT -> R.drawable.tile_5_character_left
                    TileOrientation.UP -> R.drawable.tile_5_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_5_character_right
                }
                6 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_6_character_down
                    TileOrientation.LEFT -> R.drawable.tile_6_character_left
                    TileOrientation.UP -> R.drawable.tile_6_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_6_character_right
                }
                7 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_7_character_down
                    TileOrientation.LEFT -> R.drawable.tile_7_character_left
                    TileOrientation.UP -> R.drawable.tile_7_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_7_character_right
                }
                8 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_8_character_down
                    TileOrientation.LEFT -> R.drawable.tile_8_character_left
                    TileOrientation.UP -> R.drawable.tile_8_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_8_character_right
                }
                else -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_9_character_down
                    TileOrientation.LEFT -> R.drawable.tile_9_character_left
                    TileOrientation.UP -> R.drawable.tile_9_character_up
                    TileOrientation.RIGHT -> R.drawable.tile_9_character_right
                }
            }
            Suit.DOT -> when (tile.value) {
                1 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_1_dot_down
                    TileOrientation.LEFT -> R.drawable.tile_1_dot_left
                    TileOrientation.UP -> R.drawable.tile_1_dot_up
                    TileOrientation.RIGHT -> R.drawable.tile_1_dot_right
                }
                9 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_9_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_9_ball_left
                    TileOrientation.UP -> R.drawable.tile_9_dot_up
                    TileOrientation.RIGHT -> R.drawable.tile_9_ball_right
                }
                2 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_2_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_2_ball_left
                    TileOrientation.UP -> R.drawable.tile_2_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_2_ball_right
                }
                3 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_3_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_3_ball_left
                    TileOrientation.UP -> R.drawable.tile_3_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_3_ball_right
                }
                4 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_4_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_4_ball_left
                    TileOrientation.UP -> R.drawable.tile_4_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_4_ball_right
                }
                5 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_5_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_5_ball_left
                    TileOrientation.UP -> R.drawable.tile_5_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_5_ball_right
                }
                6 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_6_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_6_ball_left
                    TileOrientation.UP -> R.drawable.tile_6_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_6_ball_right
                }
                7 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_7_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_7_ball_left
                    TileOrientation.UP -> R.drawable.tile_7_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_7_ball_right
                }
                else -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_8_ball_down
                    TileOrientation.LEFT -> R.drawable.tile_8_ball_left
                    TileOrientation.UP -> R.drawable.tile_8_ball_up
                    TileOrientation.RIGHT -> R.drawable.tile_8_ball_right
                }
            }
            Suit.BAMBOO -> when (tile.value) {
                1 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_1_stick_down
                    TileOrientation.LEFT -> R.drawable.tile_1_stick_left
                    TileOrientation.UP -> R.drawable.tile_1_stick_up
                    TileOrientation.RIGHT -> R.drawable.tile_1_stick_right
                }
                2 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_2_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_2_sticks_left
                    TileOrientation.UP -> R.drawable.tile_2_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_2_sticks_right
                }
                3 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_3_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_3_sticks_left
                    TileOrientation.UP -> R.drawable.tile_3_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_3_sticks_right
                }
                4 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_4_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_4_sticks_left
                    TileOrientation.UP -> R.drawable.tile_4_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_4_sticks_right
                }
                5 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_5_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_5_sticks_left
                    TileOrientation.UP -> R.drawable.tile_5_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_5_sticks_right
                }
                6 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_6_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_6_sticks_left
                    TileOrientation.UP -> R.drawable.tile_6_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_6_sticks_right
                }
                7 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_7_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_7_sticks_left
                    TileOrientation.UP -> R.drawable.tile_7_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_7_sticks_right
                }
                8 -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_8_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_8_sticks_left
                    TileOrientation.UP -> R.drawable.tile_8_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_8_sticks_right
                }
                else -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.tile_9_sticks_down
                    TileOrientation.LEFT -> R.drawable.tile_9_sticks_left
                    TileOrientation.UP -> R.drawable.tile_9_sticks_up
                    TileOrientation.RIGHT -> R.drawable.tile_9_sticks_right
                }
            }
            Suit.HONOR -> when (tile.honorType) {
                HonorType.EAST -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.east_down
                    TileOrientation.LEFT -> R.drawable.east_left
                    TileOrientation.UP -> R.drawable.east_up
                    TileOrientation.RIGHT -> R.drawable.east_right
                }
                HonorType.SOUTH -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.south_down
                    TileOrientation.LEFT -> R.drawable.south_left
                    TileOrientation.UP -> R.drawable.south_up
                    TileOrientation.RIGHT -> R.drawable.south_right
                }
                HonorType.WEST -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.west_down
                    TileOrientation.LEFT -> R.drawable.west_left
                    TileOrientation.UP -> R.drawable.west_up
                    TileOrientation.RIGHT -> R.drawable.west_right
                }
                HonorType.NORTH -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.north_down
                    TileOrientation.LEFT -> R.drawable.north_left
                    TileOrientation.UP -> R.drawable.north_up
                    TileOrientation.RIGHT -> R.drawable.north_right
                }
                HonorType.RED -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.dragon_red_down
                    TileOrientation.LEFT -> R.drawable.dragon_red_left
                    TileOrientation.UP -> R.drawable.dragon_red_up
                    TileOrientation.RIGHT -> R.drawable.dragon_red_right
                }
                HonorType.GREEN -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.dragon_green_down
                    TileOrientation.LEFT -> R.drawable.dragon_green_left
                    TileOrientation.UP -> R.drawable.dragon_green_up
                    TileOrientation.RIGHT -> R.drawable.dragon_green_right
                }
                HonorType.WHITE -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.dragon_white_down
                    TileOrientation.LEFT -> R.drawable.dragon_white_left
                    TileOrientation.UP -> R.drawable.dragon_white_up
                    TileOrientation.RIGHT -> R.drawable.dragon_white_right
                }
                null -> R.drawable.east_down
            }
            Suit.FLOWER -> when (tile.flowerType) {
                FlowerType.PLUM -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.plum_down
                    TileOrientation.LEFT -> R.drawable.plum_left
                    TileOrientation.UP -> R.drawable.plum_up
                    TileOrientation.RIGHT -> R.drawable.plum_right
                }
                FlowerType.ORCHID -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.orchid_down
                    TileOrientation.LEFT -> R.drawable.orchid_left
                    TileOrientation.UP -> R.drawable.orchid_up
                    TileOrientation.RIGHT -> R.drawable.orchid_right
                }
                FlowerType.CHRYSANTHEMUM -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.chrysanthemum_down
                    TileOrientation.LEFT -> R.drawable.chrysanthemum_left
                    TileOrientation.UP -> R.drawable.chrysanthemum_up
                    TileOrientation.RIGHT -> R.drawable.chrysanthemum_right
                }
                FlowerType.BAMBOO_FLOWER -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.flower_bamboo_down
                    TileOrientation.LEFT -> R.drawable.flower_bamboo_left
                    TileOrientation.UP -> R.drawable.flower_bamboo_up
                    TileOrientation.RIGHT -> R.drawable.flower_bamboo_right
                }
                null -> R.drawable.plum_down
            }
            Suit.SEASON -> when (tile.seasonType) {
                SeasonType.SPRING -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.spring_down
                    TileOrientation.LEFT -> R.drawable.spring_left
                    TileOrientation.UP -> R.drawable.spring_up
                    TileOrientation.RIGHT -> R.drawable.spring_right
                }
                SeasonType.SUMMER -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.summer_down
                    TileOrientation.LEFT -> R.drawable.summer_left
                    TileOrientation.UP -> R.drawable.summer_up
                    TileOrientation.RIGHT -> R.drawable.summer_right
                }
                SeasonType.AUTUMN -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.autumn_down
                    TileOrientation.LEFT -> R.drawable.autumn_left
                    TileOrientation.UP -> R.drawable.autumn_up
                    TileOrientation.RIGHT -> R.drawable.autumn_right
                }
                SeasonType.WINTER -> when (orientation) {
                    TileOrientation.DOWN -> R.drawable.winter_down
                    TileOrientation.LEFT -> R.drawable.winter_left
                    TileOrientation.UP -> R.drawable.winter_up
                    TileOrientation.RIGHT -> R.drawable.winter_right
                }
                null -> R.drawable.spring_down
            }
        }
    }

    fun getPlayerTileDrawable(seat: MahjongSeat): Int {
        return when (seat) {
            MahjongSeat.TOP -> R.drawable.horizontal_top_up
            MahjongSeat.LEFT -> R.drawable.player_left
            MahjongSeat.RIGHT -> R.drawable. player_right
            MahjongSeat.BOTTOM -> R.drawable.horizontal_wall
        }
    }
}
