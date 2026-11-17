package com.nowlsantos.mahjongai

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nowlsantos.mahjongai.ui.components.dialogs.ShowExitDialog
import com.nowlsantos.mahjongai.ui.screens.MahjongTable
import com.nowlsantos.mahjongai.ui.theme.MahjongAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            var showExitDialog by remember { mutableStateOf(false) }

            BackHandler {
                showExitDialog = true
            }

            MahjongAITheme {
                LoadBackgroundImage(R.drawable.main_background)
                MahjongTable()

                if (showExitDialog) {
                    ShowExitDialog(
                        onDismiss = { showExitDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadBackgroundImage(imageID: Int) {
    Image(
        painter = painterResource(imageID),
        contentDescription = "Mahjong Background",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
