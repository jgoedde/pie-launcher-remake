package io.github.jgoedde.pielauncher.platform

import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.util.Log
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads an app's own Shortcuts (manifest and dynamic) for its long-press menu. */
@Inject
class AppShortcuts(
    private val launcherApps: LauncherApps,
) {
    /**
     * The enabled Shortcuts of [app]'s activity: manifest ones first, then dynamic ones, each by
     * the app's rank, at most [MAX_SHORTCUTS]. Empty while the launcher is not the default home app (no Shortcut access).
     */
    suspend fun of(app: LauncherApp): List<ShortcutInfo> =
        withContext(Dispatchers.IO) {
            val query =
                ShortcutQuery()
                    .setQueryFlags(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC)
                    .setPackage(app.packageName)
                    .setActivity(app.componentName)
            try {
                launcherApps
                    .getShortcuts(query, app.info.user)
                    .orEmpty()
                    .filter { it.isEnabled }
                    .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                    .take(MAX_SHORTCUTS)
            } catch (e: SecurityException) {
                Log.w(TAG, "No Shortcut access for ${app.componentName}", e)
                emptyList()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Shortcuts unavailable for ${app.componentName}", e)
                emptyList()
            }
        }

    private companion object {
        const val TAG = "AppShortcuts"

        /** Some apps publish 10 or more; menus show only the app's top ones. */
        const val MAX_SHORTCUTS = 5
    }
}
