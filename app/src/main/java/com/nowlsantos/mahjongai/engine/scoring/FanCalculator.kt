package com.nowlsantos.mahjongai.engine.scoring

import com.nowlsantos.mahjongai.model.HonorType
import com.nowlsantos.mahjongai.model.MahjongTile
import com.nowlsantos.mahjongai.model.Meld
import com.nowlsantos.mahjongai.model.MeldType
import com.nowlsantos.mahjongai.model.Suit

data class ScoringResult(
    val totalFan: Int,
    val patternNames: List<String>
)

object FanCalculator {

    /**
     * Converts a Fan count into standard Hong Kong Mahjong chip points.
     * When fan is 0 (Chicken Hand), base payout is 100 chips.
     */
    fun calculatePoints(fan: Int): Int {
        return when {
            fan <= 0 -> 100
            fan == 1 -> 200
            fan == 2 -> 400
            fan == 3 -> 800
            fan == 4 -> 1600
            fan == 5 -> 2400
            fan == 6 -> 3200
            fan == 7 -> 4800
            fan == 8 -> 6400
            fan == 9 -> 9600
            else -> 12800
        }
    }

    /**
     * Calculates the Fan score for a confirmed winning hand in Hong Kong style.
     * Takes into account concealed hand tiles, revealed melds, and bonus/flower tiles.
     */
    fun calculateFan(
        hand: List<MahjongTile>,
        revealedMelds: List<Meld> = emptyList(),
        bonusTiles: List<MahjongTile> = emptyList(),
        isSelfDraw: Boolean,
        seatWind: HonorType,
        prevalentWind: HonorType,
        isWinOnReplacement: Boolean = false,
        isLastTileWall: Boolean = false,
        isRobbingKong: Boolean = false,
        turnNumber: Int = 10
    ): ScoringResult {
        var fanCount = 0
        val patterns = mutableListOf<String>()

        val concealedNonBonus = hand.filterNot { it.isBonus }
        val allBonusTiles = (bonusTiles + hand.filter { it.isBonus } +
            revealedMelds.filter { it.type == MeldType.FLOWER }.flatMap { it.tiles })
            .distinctBy { it.id }
        val standardMelds = revealedMelds.filter { it.type != MeldType.FLOWER }

        // Full composite standard tile collection (concealed hand + exposed melds)
        val allStandardTiles = concealedNonBonus + standardMelds.flatMap { it.tiles }

        val suitsPresent = allStandardTiles.map { it.suit }
            .filter { it == Suit.CHARACTER || it == Suit.BAMBOO || it == Suit.DOT }
            .distinct()
        val hasHonors = allStandardTiles.any { it.isHonor }

        val honorGroups = allStandardTiles.filter { it.isHonor }.groupBy { it.honorType }
        val dragonTypes = listOf(HonorType.RED, HonorType.GREEN, HonorType.WHITE)
        val windTypes = listOf(HonorType.EAST, HonorType.SOUTH, HonorType.WEST, HonorType.NORTH)

        // 0a. Thirteen Orphans (Sap Sam Jiu / 十三幺) - 13 Fan (Limit Hand)
        if (standardMelds.isEmpty() && com.nowlsantos.mahjongai.engine.logic.HandEvaluator.isThirteenOrphans(concealedNonBonus)) {
            return ScoringResult(13, listOf("Thirteen Orphans (十三幺)"))
        }

        // 0b. All Green (Luk Yat Sik / 綠一色) - 13 Fan (Limit Hand)
        // Hand consists exclusively of 2, 3, 4, 6, 8 Bamboo and Green Dragon
        val isAllGreen = allStandardTiles.all { tile ->
            (tile.suit == Suit.BAMBOO && tile.value in listOf(2, 3, 4, 6, 8)) ||
                (tile.suit == Suit.HONOR && tile.honorType == HonorType.GREEN)
        }
        if (isAllGreen) {
            return ScoringResult(13, listOf("All Green (綠一色)"))
        }

        // 0c. Big Four Winds (Daai Sei Hei / 大四喜) - 13 Fan (Limit Hand)
        // Pungs or Kongs of all four winds (East, South, West, North)
        val windPungs = windTypes.count { (honorGroups[it]?.size ?: 0) >= 3 }
        val windPairs = windTypes.count { (honorGroups[it]?.size ?: 0) == 2 }

        if (windPungs == 4) {
            return ScoringResult(13, listOf("Big Four Winds (大四喜)"))
        }

        // 0d. Small Four Winds (Siu Sei Hei / 小四喜) - 8 Fan
        // Three wind pungs + 1 pair of the fourth wind
        if (windPungs == 3 && windPairs == 1) {
            fanCount += 8
            patterns.add("Small Four Winds (小四喜)")
        }

        // 0e. Seven Pairs (Chat Dui Zi / 七對子) - 4 Fan
        if (standardMelds.isEmpty() && com.nowlsantos.mahjongai.engine.logic.HandEvaluator.isSevenPairs(concealedNonBonus)) {
            fanCount += 4
            patterns.add("Seven Pairs (七對子)")
        }

        // 0f. Pure Terminals (Ching Yiu Gau / 清么九) - 8 Fan
        // Entire hand consists exclusively of terminal suit tiles (1s and 9s), no honors!
        val isPureTerminals = allStandardTiles.all { it.isSuitTile && (it.value == 1 || it.value == 9) }
        // 0g. All Terminals and Honors (Wun Yiu Gau / 混么九) - 4 Fan
        // Entire hand consists exclusively of terminals (1s and 9s) AND honors
        val isAllTerminalsAndHonors = !isPureTerminals && allStandardTiles.all {
            it.isHonor || (it.isSuitTile && (it.value == 1 || it.value == 9))
        }

        if (isPureTerminals) {
            fanCount += 8
            patterns.add("Pure Terminals (清么九)")
        } else if (isAllTerminalsAndHonors) {
            fanCount += 4
            patterns.add("All Terminals & Honors (混么九)")
        }

        // 1. All Honors (Tsz Yat Sik) - 10 Fan
        if (suitsPresent.isEmpty() && hasHonors) {
            fanCount += 10
            patterns.add("All Honors (字一色)")
        }
        // 2. All One Suit (Ching Yat Sik / Full Flush) - 7 Fan
        else if (suitsPresent.size == 1 && !hasHonors) {
            fanCount += 7
            patterns.add("All One Suit (清一色)")
        }
        // 3. Mixed One Suit (Wun Yat Sik / Half Flush) - 3 Fan
        else if (suitsPresent.size == 1 && hasHonors) {
            fanCount += 3
            patterns.add("Mixed One Suit (混一色)")
        }

        // 4. All Pungs (Tui Tui Wu) vs All Chows (Ping Wu)
        val hasChowMeld = standardMelds.any { it.type == MeldType.CHOW }
        val hasPungOrKongMeld = standardMelds.any {
            it.type == MeldType.PUNG || it.type == MeldType.KONG || it.type == MeldType.CONCEALED_KONG
        }

        val isPingWu = !hasPungOrKongMeld && isConcealedHandAllChows(concealedNonBonus, 4 - standardMelds.size) &&
            !honorGroups.any { (honor, tiles) ->
                tiles.size >= 2 && (honor in dragonTypes || honor == seatWind || honor == prevalentWind)
            }

        if (!hasChowMeld && isConcealedHandAllPungs(concealedNonBonus)) {
            fanCount += 3
            patterns.add("All Pungs (對對胡)")
        } else if (isPingWu) {
            fanCount += 1
            patterns.add("All Chows (平胡)")
        }

        // 4b. Pure Straight (Yat Tiu Lung / 一條龍 / 1-9 Straight in Same Suit) - 2 Fan
        if (hasPureStraight(concealedNonBonus, standardMelds)) {
            fanCount += 2
            patterns.add("Pure Straight (一條龍)")
        }

        // 5. Dragon Patterns (Big Three Dragons = 8 Fan, Small Three Dragons = 5 Fan, or individual Dragon Pungs = 1 Fan)
        val dragonPungs = dragonTypes.count { (honorGroups[it]?.size ?: 0) >= 3 }
        val dragonPairs = dragonTypes.count { (honorGroups[it]?.size ?: 0) == 2 }

        if (dragonPungs == 3) {
            fanCount += 8
            patterns.add("Big Three Dragons (大三元)")
        } else if (dragonPungs == 2 && dragonPairs == 1) {
            fanCount += 5
            patterns.add("Small Three Dragons (小三元)")
        } else {
            for (dragon in dragonTypes) {
                if ((honorGroups[dragon]?.size ?: 0) >= 3) {
                    fanCount += 1
                    patterns.add("Dragon Pung (${dragon.name})")
                }
            }
        }

        // 6. Wind Patterns (Seat Wind & Prevalent Wind Pungs - 1 Fan each)
        // If Big Four Winds or Small Four Winds already triggered, do not double-count individual winds
        if (windPungs < 3) {
            for ((type, tiles) in honorGroups) {
                if (tiles.size >= 3 && type != null && type !in dragonTypes) {
                    if (type == seatWind) {
                        fanCount += 1
                        patterns.add("Seat Wind ($type)")
                    }
                    if (type == prevalentWind) {
                        fanCount += 1
                        patterns.add("Prevalent Wind ($type)")
                    }
                }
            }
        }

        // 7. Fully Concealed Hand (Mun Ching) - 1 Fan (no open Chows, Pungs, or open Kongs)
        val hasOpenMelds = standardMelds.any { it.type != MeldType.CONCEALED_KONG }
        if (!hasOpenMelds) {
            fanCount += 1
            patterns.add("Concealed Hand (門前清)")
        }

        // 8. Bonus / Flower & Season Scoring
        if (allBonusTiles.isEmpty()) {
            fanCount += 1
            patterns.add("No Flowers/Seasons (無花)")
        } else {
            // Seat number: East = 1, South = 2, West = 3, North = 4
            val seatNumber = seatWind.ordinal + 1
            val matchingFlowers = allBonusTiles.count { it.value == seatNumber }
            if (matchingFlowers > 0) {
                fanCount += matchingFlowers
                patterns.add("Seat Flower/Season (+$matchingFlowers)")
            }
            val flowerSetCount = allBonusTiles.count { it.suit == Suit.FLOWER }
            val seasonSetCount = allBonusTiles.count { it.suit == Suit.SEASON }
            if (flowerSetCount == 4) {
                fanCount += 2
                patterns.add("Complete Flower Set (一台花)")
            }
            if (seasonSetCount == 4) {
                fanCount += 2
                patterns.add("Complete Season Set (一台季)")
            }
        }

        // 9. Self-Draw (Jik Mo / Tsumo) - 1 Fan
        if (isSelfDraw) {
            fanCount += 1
            patterns.add("Self-Draw (自摸)")
        }

        // 10. Kong Patterns in Hand (Modern Rules)
        val kongCount = standardMelds.count { it.type == MeldType.KONG || it.type == MeldType.CONCEALED_KONG }
        when (kongCount) {
            4 -> {
                fanCount += 13
                patterns.add("Four Kongs (十八羅漢)")
            }
            3 -> {
                fanCount += 3
                patterns.add("Three Kongs (三槓子)")
            }
            2 -> {
                fanCount += 1
                patterns.add("Two Kongs (雙槓)")
            }
        }

        // 11. Win on Replacement Tile (槓上開花) - 1 Fan
        if (isWinOnReplacement) {
            fanCount += 1
            patterns.add("Win on Replacement Tile (槓上開花)")
        }

        // 12. Last Tile from Wall (海底撈月 / Hoi Dai Lou Yuet) or Last Discard (河底撈魚 / Hoh Dai Lou Yu) - 1 Fan
        if (isLastTileWall) {
            fanCount += 1
            if (isSelfDraw) {
                patterns.add("Under the Sea (海底撈月)")
            } else {
                patterns.add("Under the River (河底撈魚)")
            }
        }

        // 13. Robbing the Kong (搶槓 / Cheung Gong) - 1 Fan
        if (isRobbingKong) {
            fanCount += 1
            patterns.add("Robbing the Kong (搶槓)")
        }

        // 14. Before the Fifth / Early Win (五巡內胡牌 / Bago Mag-Singko) - 1 Fan
        // Awarded when winning before making a 5th discard (i.e. Discard count < 5 or within first 4 turns)
        if (turnNumber < 5) {
            fanCount += 1
            patterns.add("Before the Fifth (五巡內)")
        }

        // Default Chicken Hand (Kai Wu) - 0 Fan
        if (fanCount == 0) {
            patterns.add("Chicken Hand (雞胡)")
        }

        return ScoringResult(fanCount, patterns)
    }

