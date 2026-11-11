package io.github.jgoedde.pielauncher.platform

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
 * that profile, so installs and uninstalls show up immediately.
 */
@SingleIn(AppScope::class)
@Inject
class AppsRepository(
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

    /** Emits once right away, then on every package change in the main profile. */
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
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            trySend(Unit)
            awaitClose { launcherApps.unregisterCallback(callback) }
        }
}
