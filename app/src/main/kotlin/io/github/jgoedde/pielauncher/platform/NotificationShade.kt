package io.github.jgoedde.pielauncher.platform

import android.app.Application
import android.app.StatusBarManager
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Pulls down the notification shade through the hidden `StatusBarManager.expandNotificationsPanel()`
 * (needs `EXPAND_STATUS_BAR`). There is deliberately no fallback: if the reflective call fails,
 * [expand] does nothing and the failure is logged once per process.
 */
@Inject
@SingleIn(AppScope::class)
class NotificationShade(
    private val application: Application,
) {
    private var failureLogged = false

    /** Opens the notification shade, animated; does nothing if the platform refuses. */
    fun expand() {
        val statusBar = application.getSystemService(StatusBarManager::class.java)
        if (statusBar == null) {
            logFailureOnce(null)
            return
        }
        try {
            StatusBarManager::class.java.getMethod("expandNotificationsPanel").invoke(statusBar)
        } catch (e: ReflectiveOperationException) {
            logFailureOnce(e)
        } catch (e: SecurityException) {
            logFailureOnce(e)
        }
    }

    private fun logFailureOnce(cause: Throwable?) {
        if (failureLogged) return
        failureLogged = true
        Log.w(TAG, "Cannot expand the notification shade; swipe-down stays inert", cause)
    }

    private companion object {
        const val TAG = "NotificationShade"
    }
}
