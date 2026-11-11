package io.github.jgoedde.pielauncher.platform

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.provider.Settings
import android.util.Log
import dev.zacsweers.metro.Inject

/** Starts launcher activities, Shortcuts and per-app system screens the way a home app should. */
@Inject
class AppLauncher(
    private val application: Application,
    private val launcherApps: LauncherApps,
) {
    /** Returns false when the app could not be started (e.g. it was uninstalled a moment ago). */
    fun launch(app: LauncherApp): Boolean =
        attempt("launch ${app.componentName}") { launcherApps.startMainActivity(app.componentName, app.info.user, null, null) }

    /** Returns false when the Shortcut could not be started (e.g. it was disabled a moment ago). */
    fun launch(shortcut: ShortcutInfo): Boolean =
        attempt("launch shortcut ${shortcut.`package`}/${shortcut.id}") { launcherApps.startShortcut(shortcut, null, null) }

    /** Opens the system "App info" screen of [app]. */
    fun openAppInfo(app: LauncherApp): Boolean =
        attempt("open app info of ${app.componentName}") {
            launcherApps.startAppDetailsActivity(app.componentName, app.info.user, null, null)
        }

    /** Opens the system "App info" screen of the app owning [shortcut]. */
    fun openAppInfo(shortcut: PinnedShortcut): Boolean =
        attempt("open app info of ${shortcut.packageName}") {
            val activity =
                shortcut.info.activity
                    ?: launcherApps.getActivityList(shortcut.packageName, shortcut.user).firstOrNull()?.componentName
            if (activity != null) {
                launcherApps.startAppDetailsActivity(activity, shortcut.user, null, null)
            } else {
                application.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", shortcut.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

    /** Asks the system to uninstall [app]'s package; the system shows its own confirmation. */
    fun uninstall(app: LauncherApp): Boolean =
        attempt("uninstall ${app.packageName}") {
            application.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    private inline fun attempt(
        what: String,
        start: () -> Unit,
    ): Boolean =
        try {
            start()
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Cannot $what", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot $what", e)
            false
        } catch (e: IllegalStateException) {
            // LauncherApps throws this for Shortcuts while the user is locked.
            Log.w(TAG, "Cannot $what", e)
            false
        }

    private companion object {
        const val TAG = "AppLauncher"
    }
}
