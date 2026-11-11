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
    val index: StateFlow<DrawerIndex> =
        apps
            .map { DrawerIndex(it, locale()) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerIndex(emptyList(), Locale.ROOT))
}

/**
 * Drawer Entries ready for display: [search] ranks them per keystroke (cheap enough for the main
 * thread), [all] is the alphabetical list and [letters] its fast-scroller index.
 */
class DrawerIndex(
    apps: List<LauncherApp>,
    locale: Locale,
) {
    val search = DrawerSearch(apps, locale, LauncherApp::label)
    val all: List<LauncherApp> = search.search("")
    val letters: List<IndexLetter> = alphabetIndex(all, locale, LauncherApp::label)
}
