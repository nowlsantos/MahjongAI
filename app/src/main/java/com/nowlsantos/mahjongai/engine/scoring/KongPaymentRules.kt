package com.nowlsantos.mahjongai.engine.scoring

/**
 * Modern Mahjong Rules for immediate Kong settlement ("Kong Money" / 槓錢).
 */
object KongPaymentRules {
    /**
     * Base point value for a Kong under Modern Rules.
     * Equivalent to 1 Fan points (200 pts in standard 25,000 pt starting match).
     */
    const val BASE_KONG_CHIP = 200

    /**
     * Calculates the point adjustments for all 4 players (0..3) when a Kong is formed.
     *
     * Modern Rules ("Kong Money" / 槓錢):
     * 1. Concealed Kong (暗槓 - An Gang):
     *    All 3 opponents pay 2x base points to the declarer (400 pts each, total +1,200 pts).
     * 2. Added / Promoted Kong (加槓/補槓 - Bu Gang / Xiao Ming Gang):
     *    All 3 opponents pay 1x base points to the declarer (200 pts each, total +600 pts).
     * 3. Melded Kong from Discard (大明槓/直槓 - Da Ming Gang):
     *    The discarder alone pays 3x base points to the declarer (600 pts, total +600 pts).
     */
    fun calculateKongPayment(
        declarerIndex: Int,
        isConcealed: Boolean,
        isPromoted: Boolean,
        discarderIndex: Int? = null
    ): Map<Int, Int> {
        val transactions = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)

        when {
            isConcealed -> {
                val perOpponent = BASE_KONG_CHIP * 2
                var totalEarned = 0
                for (pid in 0..3) {
                    if (pid != declarerIndex) {
                        transactions[pid] = -perOpponent
                        totalEarned += perOpponent
                    }
                }
                transactions[declarerIndex] = totalEarned
            }
            isPromoted -> {
                val perOpponent = BASE_KONG_CHIP
                var totalEarned = 0
                for (pid in 0..3) {
                    if (pid != declarerIndex) {
                        transactions[pid] = -perOpponent
                        totalEarned += perOpponent
                    }
                }
                transactions[declarerIndex] = totalEarned
            }
            else -> {
                val targetDiscarder = discarderIndex ?: ((declarerIndex + 3) % 4)
                val totalAmount = BASE_KONG_CHIP * 3
                transactions[targetDiscarder] = -totalAmount
                transactions[declarerIndex] = totalAmount
            }
        }

        return transactions
    }
}
