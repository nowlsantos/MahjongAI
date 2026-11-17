package com.nowlsantos.mahjongai.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.GameOverData
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.RoundEndData
import com.nowlsantos.mahjongai.ui.components.TileView
import com.nowlsantos.mahjongai.ui.theme.BrightYellow
import com.nowlsantos.mahjongai.ui.theme.CoolOrange
import com.nowlsantos.mahjongai.ui.theme.CrimsonRed
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.ui.theme.PureWhite
import com.nowlsantos.mahjongai.ui.theme.SubtleGreen
import com.nowlsantos.mahjongai.util.GameConstants
import com.nowlsantos.mahjongai.util.TileOrientation

@Composable
fun RoundEndDialog(
    gameState: GameState,
    roundEndData: RoundEndData?,
    gameOverData: GameOverData?,
    onNextRoundClick: () -> Unit,
    onRestartClick: () -> Unit
) {
    if (roundEndData == null && gameOverData == null) return

    Dialog(
        onDismissRequest = { /* Modal dialog: dismissed only via action button */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SetDialogDim(dimAmount = 0.20f)

        Card(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 520.dp)
                .padding(16.dp)
                .border(2.dp, BrightYellow, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF5181818)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (gameOverData != null) {
                    // Match Over View
                    Text(
                        text = "MATCH COMPLETE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrightYellow,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "比賽結束",
                        fontSize = 16.sp,
                        color = PureWhite.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    HorizontalDivider(color = DarkGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    gameOverData.rankedPlayers.forEachIndexed { index, player ->
                        val medal = when (index) {
                            0 -> "1st"
                            1 -> "2nd"
                            2 -> "3rd"
                            else -> "4th"
                        }
                        val isHuman = player.playerId == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHuman) Color(0x33FF6F00) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$medal  ${player.playerName}",
                                color = if (index == 0) BrightYellow else PureWhite,
                                fontWeight = if (isHuman || index == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "${player.score} pts",
                                color = if (player.score >= GameConstants.STARTING_POINTS) SubtleGreen else CrimsonRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onRestartClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CoolOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Play Again (重新開始)",
                            color = FlatBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                } else if (roundEndData != null) {
                    // Single Round End View
                    if (roundEndData.isDraw) {
                        Text(
                            text = "EXHAUSTIVE DRAW",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CoolOrange
                        )
                        Text(
                            text = "流局 (Lau Kuk)",
                            fontSize = 16.sp,
                            color = PureWhite.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "The live wall has been completely depleted with no winning declaration.",
                            fontSize = 17.sp,
                            color = PureWhite.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    } else {
                        val winner = roundEndData.winnerIndex?.let { gameState.players[it] }
                        val winnerName = winner?.playerName ?: "RedDragon"
                        val winTypeTitle = if (roundEndData.isSelfDraw) "SELF-DRAW (自摸)" else "WIN ON DISCARD (食糊)"

                        Text(
                            text = winTypeTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrightYellow
                        )
                        Text(
                            text = "Winner: $winnerName (${winner?.seat?.name ?: "Seat"})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = PureWhite,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        // Winning Tile Display
                        roundEndData.winningTile?.let { tile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33000000))
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .border(2.dp, BrightYellow, RoundedCornerShape(4.dp))
                                        .padding(2.dp)
                                ) {
                                    TileView(
                                        tile = tile,
                                        orientation = TileOrientation.DOWN
                                    )
                                }
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CoolOrange)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${roundEndData.totalFan} FAN (番)",
                                            color = FlatBlack,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val basePts = FanCalculator.calculatePoints(roundEndData.totalFan)
                                    Text(
                                        text = "$basePts pts / payment",
                                        color = PureWhite.copy(alpha = 0.8f),
                                        fontSize = 17.sp
                                    )
                                }
                            }
                        }

                        // Fan Patterns List
                        val patterns = roundEndData.scoringResult?.patternNames ?: emptyList()
                        if (patterns.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Winning Patterns:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                patterns.forEach { pattern ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2C3E50))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = pattern,
                                            color = PureWhite,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chip Settlement
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Chip Settlement",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    (0..3).forEach { pid ->
                        val player = gameState.players[pid]
                        if (player != null) {
                            val delta = roundEndData.chipTransactions[pid] ?: 0
                            val isWinner = pid == roundEndData.winnerIndex
                            val deltaText = if (delta > 0) "+$delta" else if (delta < 0) "$delta" else "0"
                            val deltaColor = if (delta > 0) SubtleGreen else if (delta < 0) CrimsonRed else PureWhite.copy(alpha = 0.6f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isWinner) BrightYellow else Color.Transparent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${player.playerName} (${player.seat.name})",
                                        color = if (isWinner) BrightYellow else PureWhite,
                                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 17.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = deltaText,
                                        color = deltaColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        modifier = Modifier.width(75.dp),
                                        textAlign = TextAlign.End
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${player.score}",
                                        color = PureWhite,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(60.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