    private fun isConcealedHandAllPungs(concealedHand: List<MahjongTile>): Boolean {
        val counts = concealedHand.groupingBy { it.suit to it.value }.eachCount()
        val pairs = counts.filter { it.value == 2 }
        val pungs = counts.filter { it.value >= 3 }

        return pairs.size == 1 && (pairs.size + pungs.size == counts.size)
    }

    private fun isConcealedHandAllChows(concealedHand: List<MahjongTile>, requiredChows: Int): Boolean {
        if (requiredChows < 0 || concealedHand.size != requiredChows * 3 + 2) return false
        val sorted = concealedHand.sorted()
        val counts = sorted.groupingBy { it.suit to it.value }.eachCount()

        for ((pairKind, count) in counts) {
            if (count >= 2) {
                val rem = sorted.toMutableList()
                repeat(2) {
                    val idx = rem.indexOfFirst { (it.suit to it.value) == pairKind }
                    if (idx != -1) rem.removeAt(idx)
                }
                if (canFormOnlyChows(rem, requiredChows)) return true
            }
        }
        return false
    }

    private fun canFormOnlyChows(tiles: MutableList<MahjongTile>, needed: Int): Boolean {
        if (tiles.isEmpty() && needed == 0) return true
        if (tiles.isEmpty() || needed <= 0) return false
        val first = tiles[0]
        if (!first.isSuitTile) return false
        val second = tiles.find { it.suit == first.suit && it.value == first.value + 1 }
        val third = tiles.find { it.suit == first.suit && it.value == first.value + 2 }
        if (second != null && third != null) {
            val sub = tiles.toMutableList()
            sub.remove(first)
            sub.remove(second)
            sub.remove(third)
            return canFormOnlyChows(sub, needed - 1)
        }
        return false
    }

