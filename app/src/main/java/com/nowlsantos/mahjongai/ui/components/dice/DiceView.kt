package com.nowlsantos.mahjongai.ui.components.dice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nowlsantos.mahjongai.R
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.UIState
import com.nowlsantos.mahjongai.presentation.DiceResult
import com.nowlsantos.mahjongai.presentation.DiceRoller
import com.nowlsantos.mahjongai.ui.controls.ControlButton
import com.nowlsantos.mahjongai.ui.theme.CoolOrange
import com.nowlsantos.mahjongai.ui.theme.CrimsonRed

@Composable
fun DiceView(
    gameState: GameState,
    uiState: UIState,
    onRollDealerFinished: (DiceResult) -> Unit,
    onRollTargetWallFinished: (DiceResult) -> Unit,
    onRollBreakFinished: (DiceResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var localDice by remember { mutableStateOf<DiceResult?>(null) }
        var isRolling by remember { mutableStateOf(false) }
        var pendingFinishCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

        val currentDice = localDice ?: gameState.secondRollResult ?: gameState.firstRollResult
        val buttonWidth = 250.dp

        Box(
            modifier = Modifier
                .weight(3f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Dice3DPair(
                die1Value = currentDice?.die1 ?: 1,
                die2Value = currentDice?.die2 ?: 1,
                isRolling = isRolling,
                diceSize = 64.dp,
                rollDurationMillis = 850,
                onRollFinished = {
                    isRolling = false
                    val callback = pendingFinishCallback
                    pendingFinishCallback = null
                    callback?.invoke()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (gameState.firstRollResult == null && !isRolling) {
                val buttonText = if (gameState.roundNumber <= 1) {
                    stringResource(R.string.roll_for_dealer)
                } else {
                    stringResource(R.string.roll_for_target_wall)
                }
                ControlButton(
                    modifier = Modifier.width(buttonWidth),
                    color = CrimsonRed,
                    name = buttonText,
                    enabled = !isRolling,
                    onClick = {
                        if (!isRolling) {
                            val roll = DiceRoller.rollTwoDice()
                            localDice = roll
                            pendingFinishCallback = {
                                if (gameState.roundNumber <= 1) {
                                    onRollDealerFinished(roll)
                                } else {
                                    onRollTargetWallFinished(roll)
                                }
                            }
                            isRolling = true
                        }
                    }
                )
            } else if (gameState.secondRollResult == null && !isRolling) {
                ControlButton(
                    modifier = Modifier.width(buttonWidth),
                    color = CoolOrange,
                    name = stringResource(R.string.roll_to_break_wall),
                    enabled = !isRolling,
                    onClick = {
                        if (!isRolling) {
                            val roll = DiceRoller.rollTwoDice()
                            localDice = roll
                            pendingFinishCallback = { onRollBreakFinished(roll) }
                            isRolling = true
                        }
                    }
                )
            }
        }
    }
}
