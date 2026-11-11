package io.github.jgoedde.pielauncher.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.core.drawer.DrawerSearch
import io.github.jgoedde.pielauncher.core.drawer.IndexLetter
import io.github.jgoedde.pielauncher.core.drawer.alphabetIndex
import io.github.jgoedde.pielauncher.core.shortcuts.MergedPins
import io.github.jgoedde.pielauncher.core.shortcuts.ShortcutKey
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.PinnedShortcut
import io.github.jgoedde.pielauncher.platform.entryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/** One row of the App Drawer: a launchable app or a pinned Shortcut. */
sealed interface DrawerEntry {
    /** Stable across reloads; also the New Badge key. */
    val key: String
    val label: String

    /** Shows the New Badge. */
    val isNew: Boolean

    data class App(
        val app: LauncherApp,
        override val isNew: Boolean,
    ) : DrawerEntry {
        override val key: String get() = app.entryKey
        override val label: String get() = app.label
    }

    data class Shortcut(
        val shortcut: PinnedShortcut,
        override val isNew: Boolean,
    ) : DrawerEntry {
        override val key: String get() = shortcut.key.entryKey
        override val label: String get() = shortcut.label
    }
}

/**
 * The App Drawer's entries, prepared off the main thread whenever the apps, the pinned Shortcuts,
 * the New Badges or the pending removals change.
 */
class DrawerViewModel(
    apps: StateFlow<List<LauncherApp>>,
    pinnedShortcuts: StateFlow<MergedPins<PinnedShortcut>>,
    newBadges: Flow<Set<String>>,
    pendingRemovals: StateFlow<Set<ShortcutKey>>,
    locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    val entries: StateFlow<DrawerEntries> =
        combine(apps, pinnedShortcuts, newBadges, pendingRemovals) { apps, pins, badges, removing ->
            val appEntries = apps.map { DrawerEntry.App(it, isNew = it.entryKey in badges) }
            val shortcutEntries =
                pins.inDrawer
                    .filter { it.key !in removing }
                    .map { DrawerEntry.Shortcut(it, isNew = it.key.entryKey in badges) }
            DrawerEntries(appEntries + shortcutEntries, locale())
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerEntries(emptyList(), Locale.ROOT))
}

/**
 * Drawer Entries ready for display: [search] ranks them per keystroke (cheap enough for the main
 * thread); [letters] is the fast-scroller index of the unsearched, alphabetical list.
 */
class DrawerEntries(
    entries: List<DrawerEntry>,
    locale: Locale,
) {
    private val ranking = DrawerSearch(entries, locale, DrawerEntry::label)
    val letters: List<IndexLetter> = alphabetIndex(ranking.search(""), locale, DrawerEntry::label)

    /** Entries matching [query], best hit first; all entries alphabetically for a blank query. */
    fun search(query: String): List<DrawerEntry> = ranking.search(query)
}
