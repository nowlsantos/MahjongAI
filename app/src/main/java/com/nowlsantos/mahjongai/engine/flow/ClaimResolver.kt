package com.nowlsantos.mahjongai.engine.flow
import com.nowlsantos.mahjongai.model.MahjongTile

enum class ClaimType(val priority: Int) {
    WIN(3),    // Highest priority
    PUNG(2),   // High priority
    KONG(2),   // Same priority as Pung
    CHOW(1),   // Low priority (Can only claim from player directly to left)
    PASS(0)
}

data class ClaimIntent(
    val playerIndex: Int,
    val claimType: ClaimType,
    val tilesToMelded: List<MahjongTile> = emptyList()
)

object ClaimResolver {

    /**
     * Resolves competing player claims on a discarded tile.
     * Returns the winning claim, or null if all players passed.
     */
    fun resolveHighestClaim(
        discarderIndex: Int,
        intents: List<ClaimIntent>
    ): ClaimIntent? {
        val activeIntents = intents.filter { it.claimType != ClaimType.PASS }
        if (activeIntents.isEmpty()) return null

        // Find maximum priority among submitted intents
        val maxPriority = activeIntents.maxOf { it.claimType.priority }
        val highestClaims = activeIntents.filter { it.claimType.priority == maxPriority }

        // If there's a unique highest priority (e.g. Win vs Chow), higher priority wins
        if (highestClaims.size == 1) {
            return highestClaims.first()
        }

        // Tie-breaker for multiple Win claims: Turn order proximity to discarder
        return highestClaims.minByOrNull { intent ->
            (intent.playerIndex - discarderIndex + 4) % 4
        }
    }
}