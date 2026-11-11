package io.github.jgoedde.pielauncher.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.core.drawer.DrawerSearch
import io.github.jgoedde.pielauncher.core.drawer.IndexLetter
import io.github.jgoedde.pielauncher.core.drawer.alphabetIndex
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/** The App Drawer's entries, prepared off the main thread whenever the apps change. */
class DrawerViewModel(
    apps: StateFlow<List<LauncherApp>>,
    locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    val entries: StateFlow<DrawerEntries> =
        apps
            .map { DrawerEntries(it, locale()) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerEntries(emptyList(), Locale.ROOT))
}

/**
 * Drawer Entries ready for display: [search] ranks them per keystroke (cheap enough for the main
 * thread); [letters] is the fast-scroller index of the unsearched, alphabetical list.
 */
class DrawerEntries(
    apps: List<LauncherApp>,
    locale: Locale,
) {
    private val ranking = DrawerSearch(apps, locale, LauncherApp::label)
    val letters: List<IndexLetter> = alphabetIndex(ranking.search(""), locale, LauncherApp::label)

    /** Entries matching [query], best hit first; all entries alphabetically for a blank query. */
    fun search(query: String): List<LauncherApp> = ranking.search(query)
}
