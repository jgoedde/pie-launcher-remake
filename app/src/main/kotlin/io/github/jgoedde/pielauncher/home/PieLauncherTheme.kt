package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Monet colours from the wallpaper; light/dark follows the system dark mode. */
@Composable
fun PieLauncherTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colorScheme, content = content)
}
