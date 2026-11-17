package com.nowlsantos.mahjongai.engine.rl

import com.nowlsantos.mahjongai.engine.ExpectimaxEngine
import com.nowlsantos.mahjongai.engine.defense.SafetyTileAnalyzer
import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimResolver
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.engine.logic.TingCalculator
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.FlowerType
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.PlayerState
import com.nowlsantos.mahjongai.model.SeasonType
import com.nowlsantos.mahjongai.model.Suit
import com.nowlsantos.mahjongai.util.GameConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

data class StepRecord(
    val playerId: Int,
    val features: DoubleArray,
    val intermediateReward: Double
)

data class SelfPlayEpisodeResult(
    val winnerIndex: Int?,
    val discarderIndex: Int?,
    val isSelfDraw: Boolean,
    val isDraw: Boolean,
    val winningFan: Int,
    val trajectories: Map<Int, List<StepRecord>>,
    val terminalRewards: Map<Int, Double>
)

data class TrainingSummary(
    val totalEpisodes: Int,
    val totalWins: Int,
    val selfDrawWins: Int,
    val ronWins: Int,
    val draws: Int,
    val avgWinningFan: Double,
    val initialAvgLoss: Double,
    val finalAvgLoss: Double,
    val avgEpisodeReturn: Double,
    val elapsedMs: Long
)

/**
 * High-speed, multi-core, zero-UI Headless Self-Play Simulator & Reinforcement Learning Trainer.
 * Simulates complete 4-player Hong Kong Mahjong rounds in parallel across CPU cores and trains
 * [MahjongNeuralNet] using Monte Carlo / TD(lambda) returns with Potential-Based Reward Shaping
 * and Oracle-Guided danger signals.
 */
