package com.nowlsantos.mahjongai.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nowlsantos.mahjongai.model.GameOverData
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.RoundRecord
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.CoolOrange
import com.nowlsantos.mahjongai.ui.theme.CrimsonRed
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.ui.theme.MatteGray
import com.nowlsantos.mahjongai.ui.theme.PureWhite
import com.nowlsantos.mahjongai.ui.theme.SubtleGreen
import com.nowlsantos.mahjongai.util.GameConstants

@Composable
fun GameResultsDialog(
    gameState: GameState,
    gameOverData: GameOverData? = null,
    onNextRoundClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    val isGameOver = gameOverData != null || gameState.gameOverData != null
    val players = (0..3).mapNotNull { gameState.players[it] }
    val roundHistory = gameState.roundHistory

    // Compute player rank positions based on current score
    val playerScores = (0..3).associateWith { pid -> gameState.players[pid]?.score ?: 25000 }
    val sortedPids = playerScores.entries.sortedByDescending { it.value }.map { it.key }
    val playerRanks = sortedPids.mapIndexed { rank, pid -> pid to (rank + 1) }.toMap()

    Dialog(
        onDismissRequest = { /* Modal: dismissed only via action button */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SetDialogDim(dimAmount = 0.20f)

        Card(
            modifier = Modifier
                .widthIn(min = 640.dp, max = 780.dp)
                .padding(16.dp)
                .border(2.dp, BrightYellow, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF5161616)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Title Header
                Text(
                    text = if (isGameOver) "MATCH RESULTS" else "GAME RESULTS",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrightYellow,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isGameOver) "全場總結算" else "對局結算表",
                    fontSize = 15.sp,
                    color = PureWhite.copy(alpha = 0.65f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = DarkGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // 2. Table Header: "Round" + 4 Player Names
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Color(0xFF242424))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell(text = "Round", weight = 1.0f)
                    players.forEach { p ->
                        val isHuman = p.playerId == 0
                        TableHeaderCell(
                            text = if (isHuman) "${p.playerName} (You)" else p.playerName,
                            weight = 1.25f,
                            isHighlighted = isHuman
                        )
                    }
                }

                // 3. Scrollable Middle Body: Round History Rows
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 400.dp)
                        .background(Color(0xFF1B1B1B))
                        .verticalScroll(rememberScrollState())
                ) {
                    if (roundHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No completed rounds yet",
                                color = MatteGray,
                                fontSize = 17.sp
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            roundHistory.forEachIndexed { index, record ->
                                val rowBg = if (index % 2 == 0) Color.Transparent else Color(0x0EFFFFFF)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(vertical = 7.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Round Label
                                    Text(
                                        text = "R${record.roundNumber}",
                                        color = PureWhite.copy(alpha = 0.85f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1.0f),
                                        textAlign = TextAlign.Center
                                    )

                                    // 4 Players delta columns
                                    (0..3).forEach { pid ->
                                        val delta = record.chipTransactions[pid] ?: 0
                                        val isWinner = record.winnerIndex == pid
                                        val deltaText = when {
                                            delta > 0 -> "+%,d".format(delta)
                                            delta < 0 -> "%,d".format(delta)
                                            else -> "-"
                                        }
                                        val deltaColor = when {
                                            isWinner -> SubtleGreen
                                            delta < 0 -> CrimsonRed
                                            else -> MatteGray
                                        }

                                        Text(
                                            text = deltaText,
                                            color = deltaColor,
                                            fontSize = 16.sp,
                                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.weight(1.25f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkGray, thickness = 1.dp)

                // 4. Fixed Bottom Row 1: "Wins / Losses"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222622))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wins / Deals",
                        color = BrightYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.0f),
                        textAlign = TextAlign.Center
                    )
                    (0..3).forEach { pid ->
                        val wins = roundHistory.count { it.winnerIndex == pid }
                        val losses = roundHistory.count { it.discarderIndex == pid && !it.isSelfDraw }
                        Text(
                            text = "${wins}W - ${losses}L",
                            color = if (wins > 0) PureWhite else MatteGray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.25f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                HorizontalDivider(color = DarkGray.copy(alpha = 0.5f), thickness = 1.dp)

                // 5. Fixed Bottom Row 2: "Current Points"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(Color(0xFF292929))
                        .padding(vertical = 9.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Pts",
                        color = BrightYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.0f),
                        textAlign = TextAlign.Center
                    )
                    (0..3).forEach { pid ->
                        val score = playerScores[pid] ?: GameConstants.STARTING_POINTS
                        val rank = playerRanks[pid] ?: 4
                        Text(
                            text = "%,d".format(score),
                            color = if (rank == 1) BrightYellow else PureWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1.25f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Action Button
                if (isGameOver) {
                    Button(
                        onClick = onRestartClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BrightYellow),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Play Again / Restart Match (重新開始)",
                            color = FlatBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onNextRoundClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CoolOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Next Round (下一局) ➔",
                            color = FlatBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    isHighlighted: Boolean = false
) {
    Text(
        text = text,
        color = if (isHighlighted) CoolOrange else PureWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
