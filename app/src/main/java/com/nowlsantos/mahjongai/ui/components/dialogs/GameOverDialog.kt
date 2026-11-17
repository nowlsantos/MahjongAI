package com.nowlsantos.mahjongai.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nowlsantos.mahjongai.model.GameOverData
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.CoolOrange
import com.nowlsantos.mahjongai.ui.theme.CrimsonRed
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.ui.theme.PureWhite
import com.nowlsantos.mahjongai.ui.theme.SubtleGreen
import com.nowlsantos.mahjongai.util.GameConstants

@Composable
fun GameOverDialog(
    gameState: GameState,
    gameOverData: GameOverData? = null,
    onViewResultsClick: () -> Unit = {},
    onRestartClick: () -> Unit
) {
    val rankedPlayers = gameOverData?.rankedPlayers?.ifEmpty { null }
        ?: gameState.players.values.sortedByDescending { it.score }

    val winner = rankedPlayers.firstOrNull()
    val bankruptPlayer = rankedPlayers.lastOrNull { it.score <= 0 }

    Dialog(
        onDismissRequest = { /* Modal dialog: dismissed only via action buttons */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SetDialogDim(dimAmount = 0.20f)

        Card(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 540.dp)
                .padding(16.dp)
                .border(2.dp, BrightYellow, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF5161616)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Header Trophy & Title
                Text(
                    text = "GAME OVER",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrightYellow,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "對局結束 · 破產結算",
                    fontSize = 15.sp,
                    color = PureWhite.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                HorizontalDivider(color = DarkGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // 2. Winner Announcement Card
                winner?.let { winPlayer ->
                    val isHumanWinner = winPlayer.playerId == 0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, BrightYellow, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF6F00))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "MATCH WINNER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightYellow,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHumanWinner) "${winPlayer.playerName} (You)" else winPlayer.playerName,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PureWhite
                            )
                            Text(
                                text = "${winPlayer.seat.name} Seat  •  ${winPlayer.score} pts",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrightYellow,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // 3. Bankruptcy Callout
                if (bankruptPlayer != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x2EFC2424))
                            .border(1.dp, CrimsonRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${bankruptPlayer.playerName} has gone bankrupt (0 pts)!",
                            color = CrimsonRed,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 4. Final Standings Leaderboard
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Final Leaderboard",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                rankedPlayers.forEachIndexed { index, player ->
                    val isFirst = index == 0
                    val isBankrupt = player.score <= 0
                    val isHuman = player.playerId == 0
                    val medal = when (index) {
                        0 -> "1st"
                        1 -> "2nd"
                        2 -> "3rd"
                        else -> "4th"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFirst -> Color(0x26E8F503)
                                    isHuman -> Color(0x22FF6F00)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = medal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isFirst) BrightYellow else if (isBankrupt) CrimsonRed else PureWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${player.playerName}${if (isHuman) " (You)" else ""} • ${player.seat.name}",
                                color = if (isFirst) BrightYellow else PureWhite,
                                fontWeight = if (isFirst || isHuman) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "${player.score} pts",
                            color = if (player.score > GameConstants.STARTING_POINTS) SubtleGreen else if (isBankrupt) CrimsonRed else PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                // 5. Action Buttons
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewResultsClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite)
                    ) {
                        Text(
                            text = "Ledger",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }

                    Button(
                        onClick = onRestartClick,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoolOrange)
                    ) {
                        Text(
                            text = "Play Again ➔",
                            color = FlatBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }
            }
        }
    }
}
