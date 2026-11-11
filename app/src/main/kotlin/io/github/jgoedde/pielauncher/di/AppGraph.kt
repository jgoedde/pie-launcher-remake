package io.github.jgoedde.pielauncher.di

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.room.Room
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.Clock
import io.github.jgoedde.pielauncher.drawer.NewBadges
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.platform.AppLauncher
import io.github.jgoedde.pielauncher.platform.AppShortcuts
import io.github.jgoedde.pielauncher.platform.AppsRepository
import io.github.jgoedde.pielauncher.platform.LauncherDatabase
import io.github.jgoedde.pielauncher.platform.ShortcutPins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

@DependencyGraph(AppScope::class)
interface AppGraph {
    val clock: Clock
    val appsRepository: AppsRepository
    val appLauncher: AppLauncher
    val appShortcuts: AppShortcuts
    val iconLoader: IconLoader
    val shortcutPins: ShortcutPins
    val newBadges: NewBadges
    val appScope: CoroutineScope

    @Provides
    fun provideClock(): Clock = Clock.Wall

    @Provides
    fun provideLauncherApps(application: Application): LauncherApps = application.getSystemService(LauncherApps::class.java)

    @Provides
    fun provideUserManager(application: Application): UserManager = application.getSystemService(UserManager::class.java)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(application: Application): LauncherDatabase =
        Room.databaseBuilder(application, LauncherDatabase::class.java, "launcher.db").build()

    /** Lives as long as the process; hosts app-wide flows such as the apps repository. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppScope(): CoroutineScope = MainScope()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}
