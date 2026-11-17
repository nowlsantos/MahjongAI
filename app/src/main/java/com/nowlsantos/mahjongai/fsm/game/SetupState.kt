package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.model.FlowerType
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.SeasonType
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.model.WallBuilder
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SetupState : GamePhaseState {
    private val tag = "SetupState"
    //private var transitionJob: Job? = null

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: SetupState State")

        // 1. Build a full 144-tile Hong Kong Mahjong deck with unique IDs 0..143
        val rawTiles = buildMahjongTiles().shuffled()

        // 2. Build the 4 player walls using the deck data
        val initialWalls = WallBuilder.createWallsFromDeck(rawTiles)

        // 3. Update the global GameState payload via the machine
        machine.updateState { currentState ->
            val updatedPlayers = if (currentState.players.isEmpty()) {
                GameState.initializePlayers(0)
            } else {
                currentState.players.mapValues { (_, player) ->
                    player.copy(
                        hand = emptyList(),
                        revealedMelds = emptyList(),
                        bonusTiles = emptyList(),
                        discards = emptyList(),
                        targetClaimTile = null,
                        targetClaimDiscarderIndex = null,
                        isTing = false,
                        isGameActive = false
                    )
                }
            }

            val roundNum = if (currentState.roundNumber > 0) currentState.roundNumber else 1
            val dealerIdx = currentState.dealerIndex
            val prevWind = currentState.prevalentWind

            currentState.copy(
                gamePhase = GamePhase.SETUP,
                playerWalls = initialWalls,
                players = updatedPlayers,
                roundNumber = roundNum,
                dealerIndex = dealerIdx,
                targetWallPlayer = dealerIdx,
                eastWindPlayerIndex = dealerIdx,
                prevalentWind = prevWind,
                publicDiscards = emptyList(),
                lastDiscardedTile = null,
                lastDiscarderIndex = null,
                diceResult = null,
                firstRollResult = null,
                secondRollResult = null,
                roundEndData = null,
                deadWallPlayer = 0,
                deadWallStackPointer = 0
            )
        }

        // In Round 2+, dealer was determined by previous round and is carried over.
        // In Round 1, dealer is elected via the first dice roll (Roll for Dealer).
        if (machine.currentState.roundNumber > 1) {
            val dealerSeat = com.nowlsantos.mahjongai.model.PlayerState.getMahjongSeat(0, machine.currentState.dealerIndex)
            machine.emitUiEffect(UiEffect.ShowEastWindBadge(dealerSeat))
        }
        machine.emitUiEffect(UiEffect.RefreshUI)

        // 4. Automatically progress to RollDiceState after 1 second
        delay(1000.milliseconds)
        machine.transitionTo(GamePhase.ROLLING_DICE)
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        // Initialization is fully automated; external user input is ignored here.
    }

    override suspend fun onExit(machine: GameStateMachine) {
        //transitionJob?.cancel()
        //transitionJob = null
        Logger.d(tag, "Exit: SetupState complete. Transitioning to RollingDiceState")
    }

    private fun buildMahjongTiles(): List<MahjongTile> {
        val deck = mutableListOf<MahjongTile>()
        var nextId = 0

        // Suits (Characters, Bamboos, Dots) - 4 copies of each value (1..9)
        for (suit in listOf(Suit.CHARACTER, Suit.BAMBOO, Suit.DOT)) {
            for (value in 1..9) {
                repeat(4) {
                    deck.add(
                        MahjongTile(
                            id = nextId++,
                            suit = suit,
                            value = value
                        )
                    )
                }
            }
        }

        // Honors (Winds: East, South, West, North & Dragons: Red, Green, White) - 4 copies each
        for (honor in HonorType.entries) {
            repeat(4) {
                deck.add(
                    MahjongTile(
                        id = nextId++,
                        suit = Suit.HONOR,
                        value = honor.ordinal + 1,
                        honorType = honor
                    )
                )
            }
        }

        // Bonus Tiles: Flowers (1..4) - 1 copy each
        for (flower in FlowerType.entries) {
            deck.add(
                MahjongTile(
                    id = nextId++,
                    suit = Suit.FLOWER,
                    value = flower.ordinal + 1,
                    flowerType = flower
                )
            )
        }

        // Bonus Tiles: Seasons (1..4) - 1 copy each
        for (season in SeasonType.entries) {
            deck.add(
                MahjongTile(
                    id = nextId++,
                    suit = Suit.SEASON,
                    value = season.ordinal + 1,
                    seasonType = season
                )
            )
        }

        require(deck.size == 144) { "Generated deck must contain exactly 144 tiles. Total: ${deck.size}" }
        return deck
    }
}
