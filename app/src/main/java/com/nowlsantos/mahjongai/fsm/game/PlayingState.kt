package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.engine.ExpectimaxEngine
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.engine.rl.RlDecisionEngine
import com.nowlsantos.mahjongai.engine.rl.TacticalPosture
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class PlayingState : GamePhaseState {
    private val tag = "PlayingState"
    private var bonusExchangeStarted = false
    private val expectimaxEngine = ExpectimaxEngine()
    private val rlDecisionEngine = RlDecisionEngine(expectimaxEngine)

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: PlayingState")
        if (!machine.currentState.isGameActive && !bonusExchangeStarted) {
            bonusExchangeStarted = true
            machine.handleInput(GameInput.ExecuteBonusExchange)
        }
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when(input) {
            is GameInput.ExecuteDiscard -> handleDiscardTile(input.playerId, input.tile)

            is GameInput.DrawReplacementTile -> {
                Logger.d(tag, "Executing replacement tile draw for Player ${input.playerIndex}.")
                machine.performDrawReplacement(input.playerIndex)
                machine.emitUiEffect(UiEffect.RefreshUI)
            }

            is GameInput.ClaimResolved -> {
                val walls = machine.currentState.playerWalls
                if (walls.isNotEmpty() && walls.values.all { it.isFullyDepleted }) {
                    Logger.w(tag, "Live wall exhausted upon ClaimResolved. Round ends in Draw (Lau Kuk).")
                    transitionToExhaustiveDraw(machine)
                    return
                }

                val nextPlayerIndex = input.activePlayerIndex
                Logger.d(tag, "Claim resolved. Active turn set to Player $nextPlayerIndex.")
                machine.updateState { it.copy(activePlayerIndex = nextPlayerIndex) }

                if (nextPlayerIndex == 0) {
                    // Turn belongs to Human Player 0: Awaiting user to click "Draw Tile"
                    Logger.d(tag, "Human Player 0 active turn: Awaiting user draw.")
                } else {
                    // Turn belongs to AI Player (1..3): Execute entire AI turn sequentially
                    executeAiTurn(machine, nextPlayerIndex)
                }
            }

            is GameInput.ExecuteClaimInterception -> {
                machine.transitionTo(GamePhase.CLAIM_INTERCEPTION)
            }

            is GameInput.ExecuteBonusExchange -> {
                executeBonusExchange(machine, machine.currentState.dealerIndex)
            }

            is GameInput.ExecuteBonusExchangeComplete -> {
                Logger.d(tag, "Initial Bonus Exchange phase complete. Game is now fully active!")
                val dealerIndex = machine.currentState.dealerIndex

                machine.updateState { state ->
                    val updatedPlayers = state.players.mapValues { (_, player) ->
                        player.copy(isGameActive = true)
                    }
                    state.copy(
                        isGameActive = true,
                        players = updatedPlayers,
                        activePlayerIndex = dealerIndex
                    )
                }

                if (dealerIndex == 0) {
                    // Human is Dealer: Player 0 already holds 14 tiles from initial deal, ready to discard
                    Logger.d(tag, "Human Player 0 is Dealer. Awaiting human opening discard.")
                } else {
                    // AI is Dealer: AI already holds 14 tiles, immediately evaluate opening discard
                    Logger.d(tag, "AI Player $dealerIndex is Dealer. Executing opening discard.")
                    executeAiTurnPostDraw(machine, dealerIndex)
                }
            }

            is GameInput.DeclareSelfDrawWin -> {
                Logger.d(tag, "Human Player ${input.playerIndex} declared SELF-DRAW WIN (Hu)!")
                machine.updateState { state ->
                    val winnerState = state.players[input.playerIndex]
                    val updatedPlayers = if (winnerState != null) {
                        val baseHandWithoutWinningTile = winnerState.hand.filter { it.id != input.winningTile.id }
                        val sortedBaseHand = baseHandWithoutWinningTile.sorted()
                        // Winning tile remains at the far right / last index
                        val newHand = sortedBaseHand + input.winningTile
                        state.players + (input.playerIndex to winnerState.copy(hand = newHand))
                    } else {
                        state.players
                    }

                    state.copy(
                        players = updatedPlayers,
                        roundEndData = com.nowlsantos.mahjongai.model.RoundEndData(
                            winnerIndex = input.playerIndex,
                            discarderIndex = null,
                            winningTile = input.winningTile,
                            isSelfDraw = true,
                            isWinOnReplacement = state.isLastDrawReplacement,
                            isDraw = false,
                            scoringResult = null
                        )
                    )
                }
                machine.emitUiEffect(UiEffect.RefreshUI)
                machine.transitionTo(GamePhase.ROUND_END)
            }
            else -> {}
        }
    }

    override suspend fun onExit(machine: GameStateMachine) {
        bonusExchangeStarted = false
        Logger.d(tag, "PlayingState complete. Entering either Claim|Thinking or RoundEnd State.")
    }

    /**
     * Executes a complete, unified AI turn:
     * 1. Draws regular tile from wall.
     * 2. If bonus tiles are drawn, melds them and draws dead-wall replacements in a loop.
     * 3. Checks for Self-Draw Win (Tsumo).
     * 4. Evaluates strategic in-turn Kong (Concealed or Added).
     * 5. Runs ExpectimaxEngine to select the optimal discard tile.
     * 6. Executes the discard animation and commits it to the board.
     *
     * Because this runs in a direct sequential coroutine, it is physically impossible
     * for the AI to "stall" or go to sleep in an IDLE state without discarding.
     */
    suspend fun executeAiTurn(machine: GameStateMachine, playerIndex: Int) {
        Logger.d(tag, "AI Player $playerIndex starting turn draw.")

        // 1. Regular Draw
        val drawnTile = machine.performDraw(playerIndex)
        if (drawnTile == null) {
            Logger.w(tag, "Live wall exhausted while Player $playerIndex attempted to draw. Round ends in Draw (Lau Kuk).")
            machine.updateState { state ->
                state.copy(
                    roundEndData = com.nowlsantos.mahjongai.model.RoundEndData(
                        winnerIndex = null,
                        discarderIndex = null,
                        winningTile = null,
                        isSelfDraw = false,
                        isDraw = true,
                        scoringResult = null
                    )
                )
            }
            machine.transitionTo(GamePhase.ROUND_END)
            return
        }

        delay(600.milliseconds) // Turn pacing pause after drawing

        // 2. Resolve post-draw actions (bonus melds, kong, discard)
        executeAiTurnPostDraw(machine, playerIndex)
    }

    /**
     * Executes the post-draw phase of an AI turn (also used after claim melds or opening dealer turn).
     */
    suspend fun executeAiTurnPostDraw(machine: GameStateMachine, playerIndex: Int) {
        // Loop while holding bonus tiles: meld and draw replacement
        while (machine.currentState.players[playerIndex]?.hand?.any { it.isBonus } == true) {
            val bonusTile = machine.currentState.players[playerIndex]?.hand?.firstOrNull { it.isBonus } ?: break
            Logger.d(tag, "AI Player $playerIndex melding bonus tile: ${bonusTile.displayName}.")
            machine.performBonusMeld(playerIndex, bonusTile)
            delay(400.milliseconds)

            val replacementTile = machine.performDrawReplacement(playerIndex)
            if (replacementTile == null) {
                Logger.w(tag, "Wall exhausted while AI Player $playerIndex attempted to draw bonus replacement. Round ends in Draw (Lau Kuk).")
                transitionToExhaustiveDraw(machine)
                return
            }
            Logger.d(tag, "AI Player $playerIndex received replacement tile: ${replacementTile.displayName}.")
            delay(400.milliseconds)
        }

        val playerState = machine.currentState.players[playerIndex] ?: return
        val seatWind = machine.currentState.getPlayerSeatWind(playerIndex)
        val prevalentWind = machine.currentState.prevalentWind

        val minFan = if (playerIndex == 0) GameConstants.MIN_FAN_REQUIRED else GameConstants.AI_MIN_FAN_REQUIRED
        val legalActions = MoveGenerator.getLegalTurnActions(
            playerHand = playerState.hand,
            revealedMelds = playerState.revealedMelds,
            bonusTiles = playerState.bonusTiles,
            seatWind = seatWind,
            prevalentWind = prevalentWind,
            minFanRequired = minFan
        )

        // 3. Check for Self-Draw Win (Tsumo) - Strict 3+ Fan enforcement for AI
        val selfDrawWin = legalActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
        if (selfDrawWin != null) {
            val fanScore = FanCalculator.calculateFan(
                hand = playerState.hand,
                revealedMelds = playerState.revealedMelds,
                bonusTiles = playerState.bonusTiles,
                isSelfDraw = true,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                isWinOnReplacement = machine.currentState.isLastDrawReplacement,
                turnNumber = playerState.discards.size
            )

            if (playerIndex != 0 && fanScore.totalFan < GameConstants.AI_MIN_FAN_REQUIRED) {
                Logger.d(tag, "AI Player $playerIndex has winning hand shape but only ${fanScore.totalFan} Fan (< ${GameConstants.AI_MIN_FAN_REQUIRED}). Passing win to build higher value hand.")
            } else {
                Logger.d(tag, "AI Player $playerIndex achieved SELF-DRAW WIN (Hu) with ${fanScore.totalFan} Fan (${fanScore.patternNames.joinToString()})!")
                machine.updateState { state ->
                    val winnerState = state.players[playerIndex]
                    val updatedPlayers = if (winnerState != null) {
                        val baseHandWithoutWinningTile = winnerState.hand.filter { it.id != selfDrawWin.winningTile.id }
                        val sortedBaseHand = baseHandWithoutWinningTile.sorted()
                        // Winning tile remains at the far right / last index
                        val newHand = sortedBaseHand + selfDrawWin.winningTile
                        state.players + (playerIndex to winnerState.copy(hand = newHand))
                    } else {
                        state.players
                    }

                    val totalWallTilesRemaining = state.playerWalls.values.sumOf { it.currentSize }
                    val isLastTileWall = totalWallTilesRemaining == 0

                    state.copy(
                        players = updatedPlayers,
                        roundEndData = com.nowlsantos.mahjongai.model.RoundEndData(
                            winnerIndex = playerIndex,
                            discarderIndex = null,
                            winningTile = selfDrawWin.winningTile,
                            isSelfDraw = true,
                            isWinOnReplacement = state.isLastDrawReplacement,
                            isLastTileWall = isLastTileWall,
                            isDraw = false,
                            scoringResult = fanScore
                        )
                    )
                }
                machine.transitionTo(GamePhase.ROUND_END)
                return
            }
        }

        val opponents = machine.currentState.players.values.filter { it.playerId != playerIndex }
        val isOpponentInTing = opponents.any { it.isTing }
        val maxOpponentMelds = opponents.maxOfOrNull { it.revealedMelds.size } ?: 0
        val currentShanten = TingCalculator.calculateExactShanten(playerState.hand, playerState.revealedMelds.size)
        val wallRemaining = machine.currentState.playerWalls.values.sumOf { it.currentSize }

        val posture = TacticalPosture.evaluate(
            hand = playerState.hand,
            currentShanten = currentShanten,
            maxOpponentMelds = maxOpponentMelds,
            isAnyOpponentInTing = isOpponentInTing,
            wallRemaining = wallRemaining,
            playerScore = playerState.score
        )

        // 4. Strategic In-Turn Kong Discipline
        // Never declare Kong when folding (DEFENSIVE_FOLD), and avoid Added Kongs of live central suit tiles when opponents threaten Ting!
        val inTurnKong = legalActions.filterIsInstance<TurnAction.ConcealedKong>().firstOrNull()
            ?: legalActions.filterIsInstance<TurnAction.AddedKong>().firstOrNull()
        if (inTurnKong != null && wallRemaining > 0 && posture.type != com.nowlsantos.mahjongai.engine.rl.PostureType.DEFENSIVE_FOLD) {
            val shouldKong = when (inTurnKong) {
                is TurnAction.AddedKong -> {
                    val isRiskyCentralTile = inTurnKong.tile.isSuitTile && inTurnKong.tile.value in 3..7
                    // Avoid Robbing-the-Kong trap and giving free Kabe info if far from ready
                    currentShanten <= 2 && !(isOpponentInTing && isRiskyCentralTile)
                }
                is TurnAction.ConcealedKong -> {
                    val handAfterKong = playerState.hand - inTurnKong.tiles.toSet()
                    val shantenAfterKong = TingCalculator.calculateExactShanten(handAfterKong, playerState.revealedMelds.size + 1)
                    // Ensure Concealed Kong does not destroy adjacent suit sequences or Seven Pairs
                    shantenAfterKong <= currentShanten && currentShanten <= 2
                }
                else -> false
            }

            if (shouldKong) {
                Logger.d(tag, "AI Player $playerIndex declaring strategic in-turn Kong: $inTurnKong")
                delay(600.milliseconds)
                val replacement = machine.performKongMeld(playerIndex, inTurnKong)
                if (replacement == null) {
                    Logger.w(tag, "Wall exhausted while AI Player $playerIndex attempted to draw Kong replacement. Round ends in Draw (Lau Kuk).")
                    transitionToExhaustiveDraw(machine)
                    return
                }
                Logger.d(tag, "AI Player $playerIndex received Kong replacement tile: ${replacement.displayName}")
                // Recurse to handle flowers on replacement and continue turn to discard
                executeAiTurnPostDraw(machine, playerIndex)
                return
            }
        }

        // 5. Select Discard via RlDecisionEngine (Reinforcement Learning Policy with Dynamic Tactical Posture)
        val discardCandidates = legalActions.filterIsInstance<TurnAction.Discard>().map { it.tile }
        val allRevealedMelds = machine.currentState.players.values.flatMap { it.revealedMelds }.flatMap { it.tiles }
        val visibleBoardTiles = machine.currentState.publicDiscards + allRevealedMelds
        val hasRevealedChow = playerState.revealedMelds.any { it.type == MeldType.CHOW }

        val rlDecision = if (discardCandidates.isNotEmpty()) {
            rlDecisionEngine.selectOptimalDiscard(
                currentHand = playerState.hand,
                revealedMeldsCount = playerState.revealedMelds.size,
                visibleBoardTiles = visibleBoardTiles,
                posture = posture,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = hasRevealedChow,
                isOpponentInTing = isOpponentInTing,
                opponents = opponents,
                wallRemaining = wallRemaining,
                playerScore = playerState.score
            )
        } else null

        val tileToDiscard = if (rlDecision != null) {
            discardCandidates.find { it.suit == rlDecision.tileToDiscard.suit && it.value == rlDecision.tileToDiscard.value }
                ?: rlDecision.tileToDiscard
        } else {
            playerState.hand.filterNot { it.isBonus }.firstOrNull() ?: playerState.hand.firstOrNull()
        }

        if (tileToDiscard != null) {
            val shantenAfter = rlDecision?.resultingShanten ?: TingCalculator.calculateExactShanten(playerState.hand - tileToDiscard, playerState.revealedMelds.size)
            Logger.d(tag, "AI Player $playerIndex (Posture: ${posture.type.name}) chose discard: ${tileToDiscard.displayName} (Q-Val: ${"%.1f".format(rlDecision?.qValue ?: 0.0)}, Reward: ${"%.1f".format(rlDecision?.intermediateReward ?: 0.0)}, Danger: ${"%.2f".format(rlDecision?.dangerScore ?: 0.0)}, Shanten: $shantenAfter)")
            delay(800.milliseconds) // Human-friendly thinking pause before discarding
            machine.performDiscard(playerIndex, tileToDiscard)
        }
    }

    private suspend fun executeBonusExchange(machine: GameStateMachine, dealerIndex: Int) {
        Logger.d(tag, "Starting turn-order Bonus Exchange starting with Dealer (Player $dealerIndex)...")

        for (seatOffset in 0 until 4) {
            val currentPlayerIndex = (dealerIndex + seatOffset) % 4
            delay(400.milliseconds) // Pause between players

            machine.updateState { it.copy(activePlayerIndex = currentPlayerIndex) }

            if (currentPlayerIndex == 0) {
                // Human Player 0: Manually meld via UI button. Wait for human if holding bonus tiles.
                while (machine.currentState.players[0]?.hand?.any { it.isBonus } == true) {
                    Logger.d(tag, "Human Player 0 has bonus tiles to meld. Waiting for human UI action...")
                    machine.waitForHumanFlower()
                }
                machine.emitUiEffect(UiEffect.RefreshUI)
            } else {
                // AI Players (1..3): Execute bonus melding loop directly
                while (machine.currentState.players[currentPlayerIndex]?.hand?.any { it.isBonus } == true) {
                    val bonusTile = machine.currentState.players[currentPlayerIndex]?.hand?.firstOrNull { it.isBonus } ?: break
                    Logger.d(tag, "Initial Bonus Exchange: AI Player $currentPlayerIndex melding ${bonusTile.displayName}...")
                    machine.performBonusMeld(currentPlayerIndex, bonusTile)
                    delay(350.milliseconds)
                    machine.performDrawReplacement(currentPlayerIndex)
                    delay(350.milliseconds)
                }
                machine.emitUiEffect(UiEffect.RefreshUI)
            }
        }

        Logger.d(tag, "Bonus Exchange complete for all players.")
        machine.handleInput(GameInput.ExecuteBonusExchangeComplete)
    }

    private fun handleDiscardTile(playerId: Int, tile: MahjongTile) {
        Logger.d(tag, "Attempting to handle the discarded tile $playerId | ${tile.displayName}")
    }

    private suspend fun transitionToExhaustiveDraw(machine: GameStateMachine) {
        if (machine.currentPhase == GamePhase.ROUND_END) return
        machine.updateState { state ->
            state.copy(
                roundEndData = com.nowlsantos.mahjongai.model.RoundEndData(
                    winnerIndex = null,
                    discarderIndex = null,
                    winningTile = null,
                    isSelfDraw = false,
                    isDraw = true,
                    scoringResult = null
                )
            )
        }
        machine.emitUiEffect(UiEffect.RefreshUI)
        machine.transitionTo(GamePhase.ROUND_END)
    }
}
