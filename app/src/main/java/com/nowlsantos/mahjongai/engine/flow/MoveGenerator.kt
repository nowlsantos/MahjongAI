package com.nowlsantos.mahjongai.engine.flow

import com.nowlsantos.mahjongai.engine.logic.HandEvaluator
import com.nowlsantos.mahjongai.engine.scoring.FanCalculator
import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.util.GameConstants

/**
 * Defines legal actions an active player can take on their own turn.
 */
sealed interface TurnAction {
    data class Discard(val tile: MahjongTile) : TurnAction
    data class SelfDrawWin(val winningTile: MahjongTile) : TurnAction
    data class ConcealedKong(val tiles: List<MahjongTile>) : TurnAction
    data class AddedKong(val tile: MahjongTile, val targetPung: Meld) : TurnAction
    data class BonusMeld(val tile: MahjongTile) : TurnAction
}

/**
 * Pure, stateless Move Generator providing the single source of truth
 * for all legal Mahjong moves, claims, and meld opportunities.
 */
object MoveGenerator {

    /**
     * Computes all legal claims a player can declare against an opponent's discard.
     * Takes into account concealed hand tiles as well as already exposed melds.
     *
     * Enforces Hong Kong Mahjong rules:
     * - Win (Ron): requires valid hand structure and minimum Fan requirement.
     * - Kong: requires 3 matching tiles in hand.
     * - Pung: requires 2 matching tiles in hand.
     * - Chow: strictly allowed ONLY from the player directly to the left (turn order proximity = 1).
     * - Pass: always available.
     */
    fun getLegalClaims(
        playerIndex: Int,
        playerHand: List<MahjongTile>,
        revealedMelds: List<Meld> = emptyList(),
        bonusTiles: List<MahjongTile> = emptyList(),
        discarderIndex: Int,
        discardedTile: MahjongTile,
        seatWind: HonorType,
        prevalentWind: HonorType,
        minFanRequired: Int = GameConstants.MIN_FAN_REQUIRED
    ): List<ClaimIntent> {
        if (playerIndex == discarderIndex || discardedTile.isBonus) {
            return listOf(ClaimIntent(playerIndex, ClaimType.PASS))
        }

        val claims = mutableListOf<ClaimIntent>()

        // 1. Check Win (Ron) using concealed hand + discard + revealed melds count
        val handWithDiscard = playerHand + discardedTile
        if (HandEvaluator.isWinningHand(handWithDiscard, revealedMelds.size)) {
            val fanScore = FanCalculator.calculateFan(
                hand = handWithDiscard,
                revealedMelds = revealedMelds,
                bonusTiles = bonusTiles,
                isSelfDraw = false,
                seatWind = seatWind,
                prevalentWind = prevalentWind
            )
            if (fanScore.totalFan >= minFanRequired) {
                claims.add(
                    ClaimIntent(
                        playerIndex = playerIndex,
                        claimType = ClaimType.WIN,
                        tilesToMelded = emptyList()
                    )
                )
            }
        }

        // 2. Check Kong & Pung (Matching suit and value in concealed hand)
        val matchingTiles = playerHand.filter { it.suit == discardedTile.suit && it.value == discardedTile.value }
        if (matchingTiles.size >= 3) {
            claims.add(
                ClaimIntent(
                    playerIndex = playerIndex,
                    claimType = ClaimType.KONG,
                    tilesToMelded = matchingTiles.take(3)
                )
            )
        }
        if (matchingTiles.size >= 2) {
            claims.add(
                ClaimIntent(
                    playerIndex = playerIndex,
                    claimType = ClaimType.PUNG,
                    tilesToMelded = matchingTiles.take(2)
                )
            )
        }

        // 3. Check Chow (Only valid from player directly to the left: (playerIndex - 1 + 4) % 4 == discarderIndex)
        val isLeftPlayerDiscard = (playerIndex - 1 + 4) % 4 == discarderIndex
        if (isLeftPlayerDiscard && discardedTile.isSuitTile) {
            val v = discardedTile.value
            val suit = discardedTile.suit

            // Possible consecutive sequence pairs to complete a Chow [v-2, v-1, v], [v-1, v, v+1], [v, v+1, v+2]
            val candidatePairs = listOf(
                Pair(v - 2, v - 1),
                Pair(v - 1, v + 1),
                Pair(v + 1, v + 2)
            )

            for ((val1, val2) in candidatePairs) {
                if (val1 in 1..9 && val2 in 1..9) {
                    val tile1 = playerHand.find { it.suit == suit && it.value == val1 }
                    val tile2 = playerHand.find { it.suit == suit && it.value == val2 && it.id != tile1?.id }
                    if (tile1 != null && tile2 != null) {
                        claims.add(
                            ClaimIntent(
                                playerIndex = playerIndex,
                                claimType = ClaimType.CHOW,
                                tilesToMelded = listOf(tile1, tile2)
                            )
                        )
                    }
                }
            }
        }

        // 4. Pass is always valid
        claims.add(ClaimIntent(playerIndex, ClaimType.PASS))
        return claims
    }

    /**
     * Computes all legal actions a player can take on their active turn.
     * Takes into account concealed hand tiles as well as already exposed melds.
     */
    fun getLegalTurnActions(
        playerHand: List<MahjongTile>,
        revealedMelds: List<Meld> = emptyList(),
        bonusTiles: List<MahjongTile> = emptyList(),
        seatWind: HonorType,
        prevalentWind: HonorType,
        minFanRequired: Int = GameConstants.MIN_FAN_REQUIRED
    ): List<TurnAction> {
        val actions = mutableListOf<TurnAction>()

        // 1. Bonus Melds (Flowers/Seasons)
        val handBonusTiles = playerHand.filter { it.isBonus }
        handBonusTiles.forEach { actions.add(TurnAction.BonusMeld(it)) }

        // 2. Self-Draw Win (Tsumo) using remaining concealed tiles + revealed melds count
        if (HandEvaluator.isWinningHand(playerHand, revealedMelds.size)) {
            val fanScore = FanCalculator.calculateFan(
                hand = playerHand,
                revealedMelds = revealedMelds,
                bonusTiles = bonusTiles,
                isSelfDraw = true,
                seatWind = seatWind,
                prevalentWind = prevalentWind
            )
            if (fanScore.totalFan >= minFanRequired) {
                playerHand.lastOrNull()?.let { winningTile ->
                    actions.add(TurnAction.SelfDrawWin(winningTile))
                }
            }
        }

        // 3. In-Turn Kongs
        // 3a. Concealed Kong (Ankong)
        val groupedByKind = playerHand.groupBy { it.suit to it.value }
        groupedByKind.values.filter { it.size == 4 }.forEach { fourTiles ->
            actions.add(TurnAction.ConcealedKong(fourTiles))
        }

        // 3b. Added Kong (Bu Gang / Ka Gang): 1 tile in hand matching an existing revealed Pung
        val revealedPungs = revealedMelds.filter { it.type == MeldType.PUNG }
        for (pung in revealedPungs) {
            val sample = pung.tiles.firstOrNull() ?: continue
            val matchingTile = playerHand.find { it.suit == sample.suit && it.value == sample.value }
            if (matchingTile != null) {
                actions.add(TurnAction.AddedKong(matchingTile, pung))
            }
        }

        // 4. Normal Discards (Deduplicated unique candidate tiles)
        val discardCandidates = playerHand.filterNot { it.isBonus }.distinctBy { it.suit to it.value }
        discardCandidates.forEach { actions.add(TurnAction.Discard(it)) }

        return actions
    }
}