    /**
     * Checks if the winning hand contains a Pure Straight (一條龍 / 1 through 9 in a single suit).
     * The straight consists of three Chows in the same suit: [1-2-3], [4-5-6], and [7-8-9].
     * These three Chows can either be exposed melds or formed within the concealed hand.
     */
    private fun hasPureStraight(
        concealedNonBonus: List<MahjongTile>,
        standardMelds: List<Meld>
    ): Boolean {
        val targetSuits = listOf(Suit.CHARACTER, Suit.BAMBOO, Suit.DOT)

        for (suit in targetSuits) {
            // Find exposed Chows in this suit
            val exposedChows = standardMelds.filter { it.type == MeldType.CHOW && it.tiles.firstOrNull()?.suit == suit }
            val has123Exposed = exposedChows.any { m -> m.tiles.map { it.value }.sorted() == listOf(1, 2, 3) }
            val has456Exposed = exposedChows.any { m -> m.tiles.map { it.value }.sorted() == listOf(4, 5, 6) }
            val has789Exposed = exposedChows.any { m -> m.tiles.map { it.value }.sorted() == listOf(7, 8, 9) }

            // If already completed via exposed chows
            if (has123Exposed && has456Exposed && has789Exposed) return true

            // Check if missing 1-2-3, 4-5-6, or 7-8-9 runs can be formed from concealed tiles
            // We need copies of all values 1..9 in this suit that aren't already covered by exposed chows
            val neededRuns = mutableListOf<List<Int>>()
            if (!has123Exposed) neededRuns.add(listOf(1, 2, 3))
            if (!has456Exposed) neededRuns.add(listOf(4, 5, 6))
            if (!has789Exposed) neededRuns.add(listOf(7, 8, 9))

            // Check if concealed hand can supply these needed runs while leaving the rest of the hand valid
            if (canConcealedSupplyStraightRuns(concealedNonBonus, suit, neededRuns, standardMelds.size)) {
                return true
            }
        }
        return false
    }

