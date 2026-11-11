package io.github.jgoedde.pielauncher.platform

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The single source of launchable apps for the App Drawer, the Pie and the Inventory.
 *
 * Main profile only. The list is reloaded whenever `LauncherApps` reports a package change for
 * that profile, so installs and uninstalls show up immediately, and on a system language change,
 * so labels are re-read and the App Drawer re-sorts.
 */
@SingleIn(AppScope::class)
@Inject
class AppsRepository(
    private val application: Application,
    private val launcherApps: LauncherApps,
    appScope: CoroutineScope,
) {
    private val mainUser: UserHandle = Process.myUserHandle()

    val apps: StateFlow<List<LauncherApp>> =
        packageChanges()
            .conflate()
            .map { launcherApps.getActivityList(null, mainUser).map(::LauncherApp) }
            .flowOn(Dispatchers.IO)
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    /** Emits once right away, then on every package change in the main profile and locale change. */
    private fun packageChanges() =
        callbackFlow {
            val callback =
                object : LauncherApps.Callback() {
                    private fun changed(user: UserHandle) {
                        if (user == mainUser) trySend(Unit)
                    }

                    override fun onPackageAdded(
                        packageName: String,
                        user: UserHandle,
                    ) = changed(user)

                    override fun onPackageRemoved(
                        packageName: String,
                        user: UserHandle,
                    ) = changed(user)

                    override fun onPackageChanged(
                        packageName: String,
                        user: UserHandle,
                    ) = changed(user)

                    override fun onPackagesAvailable(
                        packageNames: Array<out String>,
                        user: UserHandle,
                        replacing: Boolean,
                    ) = changed(user)

                    override fun onPackagesUnavailable(
                        packageNames: Array<out String>,
                        user: UserHandle,
                        replacing: Boolean,
                    ) = changed(user)
                }
            val localeReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(Unit)
                    }
                }
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            application.registerReceiver(
                localeReceiver,
                IntentFilter(Intent.ACTION_LOCALE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
            trySend(Unit)
            awaitClose {
                launcherApps.unregisterCallback(callback)
                application.unregisterReceiver(localeReceiver)
            }
        }
}
