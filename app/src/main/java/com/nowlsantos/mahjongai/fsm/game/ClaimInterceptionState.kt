package com.nowlsantos.mahjongai.fsm.game

import com.nowlsantos.mahjongai.engine.ExpectimaxEngine
import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimResolver
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.engine.rl.PostureType
import com.nowlsantos.mahjongai.engine.rl.RlDecisionEngine
import com.nowlsantos.mahjongai.engine.rl.TacticalPosture
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.engine.scoring.KongPaymentRules
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.model.RoundEndData
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.presentation.UiEffect
import com.nowlsantos.mahjongai.ui.animations.AnimationType
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class ClaimInterceptionState : GamePhaseState {
    private val tag = "ClaimInterceptionState"
    private val submittedIntents = mutableListOf<ClaimIntent>()
    private val claimMutex = Mutex()
    private var isResolved = false
    private val expectimaxEngine = ExpectimaxEngine()
    private val rlDecisionEngine = RlDecisionEngine()

    override suspend fun onEnter(machine: GameStateMachine) {
        Logger.d(tag, "Enter:: ClaimInterceptionState. Collecting claim intents...")
        claimMutex.withLock {
            submittedIntents.clear()
            isResolved = false
        }

        val discarderIndex = machine.currentState.lastDiscarderIndex ?: 0
        val lastDiscardedTile = machine.currentState.lastDiscardedTile

        if (lastDiscardedTile == null) {
            Logger.w(tag, "No discarded tile found in ClaimInterceptionState. Advancing normal turn.")
            advanceTurn(machine, discarderIndex)
            return
        }

        // Case A: Human was the discarder (discarderIndex == 0). Only AI opponents can claim.
        if (discarderIndex == 0) {
            scheduleAiClaimSubmissions(machine, discarderIndex, lastDiscardedTile, delayMillis = 800)
            return
        }

        // Case B: An AI opponent discarded (discarderIndex != 0).
        handleAiDiscardInterception(machine, discarderIndex, lastDiscardedTile)
    }

    private fun handleAiDiscardInterception(
        machine: GameStateMachine,
        discarderIndex: Int,
        lastDiscardedTile: MahjongTile
    ) {
        // 1. Pre-evaluate competing AI claim intents to avoid prompting human for an unwinnable claim
        val aiNonDiscarders = (1..3).filter { it != discarderIndex }
        val aiIntents = aiNonDiscarders.map { aiIndex ->
            evaluateAiClaim(machine, aiIndex, lastDiscardedTile, discarderIndex)
        }
        val maxAiPriority = aiIntents.maxOfOrNull { it.claimType.priority } ?: 0

        // 2. Evaluate Human Player 0 claims
        val humanClaims = getHumanLegalClaims(machine, discarderIndex, lastDiscardedTile)
        val actionableHumanClaims = humanClaims.filter { it.claimType != ClaimType.PASS }
        val maxHumanPriority = actionableHumanClaims.maxOfOrNull { it.claimType.priority } ?: 0

        // 3. If an AI player holds a strictly higher-priority claim (e.g. AI Pung/Kong/Win beats Human Chow),
        // award the claim to the higher priority AI immediately without prompting or bothering the human player.
        if (maxAiPriority > maxHumanPriority) {
            Logger.d(tag, "AI has higher priority claim ($maxAiPriority > $maxHumanPriority). Bypassing human claim prompt.")
            autoPassHumanClaim(machine)
            scheduleAiClaimSubmissions(machine, aiIntents, delayMillis = 600)
            return
        }

        // 4. If human has viable claims that can actually win:
        if (actionableHumanClaims.isNotEmpty()) {
            val eligibleClaims = humanClaims.filter { (it.claimType == ClaimType.PASS) || (it.claimType.priority >= maxAiPriority) }
            Logger.d(tag, "Human Player 0 has viable claims: ${eligibleClaims.map { it.claimType }}. Prompting UI overlay...")
            machine.emitUiEffect(UiEffect.PromptHumanClaim(eligibleClaims))
            scheduleAiClaimSubmissions(machine, aiIntents, delayMillis = 800)
        } else {
            // Human has no legal claims at all. Auto-submit PASS.
            autoPassHumanClaim(machine)
            Logger.d(tag, "Human Player 0 has no legal claims. Auto-submitted PASS intent.")
            scheduleAiClaimSubmissions(machine, aiIntents, delayMillis = 800)
        }
    }

    private fun getHumanLegalClaims(
        machine: GameStateMachine,
        discarderIndex: Int,
        lastDiscardedTile: MahjongTile
    ): List<ClaimIntent> {
        val humanPlayer = machine.currentState.players[0] ?: return listOf(ClaimIntent(0, ClaimType.PASS))
        val seatWind = machine.currentState.getPlayerSeatWind(0)
        val prevalentWind = machine.currentState.prevalentWind

        val claims = MoveGenerator.getLegalClaims(
            playerIndex = 0,
            playerHand = humanPlayer.hand,
            revealedMelds = humanPlayer.revealedMelds,
            bonusTiles = humanPlayer.bonusTiles,
            discarderIndex = discarderIndex,
            discardedTile = lastDiscardedTile,
            seatWind = seatWind,
            prevalentWind = prevalentWind
        )

        val walls = machine.currentState.playerWalls
        val isWallExhausted = walls.isNotEmpty() && walls.values.all { it.isFullyDepleted }
        return if (isWallExhausted) {
            // On the final discard with 0 wall tiles left, only Ron (WIN) or PASS is legal
            claims.filter { it.claimType == ClaimType.WIN || it.claimType == ClaimType.PASS }
        } else {
            claims
        }
    }

    private fun autoPassHumanClaim(machine: GameStateMachine) {
        val passIntent = ClaimIntent(playerIndex = 0, claimType = ClaimType.PASS)
        machine.externalScope.launch {
            claimMutex.withLock {
                submittedIntents.add(passIntent)
            }
        }
    }

    private fun scheduleAiClaimSubmissions(
        machine: GameStateMachine,
        discarderIndex: Int,
        lastDiscardedTile: MahjongTile,
        delayMillis: Long
    ) {
        machine.externalScope.launch {
            delay(delayMillis.milliseconds)
            for (aiIndex in 1..3) {
                val aiIntent = evaluateAiClaim(machine, aiIndex, lastDiscardedTile, discarderIndex)
                Logger.d(tag, "AI Player $aiIndex evaluated strategic claim intent: ${aiIntent.claimType}")
                machine.handleInput(GameInput.SubmitClaim(aiIntent))
            }
        }
    }

    private fun scheduleAiClaimSubmissions(
        machine: GameStateMachine,
        aiIntents: List<ClaimIntent>,
        delayMillis: Long
    ) {
        machine.externalScope.launch {
            delay(delayMillis.milliseconds)
            for (aiIntent in aiIntents) {
                Logger.d(tag, "AI Player ${aiIntent.playerIndex} submitting claim intent: ${aiIntent.claimType}")
                machine.handleInput(GameInput.SubmitClaim(aiIntent))
            }
        }
    }

    override suspend fun handleInput(machine: GameStateMachine, input: GameInput) {
        when (input) {
            is GameInput.ExecuteClaimInterception -> {
                Logger.d(tag, "Re-entering ClaimInterceptionState for fresh discard.")
                onEnter(machine)
            }
            is GameInput.SubmitClaim -> {
                val intentsSnapshot = claimMutex.withLock {
                    if (isResolved) return
                    // Avoid duplicate submissions from the same player
                    if (submittedIntents.any { it.playerIndex == input.claimIntent.playerIndex }) return
                    submittedIntents.add(input.claimIntent)
                    Logger.d(tag, "Received claim intent from Player ${input.claimIntent.playerIndex}: ${input.claimIntent.claimType}")

                    if (submittedIntents.size >= 3) {
                        isResolved = true
                        submittedIntents.toList()
                    } else {
                        null
                    }
                } ?: return

                resolveInterception(machine, intentsSnapshot)
            }
            else -> {}
        }
    }

    private suspend fun resolveInterception(machine: GameStateMachine, intentsSnapshot: List<ClaimIntent>) {
        val discarderIndex = machine.currentState.lastDiscarderIndex ?: 0
        val winningClaim = ClaimResolver.resolveHighestClaim(discarderIndex, intentsSnapshot)

        if (winningClaim != null && winningClaim.claimType != ClaimType.PASS) {
            Logger.d(tag, "Winning claim granted to Player ${winningClaim.playerIndex}: ${winningClaim.claimType}")

            val claimedTile = machine.currentState.lastDiscardedTile
            if (claimedTile == null) {
                Logger.w(tag, "lastDiscardedTile was null during claim resolution; falling back to next player.")
                advanceTurn(machine, discarderIndex)
                return
            }

            val winnerIndex = winningClaim.playerIndex

            // 1. Handle terminal WIN (Ron) claim
            if (winningClaim.claimType == ClaimType.WIN) {
                executeRonWin(machine, winnerIndex, discarderIndex, claimedTile)
                return
            }

            // 2. Handle Meld claim (Chow, Pung, Kong)
            executeMeldClaim(machine, winnerIndex, discarderIndex, winningClaim, claimedTile)
        } else {
            Logger.d(tag, "All players passed claim. Advancing normal turn.")
            advanceTurn(machine, discarderIndex)
        }
    }

    private suspend fun executeRonWin(
        machine: GameStateMachine,
        winnerIndex: Int,
        discarderIndex: Int,
        claimedTile: MahjongTile
    ) {
        Logger.d(tag, "Player $winnerIndex WON on discard of ${claimedTile.displayName}!")
        machine.updateState { state ->
            val winnerState = state.players[winnerIndex]
            val updatedPlayers = if (winnerState != null) {
                val baseHandWithoutWinningTile = winnerState.hand.filter { it.id != claimedTile.id }
                val sortedBaseHand = if (winnerIndex != 0) {
                    baseHandWithoutWinningTile.sorted()
                } else {
                    baseHandWithoutWinningTile
                }
                // Winning tile remains at the far right / last index
                val newHand = sortedBaseHand + claimedTile
                state.players + (winnerIndex to winnerState.copy(hand = newHand))
            } else {
                state.players
            }

            val totalWallTilesRemaining = state.playerWalls.values.sumOf { it.currentSize }
            val isLastTileWall = totalWallTilesRemaining == 0

            state.copy(
                players = updatedPlayers,
                publicDiscards = state.publicDiscards - claimedTile,
                lastDiscardedTile = null,
                roundEndData = RoundEndData(
                    winnerIndex = winnerIndex,
                    discarderIndex = discarderIndex,
                    winningTile = claimedTile,
                    isSelfDraw = false,
                    isLastTileWall = isLastTileWall,
                    isDraw = false,
                    scoringResult = null
                )
            )
        }
        machine.transitionTo(GamePhase.ROUND_END)
    }

    private suspend fun executeMeldClaim(
        machine: GameStateMachine,
        winnerIndex: Int,
        discarderIndex: Int,
        winningClaim: ClaimIntent,
        claimedTile: MahjongTile
    ) {
        val winnerSeat = PlayerState.getMahjongSeat(0, winnerIndex)
        val animType = when (winningClaim.claimType) {
            ClaimType.CHOW -> AnimationType.CHOW_TILES
            ClaimType.PUNG -> AnimationType.PUNG_TILES
            ClaimType.KONG -> AnimationType.KONG_TILES
            else -> AnimationType.PUNG_TILES
        }

        // 1. Remove claimed tile from public discards so it disappears from pool as flight begins
        machine.updateState { state ->
            state.copy(publicDiscards = state.publicDiscards - claimedTile)
        }

        // 2. Await Meld flight animation
        machine.performMeldAnimation(winnerIndex, claimedTile, winnerSeat, animType)

        val kongPaymentDeltas = if (winningClaim.claimType == ClaimType.KONG) {
            KongPaymentRules.calculateKongPayment(
                declarerIndex = winnerIndex,
                isConcealed = false,
                isPromoted = false,
                discarderIndex = discarderIndex
            )
        } else emptyMap()

        // 3. Execute Meld in GameState
        machine.updateState { state ->
            val winnerState = state.players[winnerIndex]
            if (winnerState != null) {
                val handAfterClaim = winnerState.hand.toMutableList()
                winningClaim.tilesToMelded.forEach { handAfterClaim.remove(it) }

                val meldTiles = if (winningClaim.claimType == ClaimType.CHOW) {
                    (winningClaim.tilesToMelded + claimedTile).sortedBy { it.value }
                } else {
                    winningClaim.tilesToMelded + claimedTile
                }

                val newMeld = Meld(
                    type = when (winningClaim.claimType) {
                        ClaimType.PUNG -> MeldType.PUNG
                        ClaimType.KONG -> MeldType.KONG
                        ClaimType.CHOW -> MeldType.CHOW
                        else -> MeldType.PUNG
                    },
                    tiles = meldTiles
                )

                val finalHand = if (winnerIndex != 0) handAfterClaim.sorted() else handAfterClaim
                val updatedWinnerState = winnerState.copy(
                    hand = finalHand,
                    revealedMelds = winnerState.revealedMelds + newMeld
                )

                val updatedPlayers = state.players.mapValues { (pid, p) ->
                    val delta = kongPaymentDeltas[pid] ?: 0
                    val basePlayer = if (pid == winnerIndex) updatedWinnerState else p
                    basePlayer.copy(score = (basePlayer.score + delta).coerceAtLeast(0))
                }

                state.copy(players = updatedPlayers)
            } else state
        }
        if (kongPaymentDeltas.isNotEmpty()) {
            machine.emitUiEffect(UiEffect.ShowKongPayment(kongPaymentDeltas))
        }
        machine.emitUiEffect(UiEffect.RefreshUI)

        // 4. Transition back to PLAYING phase with winner as active player
        machine.updateState { it.copy(activePlayerIndex = winnerIndex) }
        machine.transitionTo(GamePhase.PLAYING)
        val pauseDuration = if (winningClaim.claimType == ClaimType.KONG) 1200.milliseconds else 600.milliseconds
        delay(pauseDuration) // Pause so melded tiles landing and kong payment are clearly visible

        // 5. Winner takes their turn in PLAYING phase
        resumeTurnAfterMeld(machine, winnerIndex, winningClaim)
    }

    private suspend fun resumeTurnAfterMeld(
        machine: GameStateMachine,
        winnerIndex: Int,
        winningClaim: ClaimIntent
    ) {
        if (winningClaim.claimType == ClaimType.KONG) {
            val replacement = machine.performDrawReplacement(winnerIndex)
            if (replacement == null) {
                Logger.w(tag, "Wall exhausted while drawing Kong replacement for Player $winnerIndex. Ending round as Exhaustive Draw.")
                transitionToExhaustiveDraw(machine)
                return
            }
            if (winnerIndex == 0) {
                Logger.d(tag, "Human Player 0 won Kong claim. Replacement tile drawn: ${replacement.displayName}. Awaiting human discard.")
                machine.emitUiEffect(UiEffect.RefreshUI)
            } else {
                PlayingState().executeAiTurnPostDraw(machine, winnerIndex)
            }
        } else {
            if (winnerIndex == 0) {
                Logger.d(tag, "Human Player 0 won claim. Awaiting human discard.")
            } else {
                PlayingState().executeAiTurnPostDraw(machine, winnerIndex)
            }
        }
    }

    private suspend fun advanceTurn(machine: GameStateMachine, discarderIndex: Int) {
        val walls = machine.currentState.playerWalls
        val isWallExhausted = walls.isNotEmpty() && walls.values.all { it.isFullyDepleted }
        if (isWallExhausted) {
            Logger.w(tag, "Live wall exhausted after final discard went unclaimed. Round ends in Draw (Lau Kuk).")
            delay(800.milliseconds) // Turn pacing pause so final discard stays visible before modal appears
            transitionToExhaustiveDraw(machine)
            return
        }

        val nextPlayerIndex = (discarderIndex + 1) % 4
        delay(800.milliseconds) // Turn pacing pause before resuming play
        machine.updateState { it.copy(activePlayerIndex = nextPlayerIndex) }
        machine.transitionTo(GamePhase.PLAYING)
        machine.handleInput(GameInput.ClaimResolved(nextPlayerIndex))
    }

    private suspend fun transitionToExhaustiveDraw(machine: GameStateMachine) {
        if (machine.currentPhase == GamePhase.ROUND_END) return
        machine.updateState { state ->
            state.copy(
                roundEndData = RoundEndData(
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

    private fun evaluateAiClaim(
        machine: GameStateMachine,
        playerIndex: Int,
        targetTile: MahjongTile,
        discarderIndex: Int
    ): ClaimIntent {
        val playerState = machine.currentState.players[playerIndex] ?: return ClaimIntent(playerIndex, ClaimType.PASS)
        val seatWind = machine.currentState.getPlayerSeatWind(playerIndex)
        val prevalentWind = machine.currentState.prevalentWind
        val currentRevealedCount = playerState.revealedMelds.size
        val hasRevealedChow = playerState.revealedMelds.any { it.type == MeldType.CHOW }

        val minFan = if (playerIndex == 0) GameConstants.MIN_FAN_REQUIRED else GameConstants.AI_MIN_FAN_REQUIRED
        val legalClaims = MoveGenerator.getLegalClaims(
            playerIndex = playerIndex,
            playerHand = playerState.hand,
            revealedMelds = playerState.revealedMelds,
            bonusTiles = playerState.bonusTiles,
            discarderIndex = discarderIndex,
            discardedTile = targetTile,
            seatWind = seatWind,
            prevalentWind = prevalentWind,
            minFanRequired = minFan
        )

        val allRevealedMeldTiles = machine.currentState.players.values.flatMap { it.revealedMelds }.flatMap { it.tiles }
        val visibleBoardTiles = machine.currentState.publicDiscards + allRevealedMeldTiles
        val allVisibleTiles = playerState.hand.filterNot { it.isBonus } + visibleBoardTiles
        val visibleCounts = TingCalculator.buildVisibleTileCounts(allVisibleTiles)

        val currentShanten = TingCalculator.calculateExactShanten(playerState.hand, currentRevealedCount)
        val currentUtility = expectimaxEngine.evaluatePostDiscardState(
            handAfterDiscard = playerState.hand,
            revealedMeldsCount = currentRevealedCount,
            visibleCounts = visibleCounts,
            seatWind = seatWind,
            prevalentWind = prevalentWind,
            hasRevealedChow = hasRevealedChow
        )

        val opponents = machine.currentState.players.values.filter { it.playerId != playerIndex }
        val isOpponentInTing = opponents.any { it.isTing }
        val maxOpponentMelds = opponents.maxOfOrNull { it.revealedMelds.size } ?: 0
        val wallRemaining = machine.currentState.playerWalls.values.sumOf { it.currentSize }

        val posture = TacticalPosture.evaluate(
            hand = playerState.hand,
            currentShanten = currentShanten,
            maxOpponentMelds = maxOpponentMelds,
            isAnyOpponentInTing = isOpponentInTing,
            wallRemaining = wallRemaining,
            playerScore = playerState.score
        )

        val rawWinClaim = legalClaims.find { it.claimType == ClaimType.WIN }
        val winClaim = if (rawWinClaim != null) {
            val fanScore = FanCalculator.calculateFan(
                hand = playerState.hand + targetTile,
                revealedMelds = playerState.revealedMelds,
                bonusTiles = playerState.bonusTiles,
                isSelfDraw = false,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                turnNumber = playerState.discards.size
            )
            if (playerIndex != 0 && fanScore.totalFan < GameConstants.AI_MIN_FAN_REQUIRED) {
                Logger.d(tag, "AI Player $playerIndex rejecting Ron win on ${targetTile.displayName}: only ${fanScore.totalFan} Fan (< ${GameConstants.AI_MIN_FAN_REQUIRED}).")
                null
            } else {
                Logger.d(tag, "AI Player $playerIndex taking Ron win on ${targetTile.displayName} with ${fanScore.totalFan} Fan (${fanScore.patternNames.joinToString()})!")
                rawWinClaim
            }
        } else null

        val kongClaim = legalClaims.find { it.claimType == ClaimType.KONG }
        val pungClaim = legalClaims.find { it.claimType == ClaimType.PUNG }
        val chowClaims = legalClaims.filter { it.claimType == ClaimType.CHOW }
        val isWallExhausted = machine.currentState.playerWalls.isNotEmpty() &&
            machine.currentState.playerWalls.values.all { it.isFullyDepleted }

        return when {
            winClaim != null -> winClaim

            // On the final discard with 0 wall tiles remaining, only Ron (WIN) is allowed
            isWallExhausted -> ClaimIntent(playerIndex, ClaimType.PASS)

            kongClaim != null && shouldClaimKong(
                hand = playerState.hand,
                kongClaim = kongClaim,
                currentRevealedCount = currentRevealedCount,
                currentShanten = currentShanten,
                posture = posture
            ) -> kongClaim

            pungClaim != null && shouldClaimPung(
                hand = playerState.hand,
                targetTile = targetTile,
                pungClaim = pungClaim,
                currentRevealedCount = currentRevealedCount,
                currentShanten = currentShanten,
                currentUtility = currentUtility,
                visibleBoardTiles = visibleBoardTiles,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = hasRevealedChow,
                posture = posture
            ) -> pungClaim

            chowClaims.isNotEmpty() -> selectBestStrategicChow(
                playerIndex = playerIndex,
                hand = playerState.hand,
                targetTile = targetTile,
                chowClaims = chowClaims,
                currentRevealedCount = currentRevealedCount,
                currentShanten = currentShanten,
                currentUtility = currentUtility,
                visibleBoardTiles = visibleBoardTiles,
                visibleCounts = visibleCounts,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                posture = posture
            )

            else -> ClaimIntent(playerIndex, ClaimType.PASS)
        }
    }

    private fun shouldClaimKong(
        hand: List<MahjongTile>,
        kongClaim: ClaimIntent,
        currentRevealedCount: Int,
        currentShanten: Int,
        posture: TacticalPosture
    ): Boolean {
        // Expert Kong Discipline:
        // Do not call open Kong when folding or when hand is far from ready (shanten > 2),
        // as open Kong locks 4 tiles and shrinks defensive discard flexibility.
        if (posture.type == PostureType.DEFENSIVE_FOLD || currentShanten > 2) return false
        val remainingHand = hand.toMutableList()
        kongClaim.tilesToMelded.forEach { remainingHand.remove(it) }
        val newShanten = TingCalculator.calculateExactShanten(remainingHand, currentRevealedCount + 1)
        return newShanten <= currentShanten
    }

    private fun shouldClaimPung(
        hand: List<MahjongTile>,
        targetTile: MahjongTile,
        pungClaim: ClaimIntent,
        currentRevealedCount: Int,
        currentShanten: Int,
        currentUtility: Double,
        visibleBoardTiles: List<MahjongTile>,
        seatWind: HonorType,
        prevalentWind: HonorType,
        hasRevealedChow: Boolean = false,
        posture: TacticalPosture
    ): Boolean {
        if (posture.type == PostureType.DEFENSIVE_FOLD) return false

        val handAfterMeld = hand.toMutableList()
        pungClaim.tilesToMelded.forEach { handAfterMeld.remove(it) }
        if (handAfterMeld.isEmpty()) return false

        val bestFollowUp = expectimaxEngine.findOptimalDiscard(
            currentHand = handAfterMeld,
            revealedMeldsCount = currentRevealedCount + 1,
            visibleBoardTiles = visibleBoardTiles + pungClaim.tilesToMelded,
            seatWind = seatWind,
            prevalentWind = prevalentWind,
            hasRevealedChow = hasRevealedChow
        )

        val handAfterFollowUpDiscard = handAfterMeld.toMutableList().apply {
            remove(bestFollowUp.bestTileToDiscard)
        }
        val newShanten = TingCalculator.calculateExactShanten(handAfterFollowUpDiscard, currentRevealedCount + 1)

        val isValueHonor = targetTile.suit == Suit.HONOR && (
            targetTile.honorType in listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE) ||
                targetTile.honorType == seatWind ||
                targetTile.honorType == prevalentWind
            )

        // Value-seeking: Prioritize All-Pungs (3 Fan) trajectory (only valid if no revealed chows)
        val nonBonusTiles = hand.filterNot { it.isBonus }
        val counts = nonBonusTiles.groupingBy { it.suit to it.value }.eachCount()
        val pairOrPungCount = counts.values.count { it >= 2 }
        val isAllPungsPath = !hasRevealedChow && pairOrPungCount >= 3

        // Dragon Aggression: Dragon pungs are unconditionally claimed if holding a pair
        val isDragon = targetTile.suit == Suit.HONOR && targetTile.honorType in listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)

        // Flush Path: Pung matches the dominant flush suit or honor
        val suitCounts = nonBonusTiles.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
        val dominantSuit = suitCounts.maxByOrNull { it.value }?.key
        val dominantSuitCount = suitCounts[dominantSuit] ?: 0
        val isFlushSuit = dominantSuit != null && dominantSuitCount >= 6 && targetTile.suit == dominantSuit

        val honorTiles = nonBonusTiles.filter { it.suit == Suit.HONOR }
        val honorGroups = honorTiles.groupBy { it.honorType }
        val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
        val hasValueHonorInHand = dragonTypes.any { (honorGroups[it]?.size ?: 0) >= 2 } ||
            ((honorGroups[seatWind]?.size ?: 0) >= 2) ||
            ((honorGroups[prevalentWind]?.size ?: 0) >= 2)

        // Zero-Fan trap guard for ordinary suited Pungs:
        // Do not claim an off-suit normal Pung if it breaks concealed hand without an All-Pungs or Flush or Value-Honor path
        if (!isValueHonor && !hasValueHonorInHand && !isAllPungsPath && !isFlushSuit) {
            return false
        }

        return when {
            newShanten < currentShanten -> true
            isDragon && newShanten <= currentShanten + 1 -> true // Greedily capture Dragon Pung
            isValueHonor && newShanten <= currentShanten -> true
            isAllPungsPath && newShanten <= currentShanten -> true
            newShanten == currentShanten && rlDecisionEngine.shouldAcceptClaim(
                baseUtilityBeforeClaim = currentUtility,
                projectedUtilityAfterClaim = bestFollowUp.expectedUtility,
                claimThreshold = posture.pungClaimThreshold
            ) -> true
            else -> false
        }
    }

    private fun selectBestStrategicChow(
        playerIndex: Int,
        hand: List<MahjongTile>,
        targetTile: MahjongTile,
        chowClaims: List<ClaimIntent>,
        currentRevealedCount: Int,
        currentShanten: Int,
        currentUtility: Double,
        visibleBoardTiles: List<MahjongTile>,
        visibleCounts: Map<Pair<Suit, Int>, Int>,
        seatWind: HonorType,
        prevalentWind: HonorType,
        posture: TacticalPosture
    ): ClaimIntent {
        if (posture.type == PostureType.DEFENSIVE_FOLD) {
            return ClaimIntent(playerIndex, ClaimType.PASS)
        }

        val nonBonusTiles = hand.filterNot { it.isBonus }

        // Strategic Protection 1: Never break an All-Pungs (3 Fan) or Seven Pairs (4 Fan) hand by chowing
        val counts = nonBonusTiles.groupingBy { it.suit to it.value }.eachCount()
        val pairOrPungCount = counts.values.count { it >= 2 }
        if (pairOrPungCount >= 3) {
            return ClaimIntent(playerIndex, ClaimType.PASS)
        }

        // Strategic Protection 3: Flush Protection & Zero-Fan Trap Avoidance
        val suitCounts = nonBonusTiles.filter { it.isSuitTile }.groupingBy { it.suit }.eachCount()
        val dominantSuit = suitCounts.maxByOrNull { it.value }?.key
        val dominantSuitCount = suitCounts[dominantSuit] ?: 0
        val totalSuitTiles = suitCounts.values.sum()
        val honorTiles = nonBonusTiles.filter { it.suit == Suit.HONOR }
        val honorGroups = honorTiles.groupBy { it.honorType }

        val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
        val valueHonorPungsOrPairs = dragonTypes.count { (honorGroups[it]?.size ?: 0) >= 2 } +
            (if ((honorGroups[seatWind]?.size ?: 0) >= 2) 1 else 0) +
            (if (prevalentWind != seatWind && (honorGroups[prevalentWind]?.size ?: 0) >= 2) 1 else 0)

        val isCommittedFlush = dominantSuit != null && dominantSuitCount >= 7 && (dominantSuitCount.toDouble() / totalSuitTiles.coerceAtLeast(1)) >= 0.70

        // Zero-Fan Trap Guard:
        // In Hong Kong Mahjong, an open hand with mixed suits and no value honors
        // CANNOT achieve 1 Fan under AI_MIN_FAN_REQUIRED = 1 unless it is Ping Wu (All Chows).
        // A hand qualifies for 1+ Fan if:
        // 1. Committed Flush in the chow's suit (Full or Half Flush -> 3 to 7 Fan)
        // 2. Holds at least 1 value honor pair/pung (Dragon, Seat Wind, Prevalent Wind -> 1 Fan)
        // 3. Ping Wu (All Chows) candidate: no honor tiles in hand (honorTiles.isEmpty()) -> 1 Fan
        val isPingWuCandidate = honorTiles.isEmpty()
        val hasLegitimate1FanPath = isCommittedFlush || valueHonorPungsOrPairs >= 1 || isPingWuCandidate
        if (!hasLegitimate1FanPath) {
            return ClaimIntent(playerIndex, ClaimType.PASS)
        }

        val currentAcceptance = TingCalculator.calculateLiveTileAcceptance(
            hand = nonBonusTiles,
            revealedMeldsCount = currentRevealedCount,
            visibleCounts = visibleCounts
        )

        var bestClaim: ClaimIntent? = null
        var bestPostChowUtility = Double.NEGATIVE_INFINITY
        var bestPostChowShanten = Int.MAX_VALUE
        var bestPostChowAcceptance = 0

        for (chow in chowClaims) {
            val chowSuit = chow.tilesToMelded.firstOrNull()?.suit
            if (isCommittedFlush && chowSuit != dominantSuit) {
                // Reject off-suit chow to protect high-paying flush
                continue
            }

            val handAfterMeld = hand.toMutableList()
            chow.tilesToMelded.forEach { handAfterMeld.remove(it) }
            if (handAfterMeld.isEmpty()) continue

            val followUpResult = expectimaxEngine.findOptimalDiscard(
                currentHand = handAfterMeld,
                revealedMeldsCount = currentRevealedCount + 1,
                visibleBoardTiles = visibleBoardTiles + chow.tilesToMelded,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = true
            )

            val discardAfterChow = followUpResult.bestTileToDiscard

            // Kuikae (Swap-Calling) Prohibition:
            // Never call a Chow only to discard the exact same tile (e.g. calling 2 with 3-4 and discarding 2)
            // or the opposite suji end of the exact same completed run (calling 2 with 3-4 and discarding 5).
            val meldValues = (chow.tilesToMelded.map { it.value } + targetTile.value).sorted()
            val minMeldVal = meldValues.firstOrNull() ?: 0
            val maxMeldVal = meldValues.lastOrNull() ?: 0
            val isSameTileSwap = discardAfterChow.suit == targetTile.suit && discardAfterChow.value == targetTile.value
            val isSlideSwap = discardAfterChow.suit == targetTile.suit && (
                (targetTile.value == minMeldVal && discardAfterChow.value == maxMeldVal + 1) ||
                    (targetTile.value == maxMeldVal && discardAfterChow.value == minMeldVal - 1)
                )
            if (isSameTileSwap || isSlideSwap) {
                continue
            }

            val handAfterDiscard = handAfterMeld.toMutableList().apply {
                remove(discardAfterChow)
            }
            val newShanten = TingCalculator.calculateExactShanten(handAfterDiscard, currentRevealedCount + 1)
            val postChowAcceptance = TingCalculator.calculateLiveTileAcceptance(
                hand = handAfterDiscard,
                revealedMeldsCount = currentRevealedCount + 1,
                visibleCounts = visibleCounts
            )

            if (followUpResult.expectedUtility > bestPostChowUtility) {
                bestPostChowUtility = followUpResult.expectedUtility
                bestPostChowShanten = newShanten
                bestPostChowAcceptance = postChowAcceptance
                bestClaim = chow
            }
        }

        if (bestClaim != null) {
            val improvesShanten = bestPostChowShanten < currentShanten
            // Same-Shanten Chow Upgrade Filter:
            // Only accept a same-shanten Chow if it genuinely widens live Ukeire acceptance
            // (e.g., upgrading a 4-tile Kanchan/Penchan wait to an 8-tile Ryanmen wait).
            val upgradesWaitQuality = bestPostChowShanten == currentShanten &&
                bestPostChowAcceptance > currentAcceptance + 2 &&
                rlDecisionEngine.shouldAcceptClaim(
                    baseUtilityBeforeClaim = currentUtility,
                    projectedUtilityAfterClaim = bestPostChowUtility,
                    claimThreshold = posture.chowClaimThreshold
                )
            if (improvesShanten || upgradesWaitQuality) {
                Logger.d(tag, "AI Player $playerIndex (Posture: ${posture.type.name}) accepting Chow: improvesShanten=$improvesShanten, ukeire=$currentAcceptance->$bestPostChowAcceptance, utilityGain=${bestPostChowUtility - currentUtility}")
                return bestClaim
            }
        }

        return ClaimIntent(playerIndex, ClaimType.PASS)
    }

    override suspend fun onExit(machine: GameStateMachine) {
        Logger.d(tag, "ClaimInterceptionState complete. Resuming PlayingState.")
        machine.emitUiEffect(UiEffect.ClearHumanClaim)
        claimMutex.withLock {
            submittedIntents.clear()
        }
    }
}