class HeadlessSelfPlayTrainer(
    val neuralNet: MahjongNeuralNet = MahjongNeuralNet.getSharedInstance()
) {

    fun runTrainingSession(
        numEpisodes: Int = 10000,
        gamma: Double = 0.96,
        initialLearningRate: Double = 0.0005,
        seed: Long = 2026L,
        batchSize: Int = 64,
        onProgress: ((Int, TrainingSummary) -> Unit)? = null
    ): TrainingSummary = runBlocking(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        var completedEpisodes = 0
        var wins = 0
        var tsumoCount = 0
        var ronCount = 0
        var drawCount = 0
        var totalWinningFan = 0
        var totalReturn = 0.0

        var earlyLossSum = 0.0
        var earlyLossSteps = 0
        var lateLossSum = 0.0
        var lateLossSteps = 0

        while (completedEpisodes < numEpisodes) {
            val currentBatchSize = minOf(batchSize, numEpisodes - completedEpisodes)
            val progress = completedEpisodes.toDouble() / numEpisodes.coerceAtLeast(1)
            val lr = initialLearningRate * (1.0 - 0.70 * progress).coerceAtLeast(0.15)
            val oracleWeight = (0.35 * (1.0 - progress)).coerceAtLeast(0.0)

            // 1. Parallel Episode Rollouts across all CPU cores
            val batchResults = (0 until currentBatchSize).map { offset ->
                val epIndex = completedEpisodes + offset + 1
                async {
                    val episodeRng = Random(seed + epIndex * 9973L)
                    simulateEpisode(episodeRng, oracleWeight)
                }
            }.awaitAll()

            // 2. Synchronous Batch Weight Optimization
            for (i in batchResults.indices) {
                val ep = completedEpisodes + i + 1
                val episode = batchResults[i]

                if (episode.isDraw) {
                    drawCount++
                } else if (episode.winnerIndex != null) {
                    wins++
                    if (episode.isSelfDraw) tsumoCount++ else ronCount++
                    totalWinningFan += episode.winningFan
                }

                for (pid in 0..3) {
                    val steps = episode.trajectories[pid] ?: continue
                    val termReward = episode.terminalRewards[pid] ?: 0.0
                    totalReturn += termReward

                    var runningReturn = termReward
                    for (t in steps.indices.reversed()) {
                        val step = steps[t]
                        runningReturn = step.intermediateReward + gamma * runningReturn
                        val scaledTargetQ = runningReturn * 0.65
                        val loss = neuralNet.trainStep(step.features, scaledTargetQ, lr)

                        if (ep <= numEpisodes / 4) {
                            earlyLossSum += loss
                            earlyLossSteps++
                        } else if (ep > (numEpisodes * 3) / 4) {
                            lateLossSum += loss
                            lateLossSteps++
                        }
                    }
                }
            }

            completedEpisodes += currentBatchSize

            if (onProgress != null && (completedEpisodes % 500 == 0 || completedEpisodes == numEpisodes)) {
                val summary = TrainingSummary(
                    totalEpisodes = completedEpisodes,
                    totalWins = wins,
                    selfDrawWins = tsumoCount,
                    ronWins = ronCount,
                    draws = drawCount,
                    avgWinningFan = if (wins > 0) totalWinningFan.toDouble() / wins else 0.0,
                    initialAvgLoss = if (earlyLossSteps > 0) earlyLossSum / earlyLossSteps else 0.0,
                    finalAvgLoss = if (lateLossSteps > 0) lateLossSum / lateLossSteps else (if (earlyLossSteps > 0) earlyLossSum / earlyLossSteps else 0.0),
                    avgEpisodeReturn = totalReturn / (completedEpisodes * 4.0),
                    elapsedMs = System.currentTimeMillis() - startTime
                )
                onProgress(completedEpisodes, summary)
            }
        }

        neuralNet.gamesTrained += numEpisodes

        TrainingSummary(
            totalEpisodes = numEpisodes,
            totalWins = wins,
            selfDrawWins = tsumoCount,
            ronWins = ronCount,
            draws = drawCount,
            avgWinningFan = if (wins > 0) totalWinningFan.toDouble() / wins else 0.0,
            initialAvgLoss = if (earlyLossSteps > 0) earlyLossSum / earlyLossSteps else 0.0,
            finalAvgLoss = if (lateLossSteps > 0) lateLossSum / lateLossSteps else 0.0,
            avgEpisodeReturn = totalReturn / (numEpisodes * 4.0),
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    fun simulateEpisode(rng: Random, oracleWeight: Double = 0.0): SelfPlayEpisodeResult {
        val localExpectimax = ExpectimaxEngine()
        val localSafety = SafetyTileAnalyzer()
        val rlEngine = RlDecisionEngine(localExpectimax, localSafety, neuralNet)

        val deck = buildStandardDeck().shuffled(rng).toMutableList()
        val prevalentWind = HonorType.EAST

        val players = (0..3).associateWith { pid ->
            PlayerState(
                playerId = pid,
                playerName = "Agent-$pid",
                isAi = true,
                seat = PlayerState.getMahjongSeat(0, pid),
                score = GameConstants.STARTING_POINTS
            )
        }.toMutableMap()

        // Deal 13 tiles to each player, handling bonus tiles immediately
        for (pid in 0..3) {
            val hand = mutableListOf<MahjongTile>()
            val bonuses = mutableListOf<MahjongTile>()
            while (hand.size < 13 && deck.isNotEmpty()) {
                val tile = deck.removeAt(0)
                if (tile.isBonus) bonuses.add(tile) else hand.add(tile)
            }
            players[pid] = players[pid]!!.copy(hand = hand, bonusTiles = bonuses)
        }

        val publicDiscards = mutableListOf<MahjongTile>()
        val trajectories = (0..3).associateWith { mutableListOf<StepRecord>() }
        var activePlayer = 0

        // Live wall leaves 14 tiles for the dead wall
        while (deck.size > 14) {
            val pState = players[activePlayer]!!
            val seatWind = getSeatWind(activePlayer)

            // 1. Draw tile (replacing bonus tiles from tail)
            var drawnTile: MahjongTile? = null
            val updatedBonuses = pState.bonusTiles.toMutableList()
            while (deck.size > 14) {
                val candidate = deck.removeAt(0)
                if (candidate.isBonus) {
                    updatedBonuses.add(candidate)
                } else {
                    drawnTile = candidate
                    break
                }
            }

            if (drawnTile == null) break

            val handWithDraw = pState.hand + drawnTile
            players[activePlayer] = pState.copy(hand = handWithDraw, bonusTiles = updatedBonuses)

            // 2. Check Self-Draw Win (Tsumo)
            val legalActions = MoveGenerator.getLegalTurnActions(
                playerHand = handWithDraw,
                revealedMelds = pState.revealedMelds,
                bonusTiles = updatedBonuses,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                minFanRequired = GameConstants.AI_MIN_FAN_REQUIRED
            )

            val selfDrawWin = legalActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
            if (selfDrawWin != null) {
                val score = FanCalculator.calculateFan(
                    hand = handWithDraw,
                    revealedMelds = pState.revealedMelds,
                    bonusTiles = updatedBonuses,
                    isSelfDraw = true,
                    seatWind = seatWind,
                    prevalentWind = prevalentWind
                )
                if (score.totalFan >= GameConstants.AI_MIN_FAN_REQUIRED) {
                    val termRewards = (0..3).associateWith { pid ->
                        RewardCalculator.calculateTerminalReward(
                            isWinner = pid == activePlayer,
                            isSelfDraw = true,
                            isDealtInto = false,
                            scoringResult = score
                        )
                    }
                    return SelfPlayEpisodeResult(
                        winnerIndex = activePlayer,
                        discarderIndex = null,
                        isSelfDraw = true,
                        isDraw = false,
                        winningFan = score.totalFan,
                        trajectories = trajectories,
                        terminalRewards = termRewards
                    )
                }
            }

            // 3. Evaluate Discard Decision
            val allRevealedMeldTiles = players.values.flatMap { it.revealedMelds }.flatMap { it.tiles }
            val visibleBoardTiles = publicDiscards + allRevealedMeldTiles
            val opponents = players.values.filter { it.playerId != activePlayer }
            val isOpponentInTing = opponents.any { it.isTing }
            val maxOpponentMelds = opponents.maxOfOrNull { it.revealedMelds.size } ?: 0
            val currentShanten = TingCalculator.calculateExactShanten(handWithDraw, pState.revealedMelds.size)

            val posture = TacticalPosture.evaluate(
                hand = handWithDraw,
                currentShanten = currentShanten,
                maxOpponentMelds = maxOpponentMelds,
                isAnyOpponentInTing = isOpponentInTing,
                wallRemaining = deck.size - 14,
                playerScore = pState.score
            )

            val decision = rlEngine.selectOptimalDiscard(
                currentHand = handWithDraw,
                revealedMeldsCount = pState.revealedMelds.size,
                visibleBoardTiles = visibleBoardTiles,
                posture = posture,
                seatWind = seatWind,
                prevalentWind = prevalentWind,
                hasRevealedChow = pState.revealedMelds.any { it.type == MeldType.CHOW },
                isOpponentInTing = isOpponentInTing,
                opponents = opponents,
                wallRemaining = deck.size - 14,
                playerScore = pState.score
            )

            val discardedTile = decision.tileToDiscard

            // Oracle-Guided penalty during training if discarded tile matches an opponent's 0-shanten wait
            var stepReward = decision.intermediateReward
            if (oracleWeight > 0.0) {
                for (opp in opponents) {
                    if (opp.isTing) {
                        val oppWaitShanten = TingCalculator.calculateExactShanten(opp.hand + discardedTile, opp.revealedMelds.size)
                        if (oppWaitShanten == -1) {
                            stepReward -= 25.0 * oracleWeight
                        }
                    }
                }
            }

            if (decision.features.isNotEmpty()) {
                trajectories[activePlayer]!!.add(
                    StepRecord(
                        playerId = activePlayer,
                        features = decision.features,
                        intermediateReward = stepReward
                    )
                )
            }

            val handAfterDiscard = handWithDraw.toMutableList().apply { remove(discardedTile) }
            val newIsTing = decision.resultingShanten <= 0
            players[activePlayer] = players[activePlayer]!!.copy(
                hand = handAfterDiscard,
                discards = players[activePlayer]!!.discards + discardedTile,
                isTing = newIsTing
            )
            publicDiscards.add(discardedTile)

            // 4. Evaluate Claims (Ron / Pung) from the other 3 players
            val claimIntents = mutableListOf<ClaimIntent>()
            for (offset in 1..3) {
                val claimerId = (activePlayer + offset) % 4
                val claimer = players[claimerId]!!
                val claimerWind = getSeatWind(claimerId)
                val legalClaims = MoveGenerator.getLegalClaims(
                    playerIndex = claimerId,
                    playerHand = claimer.hand,
                    revealedMelds = claimer.revealedMelds,
                    bonusTiles = claimer.bonusTiles,
                    discarderIndex = activePlayer,
                    discardedTile = discardedTile,
                    seatWind = claimerWind,
                    prevalentWind = prevalentWind,
                    minFanRequired = GameConstants.AI_MIN_FAN_REQUIRED
                )

                val winClaim = legalClaims.find { it.claimType == ClaimType.WIN }
                if (winClaim != null) {
                    val fanScore = FanCalculator.calculateFan(
                        hand = claimer.hand + discardedTile,
                        revealedMelds = claimer.revealedMelds,
                        bonusTiles = claimer.bonusTiles,
                        isSelfDraw = false,
                        seatWind = claimerWind,
                        prevalentWind = prevalentWind
                    )
                    if (fanScore.totalFan >= GameConstants.AI_MIN_FAN_REQUIRED) {
                        claimIntents.add(winClaim)
                        continue
                    }
                }

                // Pung check (if improves shanten and holds valid Fan path)
                val pungClaim = legalClaims.find { it.claimType == ClaimType.PUNG }
                if (pungClaim != null) {
                    val cShanten = TingCalculator.calculateExactShanten(claimer.hand, claimer.revealedMelds.size)
                    val rem = claimer.hand.toMutableList()
                    pungClaim.tilesToMelded.forEach { rem.remove(it) }
                    if (rem.isNotEmpty()) {
                        val nextShanten = TingCalculator.calculateExactShanten(rem.drop(1), claimer.revealedMelds.size + 1)
                        val isValueHonor = discardedTile.suit == Suit.HONOR && (
                            discardedTile.honorType in listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE) ||
                                discardedTile.honorType == claimerWind ||
                                discardedTile.honorType == prevalentWind
                            )
                        if (isValueHonor || nextShanten < cShanten) {
                            claimIntents.add(pungClaim)
                        }
                    }
                }
            }

            val winningClaim = ClaimResolver.resolveHighestClaim(activePlayer, claimIntents)
            if (winningClaim != null && winningClaim.claimType == ClaimType.WIN) {
                val winnerId = winningClaim.playerIndex
                val winner = players[winnerId]!!
                val score = FanCalculator.calculateFan(
                    hand = winner.hand + discardedTile,
                    revealedMelds = winner.revealedMelds,
                    bonusTiles = winner.bonusTiles,
                    isSelfDraw = false,
                    seatWind = getSeatWind(winnerId),
                    prevalentWind = prevalentWind
                )
                val termRewards = (0..3).associateWith { pid ->
                    RewardCalculator.calculateTerminalReward(
                        isWinner = pid == winnerId,
                        isSelfDraw = false,
                        isDealtInto = pid == activePlayer,
                        scoringResult = score
                    )
                }
                return SelfPlayEpisodeResult(
                    winnerIndex = winnerId,
                    discarderIndex = activePlayer,
                    isSelfDraw = false,
                    isDraw = false,
                    winningFan = score.totalFan,
                    trajectories = trajectories,
                    terminalRewards = termRewards
                )
            } else if (winningClaim != null && winningClaim.claimType == ClaimType.PUNG) {
                val claimerId = winningClaim.playerIndex
                val claimer = players[claimerId]!!
                val handAfterMeld = claimer.hand.toMutableList()
                winningClaim.tilesToMelded.forEach { handAfterMeld.remove(it) }
                val newMeld = Meld(MeldType.PUNG, winningClaim.tilesToMelded + discardedTile)
                publicDiscards.removeLastOrNull()

                if (handAfterMeld.isNotEmpty()) {
                    val followUp = rlEngine.selectOptimalDiscard(
                        currentHand = handAfterMeld,
                        revealedMeldsCount = claimer.revealedMelds.size + 1,
                        visibleBoardTiles = publicDiscards,
                        seatWind = getSeatWind(claimerId),
                        prevalentWind = prevalentWind,
                        hasRevealedChow = claimer.revealedMelds.any { it.type == MeldType.CHOW },
                        opponents = players.values.filter { it.playerId != claimerId },
                        wallRemaining = deck.size - 14,
                        playerScore = claimer.score
                    )
                    handAfterMeld.remove(followUp.tileToDiscard)
                    publicDiscards.add(followUp.tileToDiscard)
                    players[claimerId] = claimer.copy(
                        hand = handAfterMeld,
                        revealedMelds = claimer.revealedMelds + newMeld,
                        discards = claimer.discards + followUp.tileToDiscard,
                        isTing = followUp.resultingShanten <= 0
                    )
                }
                activePlayer = (claimerId + 1) % 4
            } else {
                activePlayer = (activePlayer + 1) % 4
            }
        }

        // Exhaustive Draw (Lau Kuk)
        val drawRewards = (0..3).associateWith { pid ->
            val isReady = players[pid]?.isTing == true
            if (isReady) 8.0 else -4.0
        }
        return SelfPlayEpisodeResult(
            winnerIndex = null,
            discarderIndex = null,
            isSelfDraw = false,
            isDraw = true,
            winningFan = 0,
            trajectories = trajectories,
            terminalRewards = drawRewards
        )
    }

    private fun getSeatWind(playerId: Int): HonorType = when (playerId) {
        0 -> HonorType.EAST
        1 -> HonorType.SOUTH
        2 -> HonorType.WEST
        else -> HonorType.NORTH
    }

    companion object {
        fun buildStandardDeck(): List<MahjongTile> {
            val deck = mutableListOf<MahjongTile>()
            var nextId = 0
            for (suit in listOf(Suit.CHARACTER, Suit.BAMBOO, Suit.DOT)) {
                for (value in 1..9) {
                    repeat(4) {
                        deck.add(MahjongTile(id = nextId++, suit = suit, value = value))
                    }
                }
            }
            for (honor in HonorType.entries) {
                repeat(4) {
                    deck.add(MahjongTile(id = nextId++, suit = Suit.HONOR, value = honor.ordinal + 1, honorType = honor))
                }
            }
            for (flower in FlowerType.entries) {
                deck.add(MahjongTile(id = nextId++, suit = Suit.FLOWER, value = flower.ordinal + 1, flowerType = flower))
            }
            for (season in SeasonType.entries) {
                deck.add(MahjongTile(id = nextId++, suit = Suit.SEASON, value = season.ordinal + 1, seasonType = season))
            }
            return deck
        }
    }
}
