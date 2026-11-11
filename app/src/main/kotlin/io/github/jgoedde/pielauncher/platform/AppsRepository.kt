package io.github.jgoedde.pielauncher.platform

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.shortcuts.MergedPins
import io.github.jgoedde.pielauncher.core.shortcuts.mergePins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * The single source of launchable apps and pinned Shortcuts for the App Drawer, the Pie and the
 * Inventory.
 *
 * Apps: main profile only. The list is reloaded whenever `LauncherApps` reports a package change
 * for that profile, so installs and uninstalls show up immediately, and on a system language
 * change, so labels are re-read and the App Drawer re-sorts.
 *
 * Pinned Shortcuts: the records of [ShortcutPins] merged with the live lookup, refreshed on every
 * record change, package change and `onShortcutsChanged`.
 */
@SingleIn(AppScope::class)
@Inject
class AppsRepository(
    private val application: Application,
    private val launcherApps: LauncherApps,
    private val shortcutPins: ShortcutPins,
    appScope: CoroutineScope,
) {
    private val mainUser: UserHandle = Process.myUserHandle()

    private enum class Change { Packages, Shortcuts }

    private val changes = launcherChanges().shareIn(appScope, SharingStarted.Eagerly, replay = 1)

    private val installs = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Packages freshly installed in the main profile (not updates), as they arrive. */
    val installedPackages: Flow<String> = installs.asSharedFlow()

    val apps: StateFlow<List<LauncherApp>> =
        changes
            .filter { it == Change.Packages }
            .conflate()
            .map { launcherApps.getActivityList(null, mainUser).map(::LauncherApp) }
            .flowOn(Dispatchers.IO)
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    /**
     * Pinned Shortcuts: [MergedPins.inDrawer] for the App Drawer, [MergedPins.records] for the
     * Inventory. While records are missing from the live lookup (as right after boot) it looks
     * again a few times; the records themselves are never dropped for it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pinnedShortcuts: StateFlow<MergedPins<PinnedShortcut>> =
        combine(shortcutPins.stored, changes) { stored, _ -> stored }
            .conflate()
            .transformLatest { stored ->
                repeat(LOOKUP_ATTEMPTS) { attempt ->
                    if (attempt > 0) delay(LOOKUP_RETRY_MILLIS)
                    val live = shortcutPins.livePins()
                    val merged = mergePins(stored, live)
                    emit(merged)
                    if (merged.toStore.isNotEmpty()) shortcutPins.store(merged.toStore)
                    val liveKeys = live.mapTo(mutableSetOf()) { it.key }
                    if (stored.all { it.key in liveKeys }) return@transformLatest
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(appScope, SharingStarted.Eagerly, MergedPins(emptyList(), emptyList(), emptyList()))

    /** Emits once right away, then on every relevant package, Shortcut and locale change. */
    private fun launcherChanges() =
        callbackFlow {
            val callback =
                object : LauncherApps.Callback() {
                    private fun changed(user: UserHandle) {
                        if (user == mainUser) trySend(Change.Packages)
                    }

                    override fun onPackageAdded(
                        packageName: String,
                        user: UserHandle,
                    ) {
                        // Updates arrive as onPackageChanged, so this is a fresh install.
                        if (user == mainUser) installs.tryEmit(packageName)
                        changed(user)
                    }

                    override fun onShortcutsChanged(
                        packageName: String,
                        shortcuts: MutableList<ShortcutInfo>,
                        user: UserHandle,
                    ) {
                        trySend(Change.Shortcuts)
                    }

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
                        trySend(Change.Packages)
                    }
                }
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            application.registerReceiver(
                localeReceiver,
                IntentFilter(Intent.ACTION_LOCALE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
            trySend(Change.Packages)
            awaitClose {
                launcherApps.unregisterCallback(callback)
                application.unregisterReceiver(localeReceiver)
            }
        }

    private companion object {
        const val LOOKUP_ATTEMPTS = 4
        const val LOOKUP_RETRY_MILLIS = 5_000L
    }
}
