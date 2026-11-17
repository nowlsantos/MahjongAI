package com.nowlsantos.mahjongai.presentation

import kotlin.random.Random

data class DiceResult(
    val die1: Int,
    val die2: Int,
    val sum: Int
)

object DiceRoller {
    /**
     * Rolls two standard 6-sided dice (1..6) for determining wall breaking and dealing order.
     */
    fun rollTwoDice(): DiceResult {
        //val d1 = 4
        //val d2 = 4
        val d1 = Random.nextInt(1, 7)
        val d2 = Random.nextInt(1, 7)
        return DiceResult(d1, d2, d1 + d2)
    }
}
