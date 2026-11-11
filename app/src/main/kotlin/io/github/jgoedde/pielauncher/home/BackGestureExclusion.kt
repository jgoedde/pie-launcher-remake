package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * While [enabled], both side edges of this element are excluded from the system back gesture
 * over its full height, each strip as wide as the left/right `systemGestures` inset. Page swipes
 * and Pie touches can then start at the very edge. The home activity is exempt from the system's
 * 200 dp cap on excluded height.
 */
@Composable
fun Modifier.excludesBackGestureAtEdges(enabled: Boolean): Modifier {
    if (!enabled) return this
    val insets = WindowInsets.systemGestures
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = insets.getLeft(density, direction).toFloat()
    val right = insets.getRight(density, direction).toFloat()
    return this
        .systemGestureExclusion { Rect(0f, 0f, left, it.size.height.toFloat()) }
        .systemGestureExclusion { Rect(it.size.width - right, 0f, it.size.width.toFloat(), it.size.height.toFloat()) }
}
