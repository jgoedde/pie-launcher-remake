package io.github.jgoedde.pielauncher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

// Long-press menu sizes, copied from M3's DropdownMenu so the custom menu looks the same.
private val MenuVerticalPadding = 8.dp
private val MenuMinWidth = 112.dp
private val MenuMaxWidth = 280.dp

// Edge margin as in M3's menu, keeps the menu off the system bars.
private val MenuEdgeMargin = 48.dp

// Transparent room around the menu card so its shadow is not clipped by the popup window.
private val MenuShadowMargin = 4.dp

/**
 * Places the menu above and to the left of the press, away from a right thumb (PRD 6.6): its end
 * edge at the touch X, its bottom edge on the row's top edge. Physical direction, also in RTL.
 * No room above: flips below the row. No room to the left: shifts right. Otherwise clamps to the
 * window.
 */
private class ThumbMenuPositionProvider(
    private val pressX: Int,
    private val shadowMargin: Int,
    private val edgeMargin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // The content carries [shadowMargin] of transparent room on every side; place the visible
        // card, not the content box.
        val width = popupContentSize.width
        val height = popupContentSize.height
        val maxX = maxOf(-shadowMargin, windowSize.width - width + shadowMargin)
        val x = (anchorBounds.left + pressX - width + shadowMargin).coerceIn(-shadowMargin, maxX)
        val minY = edgeMargin - shadowMargin
        val maxY = maxOf(minY, windowSize.height - edgeMargin - height + shadowMargin)
        val above = anchorBounds.top - height + shadowMargin
        val below = anchorBounds.bottom - shadowMargin
        val y =
            when {
                above >= minY -> above
                below <= maxY -> below
                else -> above.coerceIn(minY, maxY)
            }
        return IntOffset(x, y)
    }
}

/**
 * An M3-looking menu in a [Popup] above and left of a press at [pressX] (in the parent's
 * coordinates; the parent's top edge is the press line), placed by [ThumbMenuPositionProvider].
 * Dismisses on outside tap and back.
 */
@Composable
internal fun ThumbMenu(
    pressX: Int,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val shadowMargin = with(density) { MenuShadowMargin.roundToPx() }
    val edgeMargin = with(density) { MenuEdgeMargin.roundToPx() }
    val positionProvider =
        remember(pressX, shadowMargin, edgeMargin) { ThumbMenuPositionProvider(pressX, shadowMargin, edgeMargin) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(visibleState = visible, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
            Surface(
                shape = MenuDefaults.shape,
                color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
                modifier = Modifier.padding(MenuShadowMargin),
            ) {
                Column(
                    Modifier
                        .padding(vertical = MenuVerticalPadding)
                        .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}
