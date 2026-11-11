package io.github.jgoedde.pielauncher.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.core.drawer.sortedForDrawer
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class DrawerViewModel(
    apps: StateFlow<List<LauncherApp>>,
    locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    /** Drawer Entries in display order. */
    val entries: StateFlow<List<LauncherApp>> =
        apps
            .map { it.sortedForDrawer(locale()) { app -> app.label } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
