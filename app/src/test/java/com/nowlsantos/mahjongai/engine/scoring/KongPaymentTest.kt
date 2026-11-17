package com.nowlsantos.mahjongai.engine.scoring

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KongPaymentTest {

    @Test
    fun testConcealedKongPayment() {
        val deltas = KongPaymentRules.calculateKongPayment(
            declarerIndex = 0,
            isConcealed = true,
            isPromoted = false
        )

        assertEquals(1200, deltas[0])
        assertEquals(-400, deltas[1])
        assertEquals(-400, deltas[2])
        assertEquals(-400, deltas[3])
    }

    @Test
    fun testPromotedKongPayment() {
        val deltas = KongPaymentRules.calculateKongPayment(
            declarerIndex = 1,
            isConcealed = false,
            isPromoted = true
        )

        assertEquals(-200, deltas[0])
        assertEquals(600, deltas[1])
        assertEquals(-200, deltas[2])
        assertEquals(-200, deltas[3])
    }

    @Test
    fun testMeldedKongPaymentFromDiscard() {
        val deltas = KongPaymentRules.calculateKongPayment(
            declarerIndex = 2,
            isConcealed = false,
            isPromoted = false,
            discarderIndex = 0
        )

        assertEquals(-600, deltas[0])
        assertEquals(0, deltas[1])
        assertEquals(600, deltas[2])
        assertEquals(0, deltas[3])
    }

    @Test
    fun testFanCalculatorWinOnReplacementTile() {
        val tile = MahjongTile(id = 1, suit = Suit.DOT, value = 1)
        val result = FanCalculator.calculateFan(
            hand = listOf(tile, tile), // Eyes
            revealedMelds = listOf(
                Meld(MeldType.CHOW, listOf(MahjongTile(2, Suit.DOT, 1), MahjongTile(3, Suit.DOT, 2), MahjongTile(4, Suit.DOT, 3))),
                Meld(MeldType.CHOW, listOf(MahjongTile(5, Suit.DOT, 4), MahjongTile(6, Suit.DOT, 5), MahjongTile(7, Suit.DOT, 6))),
                Meld(MeldType.CHOW, listOf(MahjongTile(8, Suit.DOT, 7), MahjongTile(9, Suit.DOT, 8), MahjongTile(10, Suit.DOT, 9))),
                Meld(MeldType.KONG, listOf(MahjongTile(11, Suit.BAMBOO, 1), MahjongTile(12, Suit.BAMBOO, 1), MahjongTile(13, Suit.BAMBOO, 1), MahjongTile(14, Suit.BAMBOO, 1)))
            ),
            bonusTiles = emptyList(),
            isSelfDraw = true,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            isWinOnReplacement = true
        )

        assertTrue(result.patternNames.contains("Win on Replacement Tile (槓上開花)"))
        assertTrue(result.patternNames.contains("Self-Draw (自摸)"))
    }
}
