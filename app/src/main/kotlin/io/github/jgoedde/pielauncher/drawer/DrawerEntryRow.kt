package io.github.jgoedde.pielauncher.drawer

import android.content.pm.ShortcutInfo
import android.util.DisplayMetrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.icons.LauncherIcon
import io.github.jgoedde.pielauncher.icons.LauncherIconImage
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.PinnedShortcut
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val IconSize = 48.dp
private val MenuIconSize = 24.dp
private val OwnerBadgeSize = 20.dp

// Long-press menu sizes, copied from M3's DropdownMenu so the custom menu looks the same.
private val MenuVerticalPadding = 8.dp
private val MenuMinWidth = 112.dp
private val MenuMaxWidth = 280.dp

// Edge margin as in M3's menu, keeps the menu off the system bars.
private val MenuEdgeMargin = 48.dp

// Transparent room around the menu card so its shadow is not clipped by the popup window.
private val MenuShadowMargin = 4.dp

/** What a Drawer Entry and its long-press menu can do. */
interface DrawerEntryActions {
    fun launch(entry: DrawerEntry)

    suspend fun shortcutsOf(app: LauncherApp): List<ShortcutInfo>

    fun launch(shortcut: ShortcutInfo)

    fun openAppInfo(entry: DrawerEntry)

    fun uninstall(app: LauncherApp)

    /** Removes a pinned Shortcut, undoable for a few seconds. */
    fun remove(shortcut: PinnedShortcut)
}

/**
 * One Drawer Entry: icon + label, plus the New Badge while it is new. Tap launches; long-press
 * opens its menu. For an app: its Shortcuts, then App info, then Uninstall (not for system apps).
 * For a pinned Shortcut: Remove, then App info of the owning app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerEntryRow(
    entry: DrawerEntry,
    highlighted: Boolean,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
) {
    // Null while the menu is closed; the Shortcuts are loaded before it opens so it does not jump.
    var menuShortcuts by remember { mutableStateOf<List<ShortcutInfo>?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Where the current press went down, in this row's coordinates; the menu opens beside it.
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .pointerInput(Unit) {
                // Observe only: the clickable below still handles the press.
                awaitEachGesture { pressAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        RoundedCornerShape(percent = 50),
                    ).combinedClickable(
                        onClick = { actions.launch(entry) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                menuShortcuts =
                                    when (entry) {
                                        is DrawerEntry.App -> actions.shortcutsOf(entry.app)
                                        is DrawerEntry.Shortcut -> emptyList()
                                    }
                            }
                        },
                    ).padding(PaddingValues(horizontal = 8.dp, vertical = 8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (entry) {
                is DrawerEntry.App -> AppIcon(entry.app, iconLoader)
                is DrawerEntry.Shortcut -> PinnedShortcutIcon(entry.shortcut, iconLoader)
            }
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (entry.isNew) {
                Badge { Text(stringResource(R.string.drawer_new_badge)) }
            }
        }
        menuShortcuts?.let { shortcuts ->
            val dismiss = { menuShortcuts = null }
            val shadowMargin = MenuShadowMargin.toPx()
            val edgeMargin = MenuEdgeMargin.toPx()
            val positionProvider =
                remember(pressAt, shadowMargin, edgeMargin) {
                    ThumbMenuPositionProvider(pressAt.x.roundToInt(), shadowMargin, edgeMargin)
                }
            when (entry) {
                is DrawerEntry.App -> AppMenu(entry, shortcuts, iconLoader, actions, positionProvider, onDismiss = dismiss)
                is DrawerEntry.Shortcut -> PinnedShortcutMenu(entry, actions, positionProvider, onDismiss = dismiss)
            }
        }
    }
}

@Composable
private fun PinnedShortcutMenu(
    entry: DrawerEntry.Shortcut,
    actions: DrawerEntryActions,
    positionProvider: PopupPositionProvider,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DrawerMenu(positionProvider, onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_remove)) },
            onClick = { run { actions.remove(entry.shortcut) } },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_app_info)) },
            onClick = { run { actions.openAppInfo(entry) } },
        )
    }
}

@Composable
private fun AppMenu(
    entry: DrawerEntry.App,
    shortcuts: List<ShortcutInfo>,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
    positionProvider: PopupPositionProvider,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DrawerMenu(positionProvider, onDismiss) {
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = {
                    Text(
                        (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { ShortcutIcon(shortcut, iconLoader) },
                onClick = { run { actions.launch(shortcut) } },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_app_info)) },
            onClick = { run { actions.openAppInfo(entry) } },
        )
        if (!entry.app.isSystemApp) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_uninstall)) },
                onClick = { run { actions.uninstall(entry.app) } },
            )
        }
    }
}

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

/** An M3-looking menu in a [Popup] placed by [positionProvider]; dismisses on outside tap and back. */
@Composable
private fun DrawerMenu(
    positionProvider: PopupPositionProvider,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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

@Composable
private fun AppIcon(
    app: LauncherApp,
    iconLoader: IconLoader,
) {
    val sizePx = IconSize.toPx()
    val themed by iconLoader.themedIcons.collectAsState()
    val icon: LauncherIcon? by produceState(iconLoader.cached(app, sizePx), app, sizePx, themed) {
        value = iconLoader.appIcon(app, sizePx)
    }
    LauncherIconImage(icon, IconSize)
}

/** The Shortcut's own icon with a small badge of the owning app at its lower end corner. */
@Composable
private fun PinnedShortcutIcon(
    shortcut: PinnedShortcut,
    iconLoader: IconLoader,
) {
    val sizePx = IconSize.toPx()
    val badgePx = OwnerBadgeSize.toPx()
    val densityDpi = (LocalDensity.current.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    val themed by iconLoader.themedIcons.collectAsState()
    val icon: LauncherIcon? by produceState(iconLoader.cached(shortcut, sizePx), shortcut, sizePx, themed) {
        value = iconLoader.pinnedShortcutIcon(shortcut, sizePx, densityDpi)
    }
    val owner: LauncherIcon? by produceState<LauncherIcon?>(null, shortcut.packageName, shortcut.user, badgePx, themed) {
        value = iconLoader.ownerIcon(shortcut.packageName, shortcut.user, badgePx)
    }
    Box(Modifier.size(IconSize)) {
        LauncherIconImage(icon, IconSize)
        owner?.let {
            LauncherIconImage(it, OwnerBadgeSize, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
    }
}

@Composable
private fun ShortcutIcon(
    shortcut: ShortcutInfo,
    iconLoader: IconLoader,
) {
    val sizePx = MenuIconSize.toPx()
    val densityDpi = (LocalDensity.current.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    val themed by iconLoader.themedIcons.collectAsState()
    val icon: LauncherIcon? by produceState<LauncherIcon?>(null, shortcut, sizePx, themed) {
        value = iconLoader.shortcutIcon(shortcut, sizePx, densityDpi)
    }
    LauncherIconImage(icon, MenuIconSize)
}

@Composable
private fun Dp.toPx(): Int = with(LocalDensity.current) { roundToPx() }
