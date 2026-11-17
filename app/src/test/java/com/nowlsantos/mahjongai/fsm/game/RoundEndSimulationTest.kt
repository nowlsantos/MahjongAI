package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.PlayerWall
import com.nowlsantos.mahjongai.model.RoundEndData
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.model.WallStack
import com.nowlsantos.mahjongai.util.GameConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundEndSimulationTest {

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun testSelfDrawWinFinalizesFanAndChipSettlement() = runBlocking {
        val roundEndState = RoundEndState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(GamePhase.ROUND_END to roundEndState)
        )

        // Setup Player 1 (AI) with a winning hand (Clean Flush / All One Suit = 7 Fan + SelfDraw = 8 Fan)
        val winningHand = listOf(
            MahjongTile(1, Suit.BAMBOO, 1),
            MahjongTile(2, Suit.BAMBOO, 2),
            MahjongTile(3, Suit.BAMBOO, 3),

            MahjongTile(4, Suit.BAMBOO, 4),
            MahjongTile(5, Suit.BAMBOO, 5),
            MahjongTile(6, Suit.BAMBOO, 6),

            MahjongTile(7, Suit.BAMBOO, 7),
            MahjongTile(8, Suit.BAMBOO, 8),
            MahjongTile(9, Suit.BAMBOO, 9),

            MahjongTile(10, Suit.BAMBOO, 1),
            MahjongTile(11, Suit.BAMBOO, 1),
            MahjongTile(12, Suit.BAMBOO, 1),

            MahjongTile(13, Suit.BAMBOO, 5),
            MahjongTile(14, Suit.BAMBOO, 5) // Drawn winning tile
        )

        val initialPlayers = GameState.initializePlayers(0).toMutableMap()
        initialPlayers[1] = initialPlayers[1]!!.copy(hand = winningHand)

        machine.updateState { state ->
            state.copy(
                gamePhase = GamePhase.ROUND_END,
                players = initialPlayers,
                dealerIndex = 0,
                roundEndData = RoundEndData(
                    winnerIndex = 1,
                    discarderIndex = null,
                    winningTile = winningHand.last(),
                    isSelfDraw = true,
                    isDraw = false
                )
            )
        }

        roundEndState.onEnter(machine)

        val finalizedData = machine.currentState.roundEndData
        assertNotNull(finalizedData)
        assertNotNull(finalizedData?.scoringResult)
        assertTrue(finalizedData!!.totalFan >= 8) // All One Suit (7) + Self-Draw (1) = 8+ Fan

        val basePoints = FanCalculator.calculatePoints(finalizedData.totalFan)
        assertEquals(12800, basePoints) // 10 Fan = 12,800 pts

        // Winner receives 3 * basePoints; each opponent pays basePoints
        val tx = finalizedData.chipTransactions
        assertEquals(3 * 12800, tx[1])
        assertEquals(-12800, tx[0])
        assertEquals(-12800, tx[2])
        assertEquals(-12800, tx[3])

        // Verify updated chip scores on PlayerState
        assertEquals(GameConstants.STARTING_POINTS + (3 * 12800), machine.currentState.players[1]!!.score)
        assertEquals(GameConstants.STARTING_POINTS - 12800, machine.currentState.players[0]!!.score)
    }

    @Test
    fun testRonWinShooterPaysAll() = runBlocking {
        val roundEndState = RoundEndState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(GamePhase.ROUND_END to roundEndState)
        )

        // Player 0 (Human) wins on Player 3's discard (Ron)
        // Hand: All Pungs (3 Fan)
        val winningHand = listOf(
            MahjongTile(1, Suit.BAMBOO, 1),
            MahjongTile(2, Suit.BAMBOO, 1),
            MahjongTile(3, Suit.BAMBOO, 1),

            MahjongTile(4, Suit.DOT, 3),
            MahjongTile(5, Suit.DOT, 3),
            MahjongTile(6, Suit.DOT, 3),

            MahjongTile(7, Suit.CHARACTER, 7),
            MahjongTile(8, Suit.CHARACTER, 7),
            MahjongTile(9, Suit.CHARACTER, 7),

            MahjongTile(10, Suit.CHARACTER, 9),
            MahjongTile(11, Suit.CHARACTER, 9),
            MahjongTile(12, Suit.CHARACTER, 9),

            MahjongTile(13, Suit.DOT, 8)
        )
        val claimedTile = MahjongTile(14, Suit.DOT, 8) // Completes the pair

        val initialPlayers = GameState.initializePlayers(0).toMutableMap()
        initialPlayers[0] = initialPlayers[0]!!.copy(hand = winningHand)

        machine.updateState { state ->
            state.copy(
                gamePhase = GamePhase.ROUND_END,
                players = initialPlayers,
                dealerIndex = 0,
                roundEndData = RoundEndData(
                    winnerIndex = 0,
                    discarderIndex = 3,
                    winningTile = claimedTile,
                    isSelfDraw = false,
                    isDraw = false
                )
            )
        }

        roundEndState.onEnter(machine)

        val finalizedData = machine.currentState.roundEndData
        assertNotNull(finalizedData)
        assertTrue(finalizedData!!.totalFan >= 3) // All Pungs (3 Fan)

        val basePts = FanCalculator.calculatePoints(finalizedData.totalFan)
        val tx = finalizedData.chipTransactions

        // Discarder (Player 3) pays all 3 * basePts to Player 0
        assertEquals(3 * basePts, tx[0])
        assertEquals(-3 * basePts, tx[3])
        assertEquals(0, tx[1])
        assertEquals(0, tx[2])

        assertEquals(GameConstants.STARTING_POINTS + 3 * basePts, machine.currentState.players[0]!!.score)
        assertEquals(GameConstants.STARTING_POINTS - 3 * basePts, machine.currentState.players[3]!!.score)
    }

    @Test
    fun testDealerRotationWhenNonDealerWins() = runBlocking {
        val roundEndState = RoundEndState()
        val setupState = SetupState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(
                GamePhase.ROUND_END to roundEndState,
                GamePhase.SETUP to setupState
            )
        )

        // Round 1 with Dealer 0 (Human)
        machine.updateState { state ->
            state.copy(
                roundNumber = 1,
                dealerIndex = 0,
                roundEndData = RoundEndData(
                    winnerIndex = 2, // AI Player 2 won
                    discarderIndex = 3,
                    winningTile = MahjongTile(1, Suit.DOT, 1),
                    isSelfDraw = false,
                    isDraw = false
                )
            )
        }

        // Advance to next round
        roundEndState.handleInput(machine, GameInput.StartNextRound)

        // Dealer should rotate from 0 to 1, round number becomes 2
        assertEquals(2, machine.currentState.roundNumber)
        assertEquals(1, machine.currentState.dealerIndex)
    }

    @Test
    fun testDealerStaysWhenDealerWins() = runBlocking {
        val roundEndState = RoundEndState()
        val setupState = SetupState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(
                GamePhase.ROUND_END to roundEndState,
                GamePhase.SETUP to setupState
            )
        )

        // Round 1 with Dealer 0 (Human) and Dealer won (Ren-zhuang)
        machine.updateState { state ->
            state.copy(
                roundNumber = 1,
                dealerIndex = 0,
                roundEndData = RoundEndData(
                    winnerIndex = 0, // Dealer won!
                    discarderIndex = null,
                    winningTile = MahjongTile(1, Suit.DOT, 1),
                    isSelfDraw = true,
                    isDraw = false
                )
            )
        }

        roundEndState.handleInput(machine, GameInput.StartNextRound)

        // Dealer stays as 0
        assertEquals(2, machine.currentState.roundNumber)
        assertEquals(0, machine.currentState.dealerIndex)
    }

    @Test
    fun testAiSelfDrawWithPrecomputedScoringResultSettlesChipsCorrectly() = runBlocking {
        val roundEndState = RoundEndState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(GamePhase.ROUND_END to roundEndState)
        )

        // Player 1 (NineGates - AI) achieved Self-Draw with 3 Fan (800 pts/payment)
        val initialPlayers = GameState.initializePlayers(0).toMutableMap()
        val precomputedResult = com.nowlsantos.mahjongai.engine.scoring.ScoringResult(
            totalFan = 3,
            patternNames = listOf("All Chows (平胡)", "Concealed Hand (門前清)", "Self-Draw (自摸)")
        )

        machine.updateState { state ->
            state.copy(
                gamePhase = GamePhase.ROUND_END,
                players = initialPlayers,
                dealerIndex = 0,
                roundEndData = RoundEndData(
                    winnerIndex = 1,
                    discarderIndex = null,
                    winningTile = MahjongTile(14, Suit.DOT, 4),
                    isSelfDraw = true,
                    isDraw = false,
                    scoringResult = precomputedResult
                )
            )
        }

        roundEndState.onEnter(machine)

        val finalizedData = machine.currentState.roundEndData
        assertNotNull(finalizedData)
        assertEquals(3, finalizedData!!.totalFan)

        val basePoints = FanCalculator.calculatePoints(3)
        assertEquals(800, basePoints)

        // Winner (Player 1) must receive 3 * 800 = 2400 pts; each opponent pays 800 pts
        val tx = finalizedData.chipTransactions
        assertEquals(2400, tx[1])
        assertEquals(-800, tx[0])
        assertEquals(-800, tx[2])
        assertEquals(-800, tx[3])

        // Verify updated chip scores on PlayerState
        assertEquals(GameConstants.STARTING_POINTS + 2400, machine.currentState.players[1]!!.score)
        assertEquals(GameConstants.STARTING_POINTS - 800, machine.currentState.players[0]!!.score)
        assertEquals(GameConstants.STARTING_POINTS - 800, machine.currentState.players[2]!!.score)
        assertEquals(GameConstants.STARTING_POINTS - 800, machine.currentState.players[3]!!.score)
    }

    private fun createDepletedWalls(): Map<Int, PlayerWall> {
        var tileId = 0
        return (0..3).associateWith { playerIndex ->
            val stacks = (0 until 18).map { stackIdx ->
                WallStack(
                    stackIndex = stackIdx,
                    topTile = MahjongTile(tileId++, Suit.DOT, (stackIdx % 9) + 1),
                    bottomTile = MahjongTile(tileId++, Suit.BAMBOO, (stackIdx % 9) + 1),
                    isTopDrawn = true,
                    isBottomDrawn = true
                )
            }
            PlayerWall(playerIndex = playerIndex, stacks = stacks, currentStackPointer = 18)
        }
    }

    @Test
    fun testExhaustiveDrawWhenAllWallsDepletedAfterLastDiscardPasses() = runBlocking {
        val claimState = ClaimInterceptionState()
        val playingState = PlayingState()
        val roundEndState = RoundEndState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(
                GamePhase.PLAYING to playingState,
                GamePhase.CLAIM_INTERCEPTION to claimState,
                GamePhase.ROUND_END to roundEndState
            )
        )

        val finalDiscard = MahjongTile(143, Suit.DOT, 8)
        machine.updateState { state ->
            state.copy(
                gamePhase = GamePhase.CLAIM_INTERCEPTION,
                isGameActive = true,
                players = GameState.initializePlayers(0),
                playerWalls = createDepletedWalls(),
                lastDiscarderIndex = 3,
                lastDiscardedTile = finalDiscard,
                activePlayerIndex = 3
            )
        }

        // All non-discarding players pass on the final discard
        claimState.handleInput(machine, GameInput.SubmitClaim(ClaimIntent(0, ClaimType.PASS)))
        claimState.handleInput(machine, GameInput.SubmitClaim(ClaimIntent(1, ClaimType.PASS)))
        claimState.handleInput(machine, GameInput.SubmitClaim(ClaimIntent(2, ClaimType.PASS)))

        assertEquals(GamePhase.ROUND_END, machine.currentPhase)
        val roundEndData = machine.currentState.roundEndData
        assertNotNull(roundEndData)
        assertTrue(roundEndData!!.isDraw)
        assertEquals(null, roundEndData.winnerIndex)
        assertEquals(1, machine.currentState.roundHistory.size)
        assertTrue(machine.currentState.roundHistory.first().isDraw)
    }

    @Test
    fun testExhaustiveDrawWhenClaimResolvedToHumanWithDepletedWalls() = runBlocking {
        val playingState = PlayingState()
        val roundEndState = RoundEndState()
        val machine = GameStateMachine(
            externalScope = testScope,
            stateMap = mapOf(
                GamePhase.PLAYING to playingState,
                GamePhase.ROUND_END to roundEndState
            )
        )

        machine.updateState { state ->
            state.copy(
                gamePhase = GamePhase.PLAYING,
                isGameActive = true,
                players = GameState.initializePlayers(0),
                playerWalls = createDepletedWalls(),
                activePlayerIndex = 0
            )
        }

        playingState.handleInput(machine, GameInput.ClaimResolved(0))

        assertEquals(GamePhase.ROUND_END, machine.currentPhase)
        val roundEndData = machine.currentState.roundEndData
        assertNotNull(roundEndData)
        assertTrue(roundEndData!!.isDraw)
        assertEquals(null, roundEndData.winnerIndex)
    }

    @Test
    fun testDynamicSeatWindsRelativeToDealer() {
        // When Player 1 is Dealer:
        // Player 1 is East (0 offset)
        // Player 2 is South (1 offset)
        // Player 3 is West (2 offset)
        // Player 0 is North (3 offset)
        val stateWithDealer1 = GameState(dealerIndex = 1)
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.EAST, stateWithDealer1.getPlayerSeatWind(1))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.SOUTH, stateWithDealer1.getPlayerSeatWind(2))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.WEST, stateWithDealer1.getPlayerSeatWind(3))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.NORTH, stateWithDealer1.getPlayerSeatWind(0))

        // When Player 0 is Dealer:
        val stateWithDealer0 = GameState(dealerIndex = 0)
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.EAST, stateWithDealer0.getPlayerSeatWind(0))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.SOUTH, stateWithDealer0.getPlayerSeatWind(1))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.WEST, stateWithDealer0.getPlayerSeatWind(2))
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.NORTH, stateWithDealer0.getPlayerSeatWind(3))
    }

    @Test
    fun testDealerElectionCountingFormula() {
        // Roller at index 0 (Bottom)
        val roller = 0

        // Sum = 3: 1=Bottom(0), 2=Right(1), 3=Top(2) -> Player 2 (Top) is elected
        val roll3Dealer = (roller + 3 - 1) % 4
        assertEquals(2, roll3Dealer)
        assertEquals(com.nowlsantos.mahjongai.model.MahjongSeat.TOP, com.nowlsantos.mahjongai.model.PlayerState.getMahjongSeat(0, roll3Dealer))
        val stateRoll3 = GameState(dealerIndex = roll3Dealer)
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.EAST, stateRoll3.getPlayerSeatWind(2))

        // Sum = 5: 1=0, 2=1, 3=2, 4=3, 5=0 -> Player 0 (Bottom) is elected
        val roll5Dealer = (roller + 5 - 1) % 4
        assertEquals(0, roll5Dealer)
        assertEquals(com.nowlsantos.mahjongai.model.MahjongSeat.BOTTOM, com.nowlsantos.mahjongai.model.PlayerState.getMahjongSeat(0, roll5Dealer))

        // Sum = 6: -> Player 1 (Right) is elected
        val roll6Dealer = (roller + 6 - 1) % 4
        assertEquals(1, roll6Dealer)
        assertEquals(com.nowlsantos.mahjongai.model.MahjongSeat.RIGHT, com.nowlsantos.mahjongai.model.PlayerState.getMahjongSeat(0, roll6Dealer))

        // Sum = 8: -> Player 3 (Left) is elected
        val roll8Dealer = (roller + 8 - 1) % 4
        assertEquals(3, roll8Dealer)
        assertEquals(com.nowlsantos.mahjongai.model.MahjongSeat.LEFT, com.nowlsantos.mahjongai.model.PlayerState.getMahjongSeat(0, roll8Dealer))
    }

    @Test
    fun testSucceedingRoundsTargetWallRollPreservesDealer() {
        // In Round 2, Player 1 is already Dealer (from previous round rotation)
        val round2State = GameState(roundNumber = 2, dealerIndex = 1)
        assertEquals(1, round2State.dealerIndex)

        // Dealer (Player 1) rolls 3 for Target Wall:
        // Counting from dealer (Player 1): 1=Player 1, 2=Player 2, 3=Player 3
        val rollSum = 3
        val targetWall = (round2State.dealerIndex + rollSum - 1) % 4
        assertEquals(3, targetWall)

        // In Round 2+, dealer remains Player 1
        val updatedState = round2State.copy(
            firstRollResult = com.nowlsantos.mahjongai.presentation.DiceResult(1, 2, 3),
            dealerIndex = round2State.dealerIndex, // stays Player 1
            targetWallPlayer = targetWall // Player 3's wall is targeted
        )
        assertEquals(1, updatedState.dealerIndex)
        assertEquals(3, updatedState.targetWallPlayer)
        assertEquals(com.nowlsantos.mahjongai.model.HonorType.EAST, updatedState.getPlayerSeatWind(1))
    }
}
