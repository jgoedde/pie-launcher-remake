package io.github.jgoedde.pielauncher.pie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Supplies the resolved Pie Config, re-resolved on every app and Shortcut change so Unavailable
 * Items reactivate when their target returns and Shortcut lists stay current. The gesture itself
 * never goes through here (ADR 0001).
 */
class PieViewModel(
    config: StateFlow<PieConfig?>,
    apps: StateFlow<List<LauncherApp>>,
    launcherChanged: Flow<Unit>,
    resolver: PieItemResolver,
) : ViewModel() {
    val pie: StateFlow<ResolvedPie?> =
        combine(config, apps, launcherChanged) { config, apps, _ -> config?.let { resolver.resolve(it, apps) } }
            .conflate()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