    private fun canConcealedSupplyStraightRuns(
        concealedHand: List<MahjongTile>,
        suit: Suit,
        neededRuns: List<List<Int>>,
        exposedMeldsCount: Int
    ): Boolean {
        val rem = concealedHand.toMutableList()

        // Greedily remove each required run from the concealed hand
        for (run in neededRuns) {
            for (v in run) {
                val idx = rem.indexOfFirst { it.suit == suit && it.value == v }
                if (idx == -1) return false
                rem.removeAt(idx)
            }
        }

        // After removing the straight runs, the remaining concealed tiles must form:
        // (4 - exposedMeldsCount - neededRuns.size) melds + 1 pair
        val remainingMeldsNeeded = 4 - exposedMeldsCount - neededRuns.size
        if (remainingMeldsNeeded < 0) return false
        val expectedTiles = (remainingMeldsNeeded * 3) + 2
        if (rem.size != expectedTiles) return false

        // Validate that remainder forms valid melds + pair
        val sortedRem = rem.sorted()
        val counts = sortedRem.groupingBy { it.suit to it.value }.eachCount()
        for ((pairKind, count) in counts) {
            if (count >= 2) {
                val testList = sortedRem.toMutableList()
                repeat(2) {
                    val idx = testList.indexOfFirst { (it.suit to it.value) == pairKind }
                    if (idx != -1) testList.removeAt(idx)
                }
                if (canFormMelds(testList, remainingMeldsNeeded)) return true
            }
        }
        return false
    }

    private fun canFormMelds(tiles: MutableList<MahjongTile>, remainingMeldsNeeded: Int): Boolean {
        if (tiles.isEmpty() && remainingMeldsNeeded == 0) return true
        if (tiles.isEmpty() || remainingMeldsNeeded <= 0) return false

        val first = tiles[0]

        // Try Pung
        val identicalCount = tiles.count { it.suit == first.suit && it.value == first.value }
        if (identicalCount >= 3) {
            val subList = tiles.toMutableList()
            repeat(3) {
                val idx = subList.indexOfFirst { it.suit == first.suit && it.value == first.value }
                if (idx != -1) subList.removeAt(idx)
            }
            if (canFormMelds(subList, remainingMeldsNeeded - 1)) return true
        }

        // Try Chow
        if (first.isSuitTile) {
            val second = tiles.find { it.suit == first.suit && it.value == first.value + 1 }
            val third = tiles.find { it.suit == first.suit && it.value == first.value + 2 }
            if (second != null && third != null) {
                val subList = tiles.toMutableList()
                subList.remove(first)
                subList.remove(second)
                subList.remove(third)
                if (canFormMelds(subList, remainingMeldsNeeded - 1)) return true
            }
        }
        return false
    }
}
