package io.github.jgoedde.pielauncher

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import io.github.jgoedde.pielauncher.di.AppGraph
import io.github.jgoedde.pielauncher.widgets.logMemoryLimiterExits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PieLauncherApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
        // Starts listening for installs right away, so they get their New Badge.
        graph.newBadges
        // Decodes the Pie Config (or writes the Default Config on first start) before the Pie is needed.
        graph.pieConfigStore
        // Widget ids and layout rows that lost their counterpart while the launcher was not running.
        graph.appScope.launch { graph.widgetHost.reconcile() }
        graph.appScope.launch(Dispatchers.IO) { logMemoryLimiterExits(this@PieLauncherApplication) }
    }
}
