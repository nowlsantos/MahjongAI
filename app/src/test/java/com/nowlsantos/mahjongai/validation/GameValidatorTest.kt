package com.nowlsantos.mahjongai.validation

import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.model.Suit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameValidatorTest {

    private lateinit var validator: GameValidator

    @Before
    fun setUp() {
        validator = GameValidator()
    }

    @Test
    fun testValidateDiscard_success() {
        val normalTile = MahjongTile(1, Suit.BAMBOO, 1)
        val dummyTiles = (2..14).map { MahjongTile(it, Suit.DOT, it % 9 + 1) }
        val hand = listOf(normalTile) + dummyTiles // 14 tiles

        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = hand
        )
        val state = GameState(
            gamePhase = GamePhase.PLAYING,
            activePlayerIndex = 0,
            players = mapOf(0 to player)
        )

        val result = validator.validateDiscard(
            gameState = state,
            playerIndex = 0,
            discardedTile = normalTile,
            selectedTiles = listOf(normalTile)
        )

        assertTrue(result.isSuccess())
    }

    @Test
    fun testValidateDiscard_bonusTileFails() {
        val bonusTile = MahjongTile(1, Suit.FLOWER, 1, flowerType = com.nowlsantos.mahjongai.model.FlowerType.PLUM)
        val dummyTiles = (2..14).map { MahjongTile(it, Suit.DOT, it % 9 + 1) }
        val hand = listOf(bonusTile) + dummyTiles

        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = hand
        )
        val state = GameState(
            gamePhase = GamePhase.PLAYING,
            activePlayerIndex = 0,
            players = mapOf(0 to player)
        )

        val result = validator.validateDiscard(
            gameState = state,
            playerIndex = 0,
            discardedTile = bonusTile,
            selectedTiles = listOf(bonusTile)
        )

        assertTrue(result.isError())
    }

    @Test
    fun testValidateDiscard_notHolding14TilesFails() {
        val normalTile = MahjongTile(1, Suit.BAMBOO, 1)
        val dummyTiles = (2..13).map { MahjongTile(it, Suit.DOT, it % 9 + 1) }
        val hand = listOf(normalTile) + dummyTiles // 13 tiles

        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = hand
        )
        val state = GameState(
            gamePhase = GamePhase.PLAYING,
            activePlayerIndex = 0,
            players = mapOf(0 to player)
        )

        val result = validator.validateDiscard(
            gameState = state,
            playerIndex = 0,
            discardedTile = normalTile,
            selectedTiles = listOf(normalTile)
        )

        assertTrue(result.isError())
    }

    @Test
    fun testValidateDraw_success() {
        val dummyTiles = (1..13).map { MahjongTile(it, Suit.DOT, it % 9 + 1) } // 13 tiles
        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = dummyTiles
        )
        val state = GameState(
            gamePhase = GamePhase.PLAYING,
            activePlayerIndex = 0,
            players = mapOf(0 to player)
        )

        val result = validator.validateDraw(state, 0)
        assertTrue(result.isSuccess())
    }

    @Test
    fun testValidateDraw_alreadyHas14TilesFails() {
        val dummyTiles = (1..14).map { MahjongTile(it, Suit.DOT, it % 9 + 1) } // 14 tiles
        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = dummyTiles
        )
        val state = GameState(
            gamePhase = GamePhase.PLAYING,
            activePlayerIndex = 0,
            players = mapOf(0 to player)
        )

        val result = validator.validateDraw(state, 0)
        assertTrue(result.isError())
    }

    @Test
    fun testValidateClaim_passAlwaysSucceeds() {
        val state = GameState(gamePhase = GamePhase.CLAIM_INTERCEPTION)
        val result = validator.validateClaim(state, ClaimIntent(0, ClaimType.PASS))
        assertTrue(result.isSuccess())
    }

    @Test
    fun testValidateBonusMeld_onlyBonusAllowed() {
        val normalTile = MahjongTile(1, Suit.DOT, 1)
        val bonusTile = MahjongTile(2, Suit.FLOWER, 1, flowerType = com.nowlsantos.mahjongai.model.FlowerType.PLUM)
        val player = PlayerState(
            playerId = 0,
            playerName = "RedDragon",
            isAi = false,
            seat = MahjongSeat.BOTTOM,
            hand = listOf(normalTile, bonusTile)
        )
        val state = GameState(players = mapOf(0 to player))

        assertTrue(validator.validateBonusMeld(state, 0, bonusTile).isSuccess())
        assertTrue(validator.validateBonusMeld(state, 0, normalTile).isError())
    }
}
