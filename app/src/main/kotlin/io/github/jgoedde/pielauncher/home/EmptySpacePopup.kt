package io.github.jgoedde.pielauncher.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.PopupBounds
import io.github.jgoedde.pielauncher.core.home.TouchPopup
import kotlin.math.roundToInt

private val PopupMinWidth = 200.dp
private val PopupMaxWidth = 280.dp
private val PopupVerticalPadding = 8.dp

// Keeps the popup a little off the system bars and the screen edges.
private val PopupEdgeMargin = 8.dp

// Transparent room around the card so its shadow is not clipped by the popup window.
private val PopupShadowMargin = 4.dp

/** Insets of the area the popup may cover, in window px, measured from each window edge. */
private data class PopupInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Places the popup's card by [TouchPopup] around the anchor's top-left corner (the touch point).
 * The content carries [shadowMargin] of transparent room on every side, so the card, not the
 * content box, is what gets placed.
 */
private class TouchPopupPositionProvider(
    private val insets: PopupInsets,
    private val shadowMargin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds =
            PopupBounds(
                left = insets.left,
                top = insets.top,
                right = windowSize.width - insets.right,
                bottom = windowSize.height - insets.bottom,
            )
        val card =
            TouchPopup.place(
                touchX = anchorBounds.left,
                touchY = anchorBounds.top,
                width = popupContentSize.width - 2 * shadowMargin,
                height = popupContentSize.height - 2 * shadowMargin,
                bounds = bounds,
            )
        return IntOffset(card.x - shadowMargin, card.y - shadowMargin)
    }
}

/** One row of the empty-space popup: a Material icon and its label. */
private enum class EmptySpaceItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    WallpaperStyle(R.string.home_menu_wallpaper_style, R.drawable.palette),
    Widgets(R.string.home_menu_widgets, R.drawable.widgets),
    HomeSettings(R.string.home_menu_home_settings, R.drawable.settings),
}

/**
 * The empty-space popup (#34 stories 1–5), Launcher3's `WorkspaceLongPressOptions` at the touch
 * point [at] (in the parent's coordinates): "Wallpaper & style", "Widgets", "Home settings", in
 * that order. Placed by [TouchPopup]. A tap on an item closes the popup and runs it; an outside
 * tap or back closes it.
 */
@Composable
internal fun EmptySpacePopup(
    at: Offset,
    onDismiss: () -> Unit,
    onWallpaperStyle: () -> Unit,
    onWidgets: () -> Unit,
    onHomeSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val margin = with(density) { PopupEdgeMargin.roundToPx() }
    val shadowMargin = with(density) { PopupShadowMargin.roundToPx() }
    val insets =
        PopupInsets(
            left = safe.getLeft(density, layoutDirection) + margin,
            top = safe.getTop(density) + margin,
            right = safe.getRight(density, layoutDirection) + margin,
            bottom = safe.getBottom(density) + margin,
        )
    val positionProvider = remember(insets, shadowMargin) { TouchPopupPositionProvider(insets, shadowMargin) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val actions =
        mapOf(
            EmptySpaceItem.WallpaperStyle to onWallpaperStyle,
            EmptySpaceItem.Widgets to onWidgets,
            EmptySpaceItem.HomeSettings to onHomeSettings,
        )
    // A zero-size anchor at the press: its top-left corner is the touch point.
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(visibleState = visible, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = MenuDefaults.TonalElevation,
                    shadowElevation = MenuDefaults.ShadowElevation,
                    modifier = Modifier.padding(PopupShadowMargin),
                ) {
                    Column(
                        Modifier
                            .padding(vertical = PopupVerticalPadding)
                            .widthIn(min = PopupMinWidth, max = PopupMaxWidth)
                            .width(IntrinsicSize.Max),
                    ) {
                        EmptySpaceItem.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.label)) },
                                leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                                onClick = {
                                    onDismiss()
                                    actions.getValue(item)()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
