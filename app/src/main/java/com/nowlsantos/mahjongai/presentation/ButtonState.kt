package com.nowlsantos.mahjongai.presentation

data class ButtonState(
    val buttons: Map<ButtonType, Boolean>,
    val isActionPhase: Boolean = false
) {
    companion object {
        val ALL_DISABLED = ButtonState(
            buttons = mapOf(
                ButtonType.SORT_SUIT to false,
                ButtonType.PASS to false,
                ButtonType.CHOW to false,
                ButtonType.PUNG to false,
                ButtonType.KONG to false,
                ButtonType.DRAW to false,
                ButtonType.DISCARD to false,
                ButtonType.MELD to false,
                ButtonType.WIN to false
            ),
            isActionPhase = false
        )
    }
}

/**
 * Enum representing all the possible buttons in the UI.
 */
enum class ButtonType {
    PASS,
    PUNG,
    KONG,
    CHOW,
    DRAW,
    MELD,
    DISCARD,
    WIN,
    SORT_SUIT
}