package com.nowlsantos.mahjongai.fsm.game

import androidx.compose.runtime.Immutable

/**
 * Represents the top-level macro phases of a Hong Kong-style Mahjong game lifecycle.
 *
 * The game progresses sequentially through these states under the direction of the
 * [GameStateMachine], moving from setup to active play, win evaluation, and match termination.
 */
@Immutable
enum class GamePhase {
    /** Initializing deck wall, shuffling, and setting dealer/seat winds. */
    SETUP,

    /** Rolling the Dice twice to determine dealer and wall splits. */
    ROLLING_DICE,

    /** Arranging the tiles and rendering top hidden tiles for each player */
    ARRANGING,

    /** Dealing 13 tiles to each player (14 for dealer). */
    DISTRIBUTION,

    /** Main turn loop managing drawing, discarding, and intercepting claims (Chow, Pung, Kong). */
    PLAYING,

    /** A tile has been discarded; evaluating all players for potential claims (Chow, Pung, Kong, Win). */
    CLAIM_INTERCEPTION,

    /** Finalizing round scores, adjusting chip values, and rotating dealer/winds for the next round. */
    ROUND_END,

    /** All rounds/winds completed or target scores reached; final match results display. */
    GAME_OVER
}
/*

*/
/** Active player is drawing a tile from the wall (handling replacement tiles if bonus/flower drawn). *//*

DRAW_PHASE,

*/
/** Active player is evaluating hand and waiting to submit a discard choice. *//*

DISCARD_PHASE,

*/
/** A tile has been discarded; opening a reaction window for all other 3 players to claim (Win/Pung/Kong/Chow). *//*

CLAIM_WINDOW,

*/
/** Resolving submitted claim intents by priority order (Win > Pung/Kong > Chow). *//*

RESOLVING_CLAIMS,

*/
/** Executing an open meld claim or concealed Kong before returning to turn flow. *//*

MELD_EXECUTION,

*/
/** Round has concluded (either by a Win/Self-Draw or Wall Depletion draw / Lau Kuk). Calculating Fan and scores. *//*

ROUND_OVER*/
