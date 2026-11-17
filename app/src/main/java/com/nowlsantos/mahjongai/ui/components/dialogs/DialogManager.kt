package com.nowlsantos.mahjongai.ui.components.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.model.GameState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Unified dialog coordinator for MahjongTable.
 * Manages visibility, transitions, and delays between RoundEndDialog,
 * GameResultsDialog, and GameOverDialog.
 */
@Composable
fun DialogManager(
    gameState: GameState,
    onStartNextRound: () -> Unit,
    onRestartMatch: () -> Unit
) {
    var showGameResultsDialog by remember { mutableStateOf(false) }
    var delayedGameOverTriggered by remember { mutableStateOf(false) }

    val hasBankruptPlayer = remember(gameState.players) {
        gameState.players.values.any { it.score <= 0 }
    }

    // Reset internal dialog navigation states upon entering SETUP phase
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.SETUP) {
            showGameResultsDialog = false
            delayedGameOverTriggered = false
        }
    }

    // When a player is bankrupt at round end, reveal the GameOverDialog after a couple of seconds (2500ms)
    LaunchedEffect(gameState.gamePhase, hasBankruptPlayer) {
        if (hasBankruptPlayer && gameState.gamePhase == GamePhase.ROUND_END) {
            delay(2500.milliseconds)
            delayedGameOverTriggered = true
        }
    }

    val isGameOver = gameState.gamePhase == GamePhase.GAME_OVER ||
            (hasBankruptPlayer && delayedGameOverTriggered) ||
            (hasBankruptPlayer && gameState.gameOverData != null)
    val isRoundEnd = gameState.gamePhase == GamePhase.ROUND_END

    if (isGameOver) {
        if (showGameResultsDialog) {
            GameResultsDialog(
                gameState = gameState,
                gameOverData = gameState.gameOverData,
                onNextRoundClick = {
                    showGameResultsDialog = false
                },
                onRestartClick = {
                    showGameResultsDialog = false
                    onRestartMatch()
                }
            )
        } else {
            GameOverDialog(
                gameState = gameState,
                gameOverData = gameState.gameOverData,
                onViewResultsClick = {
                    showGameResultsDialog = true
                },
                onRestartClick = {
                    showGameResultsDialog = false
                    onRestartMatch()
                }
            )
        }
    } else if (isRoundEnd) {
        if (showGameResultsDialog) {
            GameResultsDialog(
                gameState = gameState,
                gameOverData = null,
                onNextRoundClick = {
                    showGameResultsDialog = false
                    onStartNextRound()
                },
                onRestartClick = {
                    showGameResultsDialog = false
                    onRestartMatch()
                }
            )
        } else {
            RoundEndDialog(
                gameState = gameState,
                roundEndData = gameState.roundEndData,
                gameOverData = null,
                onNextRoundClick = {
                    showGameResultsDialog = true
                },
                onRestartClick = {
                    showGameResultsDialog = true
                }
            )
        }
    }
}
