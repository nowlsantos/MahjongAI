package com.nowlsantos.mahjongai.ui.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nowlsantos.mahjongai.engine.flow.ClaimIntent
import com.nowlsantos.mahjongai.engine.flow.ClaimType
import com.nowlsantos.mahjongai.engine.flow.MoveGenerator
import com.nowlsantos.mahjongai.engine.flow.TurnAction
import com.nowlsantos.mahjongai.fsm.game.GamePhase
import com.nowlsantos.mahjongai.model.GameInput
import com.nowlsantos.mahjongai.model.GameState
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongSeat
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.UIState
import com.nowlsantos.mahjongai.ui.controls.ControlButton
import com.nowlsantos.mahjongai.ui.theme.CoolOrange
import com.nowlsantos.mahjongai.ui.theme.CrimsonRed
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.DarkGreen
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.ui.theme.LovelyPink
import com.nowlsantos.mahjongai.ui.theme.MatteGray
import com.nowlsantos.mahjongai.ui.theme.RareIndigo
import com.nowlsantos.mahjongai.ui.theme.RoyalPurple
import com.nowlsantos.mahjongai.ui.theme.SubtleGreen
import com.nowlsantos.mahjongai.ui.theme.TealDrop
import com.nowlsantos.mahjongai.ui.theme.TrueBlue
import com.nowlsantos.mahjongai.util.GameConstants

