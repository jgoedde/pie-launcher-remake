package io.github.jgoedde.pielauncher.icons

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.PinnedShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads Original Icons (the app's own colours, untinted). Themed Icons replace this later.
 *
 * App icons are cached per [LauncherApp] instance; the apps repository creates new instances
 * after every package change, so updated icons are picked up and stale ones age out of the LRU.
 * Pinned Shortcut icons are cached per Shortcut version (its last-changed time), so pinning the
 * same id again with a new icon shows the new one.
 */
@SingleIn(AppScope::class)
@Inject
class IconLoader(
    private val launcherApps: LauncherApps,
) {
    private val cache = LruCache<Pair<Any, Int>, ImageBitmap>(MAX_ICONS)

    fun cached(
        app: LauncherApp,
        sizePx: Int,
    ): ImageBitmap? = cache[app to sizePx]

    suspend fun originalIcon(
        app: LauncherApp,
        sizePx: Int,
    ): ImageBitmap =
        cached(app, sizePx) ?: withContext(Dispatchers.IO) {
            app.info
                .getIcon(0)
                .toBitmap(sizePx, sizePx)
                .asImageBitmap()
                .also { cache.put(app to sizePx, it) }
        }

    fun cached(
        shortcut: PinnedShortcut,
        sizePx: Int,
    ): ImageBitmap? = cache[shortcut.iconKey() to sizePx]

    /** A pinned Shortcut's own icon; null when it has none. */
    suspend fun pinnedShortcutIcon(
        shortcut: PinnedShortcut,
        sizePx: Int,
        densityDpi: Int,
    ): ImageBitmap? =
        cached(shortcut, sizePx)
            ?: shortcutIcon(shortcut.info, sizePx, densityDpi)?.also { cache.put(shortcut.iconKey() to sizePx, it) }

    /** The icon of the app owning [packageName] in [user], for the small badge on a pinned Shortcut. */
    suspend fun ownerIcon(
        packageName: String,
        user: UserHandle,
        sizePx: Int,
    ): ImageBitmap? {
        val key = "owner:$packageName/$user" to sizePx
        return cache[key] ?: withContext(Dispatchers.IO) {
            launcherApps
                .getActivityList(packageName, user)
                .firstOrNull()
                ?.getIcon(0)
                ?.toBitmap(sizePx, sizePx)
                ?.asImageBitmap()
                ?.also { cache.put(key, it) }
        }
    }

    /** A Shortcut's own icon, uncached (menus are short-lived); null when it has none. */
    suspend fun shortcutIcon(
        shortcut: ShortcutInfo,
        sizePx: Int,
        densityDpi: Int,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            launcherApps
                .getShortcutIconDrawable(shortcut, densityDpi)
                ?.toBitmap(sizePx, sizePx)
                ?.asImageBitmap()
        }

    private fun PinnedShortcut.iconKey() = key to info.lastChangedTimestamp

    private companion object {
        const val MAX_ICONS = 300
    }
}
