package io.github.jgoedde.pielauncher.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.jgoedde.pielauncher.core.Clock

@DependencyGraph
interface AppGraph {
    val clock: Clock

    @Provides
    fun provideClock(): Clock = Clock.Wall
}
