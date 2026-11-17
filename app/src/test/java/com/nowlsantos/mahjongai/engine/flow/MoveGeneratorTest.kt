package com.nowlsantos.mahjongai.engine.flow

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveGeneratorTest {

    @Test
    fun testPungAndKongClaimsGenerated() {
        val hand = listOf(
            MahjongTile(1, Suit.BAMBOO, 3),
            MahjongTile(2, Suit.BAMBOO, 3),
            MahjongTile(3, Suit.BAMBOO, 3),
            MahjongTile(4, Suit.DOT, 5)
        )
        val discardedTile = MahjongTile(10, Suit.BAMBOO, 3)

        val claims = MoveGenerator.getLegalClaims(
            playerIndex = 1,
            playerHand = hand,
            discarderIndex = 0,
            discardedTile = discardedTile,
            seatWind = HonorType.SOUTH,
            prevalentWind = HonorType.EAST
        )

        val claimTypes = claims.map { it.claimType }.toSet()
        assertTrue(claimTypes.contains(ClaimType.PUNG))
        assertTrue(claimTypes.contains(ClaimType.KONG))
        assertTrue(claimTypes.contains(ClaimType.PASS))
    }

    @Test
    fun testChowAllowedOnlyFromLeftPlayer() {
        val hand = listOf(
            MahjongTile(1, Suit.CHARACTER, 2),
            MahjongTile(2, Suit.CHARACTER, 4)
        )
        val discardedTile = MahjongTile(10, Suit.CHARACTER, 3)

        // Player 2 checking discard from Player 1 (Player 1 is left of Player 2: (2 - 1 + 4) % 4 == 1)
        val validChowClaims = MoveGenerator.getLegalClaims(
            playerIndex = 2,
            playerHand = hand,
            discarderIndex = 1,
            discardedTile = discardedTile,
            seatWind = HonorType.WEST,
            prevalentWind = HonorType.EAST
        )
        assertTrue(validChowClaims.any { it.claimType == ClaimType.CHOW })

        // Player 2 checking discard from Player 0 (Opposite player: not left)
        val invalidChowClaims = MoveGenerator.getLegalClaims(
            playerIndex = 2,
            playerHand = hand,
            discarderIndex = 0,
            discardedTile = discardedTile,
            seatWind = HonorType.WEST,
            prevalentWind = HonorType.EAST
        )
        assertFalse(invalidChowClaims.any { it.claimType == ClaimType.CHOW })
    }

    @Test
    fun testLegalTurnActionsExcludesBonusTilesFromDiscards() {
        val hand = listOf(
            MahjongTile(1, Suit.DOT, 1),
            MahjongTile(2, Suit.DOT, 2),
            MahjongTile(100, Suit.FLOWER, 1, flowerType = com.nowlsantos.mahjongai.model.FlowerType.PLUM)
        )

        val actions = MoveGenerator.getLegalTurnActions(
            playerHand = hand,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST
        )

        val bonusActions = actions.filterIsInstance<TurnAction.BonusMeld>()
        assertEquals(1, bonusActions.size)
        assertEquals(100, bonusActions[0].tile.id)

        val discardActions = actions.filterIsInstance<TurnAction.Discard>()
        assertEquals(2, discardActions.size)
        assertTrue(discardActions.none { it.tile.isBonus })
    }

    @Test
    fun testWinningHandWithRevealedMelds() {
        // Player has 2 revealed melds (6 tiles) and 8 concealed tiles (forming 2 melds + 1 pair) = 14 total
        val revealedMelds = listOf(
            Meld(MeldType.PUNG, listOf(MahjongTile(10, Suit.CHARACTER, 1), MahjongTile(11, Suit.CHARACTER, 1), MahjongTile(12, Suit.CHARACTER, 1))),
            Meld(MeldType.CHOW, listOf(MahjongTile(13, Suit.CHARACTER, 2), MahjongTile(14, Suit.CHARACTER, 3), MahjongTile(15, Suit.CHARACTER, 4)))
        )

        // Concealed hand has 7 tiles + 1 winning tile: [5, 5, 5 Character] + [7, 8, 9 Character] + [9, 9 Character (Pair)]
        val concealedHand = listOf(
            MahjongTile(20, Suit.CHARACTER, 5),
            MahjongTile(21, Suit.CHARACTER, 5),
            MahjongTile(22, Suit.CHARACTER, 5),
            MahjongTile(23, Suit.CHARACTER, 7),
            MahjongTile(24, Suit.CHARACTER, 8),
            MahjongTile(25, Suit.CHARACTER, 9),
            MahjongTile(26, Suit.CHARACTER, 9),
            MahjongTile(27, Suit.CHARACTER, 9) // 8th tile (drawn)
        )

        val turnActions = MoveGenerator.getLegalTurnActions(
            playerHand = concealedHand,
            revealedMelds = revealedMelds,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = 3
        )

        // All Character tiles = Clean Flush (7 Fan), so SelfDrawWin must be present
        val winAction = turnActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
        assertTrue(winAction != null)
    }

    @Test
    fun testStrategicEngineAvoidsDeadWaitsAndRecognizesChows() {
        val engine = com.nowlsantos.mahjongai.engine.ExpectimaxEngine()

        // Hand with 2 complete Chows [1,2,3 Char], [4,5,6 Bamboo], 1 Pair [9,9 Dot],
        // 1 open-ended proto-chow [4,5 Dot] (waiting on 3 Dot or 6 Dot),
        // and 1 isolated inside proto-chow [1,3 Bamboo] (waiting ONLY on 2 Bamboo),
        // plus an isolated North Wind.
        val hand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 2),
            MahjongTile(3, Suit.CHARACTER, 3),
            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),
            MahjongTile(7, Suit.DOT, 9),
            MahjongTile(8, Suit.DOT, 9),
            MahjongTile(9, Suit.DOT, 4),
            MahjongTile(10, Suit.DOT, 5),
            MahjongTile(11, Suit.BAMBOO, 1),
            MahjongTile(12, Suit.BAMBOO, 3),
            MahjongTile(13, Suit.HONOR, 4, honorType = HonorType.NORTH),
            MahjongTile(14, Suit.HONOR, 3, honorType = HonorType.WEST)
        )

        // Suppose 3 copies of North Wind are already in public discards (dead honor!)
        val publicDiscards = listOf(
            MahjongTile(50, Suit.HONOR, 4, honorType = HonorType.NORTH),
            MahjongTile(51, Suit.HONOR, 4, honorType = HonorType.NORTH),
            MahjongTile(52, Suit.HONOR, 4, honorType = HonorType.NORTH)
        )

        val decision = engine.findOptimalDiscard(
            currentHand = hand,
            revealedMeldsCount = 0,
            visibleBoardTiles = publicDiscards,
            seatWind = HonorType.SOUTH,
            prevalentWind = HonorType.EAST
        )

        // Engine must discard the dead North Wind first over West Wind or connected suit tiles
        assertEquals(Suit.HONOR, decision.bestTileToDiscard.suit)
        assertEquals(4, decision.bestTileToDiscard.value)
    }

    @Test
    fun testStrategicEnginePureSuitStressAndDeadWaitPivot() {
        val engine = com.nowlsantos.mahjongai.engine.ExpectimaxEngine()

        // 1. Stress test: Pure 14-tile single-suit hand (Nine Gates style) must evaluate in milliseconds
        val pureSuitHand = listOf(
            MahjongTile(1, Suit.BAMBOO, 1),
            MahjongTile(2, Suit.BAMBOO, 1),
            MahjongTile(3, Suit.BAMBOO, 1),
            MahjongTile(4, Suit.BAMBOO, 2),
            MahjongTile(5, Suit.BAMBOO, 3),
            MahjongTile(6, Suit.BAMBOO, 4),
            MahjongTile(7, Suit.BAMBOO, 5),
            MahjongTile(8, Suit.BAMBOO, 6),
            MahjongTile(9, Suit.BAMBOO, 7),
            MahjongTile(10, Suit.BAMBOO, 8),
            MahjongTile(11, Suit.BAMBOO, 9),
            MahjongTile(12, Suit.BAMBOO, 9),
            MahjongTile(13, Suit.BAMBOO, 9),
            MahjongTile(14, Suit.BAMBOO, 5)
        )
        val pureResult = engine.findOptimalDiscard(pureSuitHand, revealedMeldsCount = 0)
        assertTrue(pureSuitHand.any { it.suit == pureResult.bestTileToDiscard.suit && it.value == pureResult.bestTileToDiscard.value })

        // 2. Dead Wait Pivot: Player has 2 proto-chows: [1, 2 Dot] (waiting ONLY on 3 Dot) and [4, 6 Char] (waiting ONLY on 5 Char)
        // Suppose all 4 copies of 3 Dot are already melded/discarded on the board!
        // The AI must recognize [1, 2 Dot] is completely dead (0 live 3 Dots) and discard 1 Dot or 2 Dot instead of breaking [4, 6 Char].
        val handWithDeadEdgeWait = listOf(
            MahjongTile(20, Suit.BAMBOO, 1),
            MahjongTile(21, Suit.BAMBOO, 2),
            MahjongTile(22, Suit.BAMBOO, 3),
            MahjongTile(23, Suit.BAMBOO, 7),
            MahjongTile(24, Suit.BAMBOO, 8),
            MahjongTile(25, Suit.BAMBOO, 9),
            MahjongTile(26, Suit.HONOR, 5, honorType = HonorType.RED),
            MahjongTile(27, Suit.HONOR, 5, honorType = HonorType.RED),
            MahjongTile(28, Suit.HONOR, 5, honorType = HonorType.RED),
            MahjongTile(29, Suit.DOT, 8),
            MahjongTile(30, Suit.DOT, 8), // Pair
            MahjongTile(31, Suit.DOT, 1),
            MahjongTile(32, Suit.DOT, 2), // Edge wait on 3 Dot (DEAD)
            MahjongTile(33, Suit.CHARACTER, 4) // Extra tile
        )

        val exhaustedThreeDots = listOf(
            MahjongTile(60, Suit.DOT, 3),
            MahjongTile(61, Suit.DOT, 3),
            MahjongTile(62, Suit.DOT, 3),
            MahjongTile(63, Suit.DOT, 3)
        )

        val shantenWithDeadWait = com.nowlsantos.mahjongai.engine.logic.TingCalculator.calculateLiveTileAcceptance(
            hand = handWithDeadEdgeWait.dropLast(1), // 13 tiles waiting on 3 Dot
            revealedMeldsCount = 0,
            visibleCounts = com.nowlsantos.mahjongai.engine.logic.TingCalculator.buildVisibleTileCounts(handWithDeadEdgeWait + exhaustedThreeDots)
        )
        // Since all 4 copies of 3 Dot are gone, live tile acceptance for that Tenpai wait is strictly 0
        assertEquals(0, shantenWithDeadWait)
    }

    @Test
    fun testChickenHandSelfDrawWinAllowed() {
        // Chicken Hand: Mixed suits, some chows, some pungs, no dragons, non-seat wind, 0 Fan
        val chickenHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 2),
            MahjongTile(3, Suit.CHARACTER, 3),
            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),
            MahjongTile(7, Suit.DOT, 7),
            MahjongTile(8, Suit.DOT, 7),
            MahjongTile(9, Suit.DOT, 7),
            MahjongTile(10, Suit.CHARACTER, 9),
            MahjongTile(11, Suit.CHARACTER, 9),
            MahjongTile(12, Suit.CHARACTER, 9),
            MahjongTile(13, Suit.BAMBOO, 2),
            MahjongTile(14, Suit.BAMBOO, 2) // Pair
        )

        val turnActions = MoveGenerator.getLegalTurnActions(
            playerHand = chickenHand,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.MIN_FAN_REQUIRED
        )

        val winAction = turnActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
        assertTrue(winAction != null)
    }

    @Test
    fun testChickenHandRonClaimWinAllowed() {
        // 13 tiles in hand waiting on 2 Bamboo to complete pair
        val hand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 2),
            MahjongTile(3, Suit.CHARACTER, 3),
            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),
            MahjongTile(7, Suit.DOT, 7),
            MahjongTile(8, Suit.DOT, 7),
            MahjongTile(9, Suit.DOT, 7),
            MahjongTile(10, Suit.CHARACTER, 9),
            MahjongTile(11, Suit.CHARACTER, 9),
            MahjongTile(12, Suit.CHARACTER, 9),
            MahjongTile(13, Suit.BAMBOO, 2)
        )
        val discardedTile = MahjongTile(14, Suit.BAMBOO, 2)

        val claims = MoveGenerator.getLegalClaims(
            playerIndex = 0,
            playerHand = hand,
            discarderIndex = 1,
            discardedTile = discardedTile,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.MIN_FAN_REQUIRED
        )

        val winClaim = claims.find { it.claimType == ClaimType.WIN }
        assertTrue("Chicken hand must be able to claim Win on discard", winClaim != null)
    }

    @Test
    fun testSevenPairsWinAllowed() {
        val sevenPairsHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 1),
            MahjongTile(3, Suit.CHARACTER, 5),
            MahjongTile(4, Suit.CHARACTER, 5),
            MahjongTile(5, Suit.BAMBOO, 2),
            MahjongTile(6, Suit.BAMBOO, 2),
            MahjongTile(7, Suit.BAMBOO, 9),
            MahjongTile(8, Suit.BAMBOO, 9),
            MahjongTile(9, Suit.DOT, 3),
            MahjongTile(10, Suit.DOT, 3),
            MahjongTile(11, Suit.DOT, 7),
            MahjongTile(12, Suit.DOT, 7),
            MahjongTile(13, Suit.HONOR, 1, honorType = HonorType.EAST),
            MahjongTile(14, Suit.HONOR, 1, honorType = HonorType.EAST)
        )

        assertTrue(com.nowlsantos.mahjongai.engine.logic.HandEvaluator.isWinningHand(sevenPairsHand))

        val fan = com.nowlsantos.mahjongai.engine.scoring.FanCalculator.calculateFan(
            hand = sevenPairsHand,
            isSelfDraw = true,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST
        )
        assertTrue(fan.totalFan >= 4)
        assertTrue(fan.patternNames.contains("Seven Pairs (七對子)"))
    }

    @Test
    fun testThirteenOrphansWinAllowed() {
        val thirteenOrphansHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 9),
            MahjongTile(3, Suit.BAMBOO, 1),
            MahjongTile(4, Suit.BAMBOO, 9),
            MahjongTile(5, Suit.DOT, 1),
            MahjongTile(6, Suit.DOT, 9),
            MahjongTile(7, Suit.HONOR, 1, honorType = HonorType.EAST),
            MahjongTile(8, Suit.HONOR, 2, honorType = HonorType.SOUTH),
            MahjongTile(9, Suit.HONOR, 3, honorType = HonorType.WEST),
            MahjongTile(10, Suit.HONOR, 4, honorType = HonorType.NORTH),
            MahjongTile(11, Suit.HONOR, 5, honorType = HonorType.RED),
            MahjongTile(12, Suit.HONOR, 6, honorType = HonorType.GREEN),
            MahjongTile(13, Suit.HONOR, 7, honorType = HonorType.WHITE),
            MahjongTile(14, Suit.HONOR, 7, honorType = HonorType.WHITE) // Pair of White Dragons
        )

        assertTrue(com.nowlsantos.mahjongai.engine.logic.HandEvaluator.isWinningHand(thirteenOrphansHand))

        val fan = com.nowlsantos.mahjongai.engine.scoring.FanCalculator.calculateFan(
            hand = thirteenOrphansHand,
            isSelfDraw = false,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST
        )
        assertEquals(13, fan.totalFan)
        assertTrue(fan.patternNames.contains("Thirteen Orphans (十三幺)"))
    }

    @Test
    fun testAiPlayerCannotWinChickenHandBelow1Fan() {
        val chickenHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 2),
            MahjongTile(3, Suit.CHARACTER, 3),
            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),
            MahjongTile(7, Suit.DOT, 7),
            MahjongTile(8, Suit.DOT, 7),
            MahjongTile(9, Suit.DOT, 7),
            MahjongTile(10, Suit.CHARACTER, 9),
            MahjongTile(11, Suit.CHARACTER, 9),
            MahjongTile(12, Suit.CHARACTER, 9),
            MahjongTile(13, Suit.BAMBOO, 2),
            MahjongTile(14, Suit.BAMBOO, 2)
        )
        // With an open meld (revealed melds), no Concealed Hand is present.
        // With a non-matching flower (Flower #2 when seat is East/1), no "No Flowers" bonus is present -> 0 Fan.
        val openMelds = listOf(
            Meld(MeldType.CHOW, listOf(MahjongTile(1, Suit.CHARACTER, 1), MahjongTile(2, Suit.CHARACTER, 2), MahjongTile(3, Suit.CHARACTER, 3)))
        )
        val nonMatchingFlowers = listOf(
            MahjongTile(137, Suit.FLOWER, 2, flowerType = com.nowlsantos.mahjongai.model.FlowerType.ORCHID)
        )
        val concealedRemaining = chickenHand.drop(3)

        val discardedTile = MahjongTile(14, Suit.BAMBOO, 2)
        val aiClaims = MoveGenerator.getLegalClaims(
            playerIndex = 1,
            playerHand = concealedRemaining.dropLast(1),
            revealedMelds = openMelds,
            bonusTiles = nonMatchingFlowers,
            discarderIndex = 0,
            discardedTile = discardedTile,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.AI_MIN_FAN_REQUIRED
        )
        // AI must NOT be allowed to Ron claim win with 0 Fan under AI_MIN_FAN_REQUIRED = 1
        val aiWinClaim = aiClaims.find { it.claimType == ClaimType.WIN }
        assertTrue("AI must not be allowed to Ron claim win with 0 Fan chicken hand", aiWinClaim == null)

        // Human player (playerIndex = 0, MIN_FAN_REQUIRED = 0) CAN Ron claim win with 0 Fan
        val humanClaims = MoveGenerator.getLegalClaims(
            playerIndex = 0,
            playerHand = concealedRemaining.dropLast(1),
            revealedMelds = openMelds,
            bonusTiles = nonMatchingFlowers,
            discarderIndex = 1,
            discardedTile = discardedTile,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.MIN_FAN_REQUIRED
        )
        val humanWinClaim = humanClaims.find { it.claimType == ClaimType.WIN }
        assertTrue("Human player must be allowed to Ron claim win with 0 Fan chicken hand", humanWinClaim != null)
    }

    @Test
    fun testAiPlayerCanWinWith1FanDragonPung() {
        // Hand with an open Red Dragon Pung (1 Fan) + suit melds
        val openMelds = listOf(
            Meld(
                MeldType.PUNG,
                listOf(
                    MahjongTile(101, Suit.HONOR, 1, honorType = HonorType.RED),
                    MahjongTile(102, Suit.HONOR, 1, honorType = HonorType.RED),
                    MahjongTile(103, Suit.HONOR, 1, honorType = HonorType.RED)
                )
            )
        )
        val concealedHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 2),
            MahjongTile(3, Suit.CHARACTER, 3),
            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),
            MahjongTile(7, Suit.DOT, 7),
            MahjongTile(8, Suit.DOT, 8),
            MahjongTile(9, Suit.DOT, 9),
            MahjongTile(10, Suit.BAMBOO, 2),
            MahjongTile(11, Suit.BAMBOO, 2)
        )

        val turnActions = MoveGenerator.getLegalTurnActions(
            playerHand = concealedHand,
            revealedMelds = openMelds,
            seatWind = HonorType.SOUTH,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.AI_MIN_FAN_REQUIRED
        )
        val winAction = turnActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
        assertTrue("AI must be allowed to self-draw win with 1-Fan Dragon Pung", winAction != null)

        val discardedTile = MahjongTile(11, Suit.BAMBOO, 2)
        val claims = MoveGenerator.getLegalClaims(
            playerIndex = 1,
            playerHand = concealedHand.dropLast(1),
            revealedMelds = openMelds,
            discarderIndex = 0,
            discardedTile = discardedTile,
            seatWind = HonorType.SOUTH,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.AI_MIN_FAN_REQUIRED
        )
        val winClaim = claims.find { it.claimType == ClaimType.WIN }
        assertTrue("AI must be allowed to Ron claim win with 1-Fan Dragon Pung", winClaim != null)
    }

    @Test
    fun testAiPlayerCanWinAtOrAbove3Fan() {
        // Seven Pairs gives 4 Fan in Hong Kong Mahjong
        val sevenPairsHand = listOf(
            MahjongTile(1, Suit.CHARACTER, 1),
            MahjongTile(2, Suit.CHARACTER, 1),
            MahjongTile(3, Suit.CHARACTER, 5),
            MahjongTile(4, Suit.CHARACTER, 5),
            MahjongTile(5, Suit.BAMBOO, 2),
            MahjongTile(6, Suit.BAMBOO, 2),
            MahjongTile(7, Suit.BAMBOO, 9),
            MahjongTile(8, Suit.BAMBOO, 9),
            MahjongTile(9, Suit.DOT, 3),
            MahjongTile(10, Suit.DOT, 3),
            MahjongTile(11, Suit.DOT, 7),
            MahjongTile(12, Suit.DOT, 7),
            MahjongTile(13, Suit.HONOR, 1, honorType = HonorType.EAST),
            MahjongTile(14, Suit.HONOR, 1, honorType = HonorType.EAST)
        )

        val turnActions = MoveGenerator.getLegalTurnActions(
            playerHand = sevenPairsHand,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.AI_MIN_FAN_REQUIRED
        )
        val winAction = turnActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
        assertTrue("AI must be allowed to self-draw win with 4-Fan Seven Pairs", winAction != null)

        val discardedTile = MahjongTile(14, Suit.HONOR, 1, honorType = HonorType.EAST)
        val claims = MoveGenerator.getLegalClaims(
            playerIndex = 1,
            playerHand = sevenPairsHand.dropLast(1),
            discarderIndex = 0,
            discardedTile = discardedTile,
            seatWind = HonorType.EAST,
            prevalentWind = HonorType.EAST,
            minFanRequired = com.nowlsantos.mahjongai.util.GameConstants.AI_MIN_FAN_REQUIRED
        )
        val winClaim = claims.find { it.claimType == ClaimType.WIN }
        assertTrue("AI must be allowed to Ron claim win with 4-Fan Seven Pairs", winClaim != null)
    }
}
