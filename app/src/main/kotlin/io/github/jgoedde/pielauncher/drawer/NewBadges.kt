package io.github.jgoedde.pielauncher.drawer

import android.content.pm.LauncherApps
import android.os.Process
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.Clock
import io.github.jgoedde.pielauncher.core.drawer.NEW_BADGE_LIFETIME_MILLIS
import io.github.jgoedde.pielauncher.core.drawer.activeAt
import io.github.jgoedde.pielauncher.platform.AppsRepository
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.LauncherDatabase
import io.github.jgoedde.pielauncher.platform.NewBadgeRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The App Drawer key of a launchable app, as used for its New Badge. */
val LauncherApp.entryKey: String get() = "app:${componentName.flattenToString()}"

/**
 * New Badges (PRD 6.3): set for freshly installed apps (not updates) and newly pinned Shortcuts,
 * cleared on an entry's first launch or after [NEW_BADGE_LIFETIME_MILLIS]. Drawer only.
 */
@SingleIn(AppScope::class)
@Inject
class NewBadges(
    database: LauncherDatabase,
    private val clock: Clock,
    private val appScope: CoroutineScope,
    appsRepository: AppsRepository,
    launcherApps: LauncherApps,
) {
    private val dao = database.newBadges()

    /** Entry keys whose badge shows right now; re-evaluated hourly so badges expire on time. */
    val active: Flow<Set<String>> =
        combine(dao.observeAll(), hourlyTicks()) { rows, _ -> rows.map { it.toNewBadge() }.activeAt(clock.nowMillis()) }

    init {
        appScope.launch { dao.deleteAddedUpTo(clock.nowMillis() - NEW_BADGE_LIFETIME_MILLIS) }
        appScope.launch {
            appsRepository.installedPackages.collect { packageName ->
                val activities =
                    withContext(Dispatchers.IO) { launcherApps.getActivityList(packageName, Process.myUserHandle()) }
                add(activities.map { LauncherApp(it).entryKey })
            }
        }
    }

    fun add(entryKeys: List<String>) {
        if (entryKeys.isEmpty()) return
        val now = clock.nowMillis()
        appScope.launch { dao.add(entryKeys.map { NewBadgeRow(it, now) }) }
    }

    /** The entry was launched (from the Drawer, the Pie or search). */
    fun clear(entryKey: String) {
        appScope.launch { dao.delete(entryKey) }
    }

    private fun hourlyTicks() =
        flow {
            while (true) {
                emit(Unit)
                delay(HOUR_MILLIS)
            }
        }

    private companion object {
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
