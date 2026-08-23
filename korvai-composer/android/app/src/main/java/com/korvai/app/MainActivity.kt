package com.korvai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.korvai.app.ui.ComposerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KorvaiTheme {
                val vm: KorvaiViewModel = viewModel()
                var crashLog by remember { mutableStateOf(KorvaiViewModel.readCrashLog(application)) }
                if (crashLog != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Previous run crashed") },
                        text = {
                            Text(
                                "The app recorded this before closing (please screenshot and share it):\n\n" +
                                    (crashLog ?: ""),
                                fontSize = 11.sp,
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                KorvaiViewModel.clearCrashLog(application)
                                crashLog = null
                            }) { Text("Clear & continue") }
                        },
                    )
                }
                ComposerScreen(vm)
            }
        }
    }
}

private val Gold = Color(0xFFD4A017)
private val GoldBright = Color(0xFFFACC15)
private val Maroon = Color(0xFF7C2D3E)
private val Ink = Color(0xFF12100E)
private val Panel = Color(0xFF1C1916)

@Composable
fun KorvaiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(primary = GoldBright, secondary = Gold, background = Ink, surface = Panel, tertiary = Maroon)
    } else {
        lightColorScheme(primary = Maroon, secondary = Gold, tertiary = GoldBright)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
