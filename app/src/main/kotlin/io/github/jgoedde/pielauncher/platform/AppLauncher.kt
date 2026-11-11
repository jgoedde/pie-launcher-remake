package io.github.jgoedde.pielauncher.platform

import android.content.ActivityNotFoundException
import android.content.pm.LauncherApps
import android.util.Log
import dev.zacsweers.metro.Inject

/** Starts launcher activities the way a home app should: through `LauncherApps`. */
@Inject
class AppLauncher(
    private val launcherApps: LauncherApps,
) {
    /** Returns false when the app could not be started (e.g. it was uninstalled a moment ago). */
    fun launch(app: LauncherApp): Boolean =
        try {
            launcherApps.startMainActivity(app.componentName, app.info.user, null, null)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot launch ${app.componentName}", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot launch ${app.componentName}", e)
            false
        }

    private companion object {
        const val TAG = "AppLauncher"
    }
}
