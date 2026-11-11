package io.github.jgoedde.pielauncher.drawer

import android.content.pm.ShortcutInfo
import android.util.DisplayMetrics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val IconSize = 48.dp
private val MenuIconSize = 24.dp

/** What a Drawer Entry and its long-press menu can do. */
interface DrawerEntryActions {
    fun launch(app: LauncherApp)

    suspend fun shortcutsOf(app: LauncherApp): List<ShortcutInfo>

    fun launch(shortcut: ShortcutInfo)

    fun openAppInfo(app: LauncherApp)

    fun uninstall(app: LauncherApp)
}

/**
 * One Drawer Entry: icon + label. Tap launches; long-press opens its menu: the app's Shortcuts,
 * then App info, then Uninstall (not for system apps).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DrawerEntryRow(
    app: LauncherApp,
    highlighted: Boolean,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
) {
    // Null while the menu is closed; the Shortcuts are loaded before it opens so it does not jump.
    var menuShortcuts by remember { mutableStateOf<List<ShortcutInfo>?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Where the current press went down, in this row's coordinates; the menu opens there.
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier
            .onSizeChanged { rowSize = it }
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
                        onClick = { actions.launch(app) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { menuShortcuts = actions.shortcutsOf(app) }
                        },
                    ).padding(PaddingValues(horizontal = 8.dp, vertical = 8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app, iconLoader)
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        menuShortcuts?.let { shortcuts ->
            EntryMenu(app, shortcuts, iconLoader, actions, menuOffset(pressAt, rowSize), onDismiss = { menuShortcuts = null })
        }
    }
}

@Composable
private fun EntryMenu(
    app: LauncherApp,
    shortcuts: List<ShortcutInfo>,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
    offset: DpOffset,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, offset = offset) {
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
            onClick = { run { actions.openAppInfo(app) } },
        )
        if (!app.isSystemApp) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_uninstall)) },
                onClick = { run { actions.uninstall(app) } },
            )
        }
    }
}

/**
 * [DropdownMenu] measures its offset from the anchor's bottom-start corner (start is the right
 * edge in RTL); turn the press position into that so the menu opens under the finger. The menu
 * still flips or shifts on its own when there is no room.
 */
@Composable
private fun menuOffset(
    press: Offset,
    rowSize: IntSize,
): DpOffset {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val x = if (rtl) rowSize.width - press.x else press.x
    return with(LocalDensity.current) { DpOffset(x.toDp(), (press.y - rowSize.height).toDp()) }
}

@Composable
private fun AppIcon(
    app: LauncherApp,
    iconLoader: IconLoader,
) {
    val sizePx = IconSize.toPx()
    val icon: ImageBitmap? by produceState(iconLoader.cached(app, sizePx), app, sizePx) {
        if (value == null) value = iconLoader.originalIcon(app, sizePx)
    }
    IconBox(icon, IconSize)
}

@Composable
private fun ShortcutIcon(
    shortcut: ShortcutInfo,
    iconLoader: IconLoader,
) {
    val sizePx = MenuIconSize.toPx()
    val densityDpi = (LocalDensity.current.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, shortcut, sizePx) {
        value = iconLoader.shortcutIcon(shortcut, sizePx, densityDpi)
    }
    IconBox(icon, MenuIconSize)
}

@Composable
private fun IconBox(
    icon: ImageBitmap?,
    size: Dp,
) {
    Box(Modifier.size(size)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Dp.toPx(): Int = with(LocalDensity.current) { roundToPx() }
