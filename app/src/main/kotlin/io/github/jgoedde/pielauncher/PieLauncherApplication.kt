package io.github.jgoedde.pielauncher

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import io.github.jgoedde.pielauncher.di.AppGraph

class PieLauncherApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
        // Starts listening for installs right away, so they get their New Badge.
        graph.newBadges
    }
}
