package com.purescanner.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours measured from the Figma export. */
object Ui {
    val Background = Color(0xFF2A2A2A)
    val Card = Color(0xFF595959)
    val Paper = Color(0xFFD9D9D9)
}

@Composable
fun PureScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ui.Background,
            surface = Ui.Background,
            primary = Color.White,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
