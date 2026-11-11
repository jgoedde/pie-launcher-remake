package io.github.jgoedde.pielauncher.icons

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads Original Icons (the app's own colours, untinted). Themed Icons replace this later.
 *
 * Cached per [LauncherApp] instance; the apps repository creates new instances after every
 * package change, so updated icons are picked up and stale ones age out of the LRU.
 */
@SingleIn(AppScope::class)
@Inject
class IconLoader(
    private val launcherApps: LauncherApps,
) {
    private val cache = LruCache<Pair<LauncherApp, Int>, ImageBitmap>(MAX_ICONS)

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

    private companion object {
        const val MAX_ICONS = 300
    }
}
