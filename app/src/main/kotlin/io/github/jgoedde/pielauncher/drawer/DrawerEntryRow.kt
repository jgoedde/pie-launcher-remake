package io.github.jgoedde.pielauncher.drawer

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.launch

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
    Box {
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
            EntryMenu(app, shortcuts, iconLoader, actions, onDismiss = { menuShortcuts = null })
        }
    }
}

@Composable
private fun EntryMenu(
    app: LauncherApp,
    shortcuts: List<ShortcutInfo>,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
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
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, shortcut, sizePx) {
        value = iconLoader.shortcutIcon(shortcut, sizePx)
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
