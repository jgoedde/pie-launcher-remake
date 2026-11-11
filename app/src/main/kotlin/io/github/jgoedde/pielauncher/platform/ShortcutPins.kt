package io.github.jgoedde.pielauncher.platform

import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.shortcuts.LivePin
import io.github.jgoedde.pielauncher.core.shortcuts.PinOutcome
import io.github.jgoedde.pielauncher.core.shortcuts.ShortcutKey
import io.github.jgoedde.pielauncher.core.shortcuts.StoredPin
import io.github.jgoedde.pielauncher.core.shortcuts.idsToKeepPinned
import io.github.jgoedde.pielauncher.core.shortcuts.pinOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A pinned Shortcut as the system reports it, with the key the launcher records it under. */
data class PinnedShortcut(
    val info: ShortcutInfo,
    val key: ShortcutKey,
) {
    val packageName: String get() = key.packageName
    val user: UserHandle get() = info.userHandle
    val label: String = info.label()
}

/** The App Drawer key of a pinned Shortcut, as used for its New Badge. */
val ShortcutKey.entryKey: String get() = "shortcut:$userSerial/$packageName/$id"

/**
 * This launcher's pinned Shortcuts: records them in Room on a Pin Request, looks them up live with
 * `FLAG_MATCH_PINNED`, and unpins them on Remove once the undo window has passed (PRD 7.7).
 */
@SingleIn(AppScope::class)
@Inject
class ShortcutPins(
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    database: LauncherDatabase,
    private val appScope: CoroutineScope,
) {
    private val dao = database.pinnedShortcuts()

    val stored: Flow<List<StoredPin>> = dao.observeAll().map { rows -> rows.map { it.toStoredPin() } }

    private val pending = MutableStateFlow<Set<ShortcutKey>>(emptySet())
    private val removals = mutableMapOf<ShortcutKey, Job>()

    /** Removed Shortcuts still inside their undo window; the App Drawer hides them already. */
    val pendingRemovals: StateFlow<Set<ShortcutKey>> = pending.asStateFlow()

    fun keyOf(info: ShortcutInfo) = ShortcutKey(info.`package`, info.id, userManager.getSerialNumberForUser(info.userHandle))

    /** Records [info] after its Pin Request was accepted; refreshes the label of a known one. */
    suspend fun record(info: ShortcutInfo): PinOutcome {
        val key = keyOf(info)
        val outcome = pinOutcome(dao.all().map { it.toStoredPin().key }, key)
        dao.upsert(listOf(PinnedShortcutRow.of(StoredPin(key, info.label()))))
        return outcome
    }

    suspend fun store(pins: List<StoredPin>) = dao.upsert(pins.map(PinnedShortcutRow::of))

    /**
     * Every Shortcut the system reports pinned by this launcher, across profiles. A profile whose
     * lookup fails (locked, or no Shortcut access) contributes nothing; its records stay.
     */
    suspend fun livePins(): List<LivePin<PinnedShortcut>> =
        withContext(Dispatchers.IO) {
            launcherApps.profiles.flatMap { user ->
                try {
                    pinnedIn(user).map { info ->
                        val pinned = PinnedShortcut(info, keyOf(info))
                        LivePin(pinned.key, pinned.label, info.isEnabled, pinned)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "No Shortcut access for $user", e)
                    emptyList()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Shortcuts unavailable for $user", e)
                    emptyList()
                }
            }
        }

    /** Hides [key] from the App Drawer now; unpins it after [UNDO_WINDOW_MILLIS] unless [undoRemoval] comes first. */
    fun remove(key: ShortcutKey) {
        if (key in removals) return
        pending.update { it + key }
        removals[key] =
            appScope.launch {
                delay(UNDO_WINDOW_MILLIS)
                try {
                    unpin(key)
                } finally {
                    removals.remove(key)
                    pending.update { it - key }
                }
            }
    }

    fun undoRemoval(key: ShortcutKey) {
        removals.remove(key)?.cancel()
        pending.update { it - key }
    }

    /**
     * Android deletes a purely pinned Shortcut once it is unpinned, so this is final. The ids kept
     * pinned come from the records and a fresh lookup together, never from a lookup alone that may
     * have come back short. If that lookup fails, nothing is unpinned and the entry comes back.
     */
    private suspend fun unpin(key: ShortcutKey) {
        val user = userManager.getUserForSerialNumber(key.userSerial)
        if (user == null) {
            // The profile is gone, and its Shortcuts with it.
            dao.delete(key.packageName, key.id, key.userSerial)
            return
        }
        try {
            withContext(Dispatchers.IO) {
                val live = pinnedIn(user, key.packageName).map(::keyOf)
                val keep = idsToKeepPinned(key, dao.all().map { it.toStoredPin().key }, live)
                launcherApps.pinShortcuts(key.packageName, keep, user)
            }
            dao.delete(key.packageName, key.id, key.userSerial)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot unpin $key", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot unpin $key", e)
        }
    }

    private fun pinnedIn(
        user: UserHandle,
        packageName: String? = null,
    ): List<ShortcutInfo> {
        val query = ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
        if (packageName != null) query.setPackage(packageName)
        return launcherApps.getShortcuts(query, user).orEmpty()
    }

    companion object {
        /** How long a removed pinned Shortcut can still be restored. */
        const val UNDO_WINDOW_MILLIS = 5_000L
        private const val TAG = "ShortcutPins"
    }
}

private fun ShortcutInfo.label(): String = (shortLabel ?: longLabel ?: id).toString()
