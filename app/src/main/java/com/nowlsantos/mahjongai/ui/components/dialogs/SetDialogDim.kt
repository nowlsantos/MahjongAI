package com.nowlsantos.mahjongai.ui.components.dialogs

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Adjusts the background scrim dim amount of the containing Dialog or AlertDialog,
 * and enforces immersive fullscreen mode on the dialog's separate Window so that
 * system navigation bars and status bars remain completely hidden.
 *
 * @param dimAmount 0.0f = completely clear (no scrim), 0.6f = default Android dark dim,
 *                  0.20f = subtle 20% opacity translucent scrim.
 */
@Composable
fun SetDialogDim(dimAmount: Float = 0.20f) {
    val view = LocalView.current
    SideEffect {
        var parent = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) {
                val dialogWindow = parent.window

                // 1. Configure translucent background scrim opacity
                dialogWindow.setDimAmount(dimAmount)

                // 2. Hide system navigation and status bars on the dialog's own Window
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                val insetsController = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                // 3. Clear non-focusable flags to ensure clean modal interaction while maintaining edge-to-edge
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                break
            }
            parent = parent.parent
        }
    }
}

