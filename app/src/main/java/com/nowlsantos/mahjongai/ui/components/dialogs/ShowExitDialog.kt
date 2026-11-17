package com.nowlsantos.mahjongai.ui.components.dialogs

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nowlsantos.mahjongai.ui.theme.DarkGray
import com.nowlsantos.mahjongai.ui.theme.DarkGreen
import com.nowlsantos.mahjongai.ui.theme.FlatBlack
import com.nowlsantos.mahjongai.ui.theme.PureWhite
import com.nowlsantos.mahjongai.ui.theme.SubtleGreen

@Composable
fun ShowExitDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatBlack,
        textContentColor = PureWhite,
        title = {
            SetDialogDim(dimAmount = 0.20f)
            Text(
                text = "Exit MahjongAI?",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to close the game?",
                fontSize = 22.sp,
                color = PureWhite
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    (context as? Activity)?.finishAndRemoveTask()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubtleGreen,
                    contentColor = PureWhite
                )
            ) {
                Text(
                    text = "Exit",
                    fontSize = 18.sp
                )
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen,
                    contentColor = PureWhite
                )
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 18.sp
                )
            }
        }
    )
}