@Composable
fun PlayerController(
    modifier: Modifier = Modifier,
    gameState: GameState,
    uiState: UIState,
    onAction: (GameInput) -> Unit
) {
    val isHumanTurn = gameState.gamePhase == GamePhase.PLAYING && gameState.activePlayerIndex == 0
    val humanHand = uiState.playerStates[0]?.hand ?: emptyList()
    val revealedMelds = uiState.playerStates[0]?.revealedMelds ?: emptyList()
    val hasDrawn14thTile = (humanHand.size + revealedMelds.size * 3) % 3 == 2

    val hasConcealedKong = humanHand.groupBy { it.suit to it.value }.values.any { it.size == 4 }
    val hasAddedKong = revealedMelds.filter { it.type == MeldType.PUNG }.any { pung ->
        val sample = pung.tiles.firstOrNull()
        sample != null && humanHand.any { it.suit == sample.suit && it.value == sample.value }
    }
    val hasWallTilesRemaining = gameState.playerWalls.isEmpty() || gameState.playerWalls.values.any { !it.isFullyDepleted }
    val hasInTurnKong = isHumanTurn && hasDrawn14thTile && hasWallTilesRemaining && (hasConcealedKong || hasAddedKong)

    val canChow = uiState.availableClaims.contains(ClaimType.CHOW)
    val canPung = uiState.availableClaims.contains(ClaimType.PUNG)
    val canClaimKong = uiState.availableClaims.contains(ClaimType.KONG)
    val canWin = uiState.availableClaims.contains(ClaimType.WIN)
    val hasClaims = uiState.availableClaims.isNotEmpty()
    val buttonWidth = 170.dp

    // Control bar is visible either during a claim interception opportunity OR during the Human player's turn
    if (hasClaims || isHumanTurn) {
        Box(
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (hasClaims) {
                    // 1. Claim state: ONLY show claim-assisted buttons and Pass
                    val selectedTiles = humanHand.filter { uiState.selectedTileIds.contains(it.id) }
                    val lastDiscard = gameState.lastDiscardedTile

                    // Validate Chow claim with selected tiles: human must select exactly 2 tiles forming a legal chow with discard
                    val matchingChowIntent = if (canChow && selectedTiles.size == 2) {
                        uiState.legalClaimIntents.find { intent ->
                            intent.claimType == ClaimType.CHOW &&
                            intent.tilesToMelded.size == 2 &&
                            selectedTiles.map { it.suit to it.value }.sortedBy { it.second } ==
                                intent.tilesToMelded.map { it.suit to it.value }.sortedBy { it.second }
                        }?.copy(tilesToMelded = selectedTiles)
                    } else null

                    // Validate Pung claim with selected tiles: human must select exactly 2 matching tiles
                    val matchingPungIntent = if (canPung && selectedTiles.size == 2 && lastDiscard != null &&
                        selectedTiles.all { it.suit == lastDiscard.suit && it.value == lastDiscard.value }) {
                        uiState.legalClaimIntents.find { it.claimType == ClaimType.PUNG }?.copy(tilesToMelded = selectedTiles)
                    } else null

                    // Validate Kong claim with selected tiles: human must select exactly 3 matching tiles
                    val matchingKongIntent = if (canClaimKong && selectedTiles.size == 3 && lastDiscard != null &&
                        selectedTiles.all { it.suit == lastDiscard.suit && it.value == lastDiscard.value }) {
                        uiState.legalClaimIntents.find { it.claimType == ClaimType.KONG }?.copy(tilesToMelded = selectedTiles)
                    } else null

                    if (canChow) {
                        val isChowReady = matchingChowIntent != null
                        ControlButton(
                            modifier = Modifier.width(buttonWidth),
                            color = if (isChowReady) CoolOrange else MatteGray,
                            name = "Chow",
                            onClick = {
                                if (matchingChowIntent != null) {
                                    onAction(GameInput.SubmitClaim(matchingChowIntent))
                                }
                            }
                        )
                    }
                    if (canPung) {
                        val isPungReady = matchingPungIntent != null
                        ControlButton(
                            modifier = Modifier.width(buttonWidth),
                            color = if (isPungReady) CoolOrange else MatteGray,
                            name = "Pung",
                            onClick = {
                                if (matchingPungIntent != null) {
                                    onAction(GameInput.SubmitClaim(matchingPungIntent))
                                }
                            }
                        )
                    }
                    if (canClaimKong) {
                        val isKongReady = matchingKongIntent != null
                        ControlButton(
                            modifier = Modifier.width(buttonWidth),
                            color = if (isKongReady) CoolOrange else MatteGray,
                            name = "Kong",
                            onClick = {
                                if (matchingKongIntent != null) {
                                    onAction(GameInput.SubmitClaim(matchingKongIntent))
                                }
                            }
                        )
                    }
                    if (canWin) {
                        ControlButton(
                            modifier = Modifier.width(buttonWidth),
                            color = CoolOrange,
                            name = "Win",
                            onClick = {
                                val matchedIntent = uiState.legalClaimIntents.find { it.claimType == ClaimType.WIN }
                                    ?: ClaimIntent(playerIndex = 0, claimType = ClaimType.WIN)
                                onAction(GameInput.SubmitClaim(matchedIntent))
                            }
                        )
                    }
                    ControlButton(
                        modifier = Modifier.width(buttonWidth),
                        color = CrimsonRed,
                        name = "Pass",
                        onClick = {
                            val intent = ClaimIntent(
                                playerIndex = 0,
                                claimType = ClaimType.PASS
                            )
                            onAction(GameInput.SubmitClaim(intent))
                        }
                    )
                } else {
                    // 2. Human turn state:
                    val hasBonus = humanHand.any { it.isBonus }
                    val isBonusExchange = !gameState.isGameActive

                    if (isBonusExchange) {
                        // During initial bonus exchange: Show "Bonus Tile" (if holding bonus) and "Sort"
                        if (hasBonus) {
                            ControlButton(
                                modifier = Modifier.width(buttonWidth),
                                color = CoolOrange,
                                name = "Bonus Tile",
                                onClick = {
                                    val selectedTileIds = uiState.selectedTileIds
                                    val bonusTileToMeld = if (selectedTileIds.isNotEmpty()) {
                                        humanHand.firstOrNull { selectedTileIds.contains(it.id) && it.isBonus }
                                    } else {
                                        humanHand.firstOrNull { it.isBonus }
                                    }

                                    bonusTileToMeld?.let { tile ->
                                        onAction(GameInput.PerformBonusMeld(tile, 0, MahjongSeat.BOTTOM))
                                    }
                                }
                            )
                        }
                    } else {
                        // Normal active play:
                        if (!hasDrawn14thTile) {
                            // Before drawing: Only "Draw Tile" (and "Sort")
                            ControlButton(
                                modifier = Modifier.width(buttonWidth),
                                color = CrimsonRed,
                                name = "Draw Tile",
                                onClick = {
                                    onAction(GameInput.DrawRegularTile(0))
                                }
                            )
                        } else {
                            // After drawing / Holding 14 tiles: "Discard"
                            val tileToDiscard = if (uiState.selectedTileIds.size == 1) humanHand.find { uiState.selectedTileIds.contains(it.id) } else null
                            val isTileSelected = tileToDiscard != null
                            ControlButton(
                                modifier = Modifier.width(buttonWidth),
                                color = if (isTileSelected) CrimsonRed else MatteGray,
                                name = "Discard",
                                onClick = {
                                    if (tileToDiscard != null) {
                                        onAction(
                                            GameInput.StartDiscardAnimation(
                                                tile = tileToDiscard,
                                                playerIndex = 0,
                                                seat = MahjongSeat.BOTTOM
                                            )
                                        )
                                    }
                                }
                            )

                            // Self-Draw Win: ONLY visible if human player has achieved Hu
                            val humanBonusTiles = uiState.playerStates[0]?.bonusTiles ?: emptyList()
                            val seatWind = gameState.getPlayerSeatWind(0)
                            val humanTurnActions = MoveGenerator.getLegalTurnActions(
                                playerHand = humanHand,
                                revealedMelds = revealedMelds,
                                bonusTiles = humanBonusTiles,
                                seatWind = seatWind,
                                prevalentWind = gameState.prevalentWind,
                                minFanRequired = GameConstants.MIN_FAN_REQUIRED
                            )
                            val selfDrawWin = humanTurnActions.filterIsInstance<TurnAction.SelfDrawWin>().firstOrNull()
                            if (selfDrawWin != null) {
                                ControlButton(
                                    modifier = Modifier.width(buttonWidth),
                                    color = CoolOrange,
                                    name = "Win (Hu)",
                                    onClick = {
                                        onAction(GameInput.DeclareSelfDrawWin(0, selfDrawWin.winningTile))
                                    }
                                )
                            }

                            // In-turn Kong: ONLY visible if human player has a legal concealed or added kong
                            if (hasInTurnKong) {
                                ControlButton(
                                    modifier = Modifier.width(buttonWidth),
                                    color = CoolOrange,
                                    name = "Kong",
                                    onClick = {
                                        onAction(GameInput.PerformInTurnKong(0))
                                    }
                                )
                            }

                            // Bonus Tile: ONLY visible if human player has flowers/seasons
                            if (hasBonus) {
                                ControlButton(
                                    modifier = Modifier.width(buttonWidth),
                                    color = CoolOrange,
                                    name = "Bonus Tile",
                                    onClick = {
                                        val selectedTileIds = uiState.selectedTileIds
                                        val bonusTileToMeld = if (selectedTileIds.isNotEmpty()) {
                                            humanHand.firstOrNull { selectedTileIds.contains(it.id) && it.isBonus }
                                        } else {
                                            humanHand.firstOrNull { it.isBonus }
                                        }

                                        bonusTileToMeld?.let { tile ->
                                            onAction(GameInput.PerformBonusMeld(tile, 0, MahjongSeat.BOTTOM))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Sort button is available at all times during human turn
                ControlButton(
                    modifier = Modifier.width(buttonWidth),
                    color = SubtleGreen,
                    name = "Sort By Suit",
                    onClick = {
                        onAction(GameInput.SortBySuit)
                    }
                )
            }
        }
    }
}
