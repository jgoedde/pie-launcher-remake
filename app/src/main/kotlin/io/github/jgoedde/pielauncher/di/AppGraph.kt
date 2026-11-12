package io.github.jgoedde.pielauncher.di

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.config.ConfigExporter
import io.github.jgoedde.pielauncher.config.ImportRecordStore
import io.github.jgoedde.pielauncher.config.PieConfigImporter
import io.github.jgoedde.pielauncher.config.PieConfigStore
import io.github.jgoedde.pielauncher.core.Clock
import io.github.jgoedde.pielauncher.drawer.NewBadges
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.pie.PieItemResolver
import io.github.jgoedde.pielauncher.platform.AppLauncher
import io.github.jgoedde.pielauncher.platform.AppShortcuts
import io.github.jgoedde.pielauncher.platform.AppsRepository
import io.github.jgoedde.pielauncher.platform.IconSettingsStore
import io.github.jgoedde.pielauncher.platform.LauncherDatabase
import io.github.jgoedde.pielauncher.platform.NotificationShade
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
    val iconSettings: IconSettingsStore
    val shortcutPins: ShortcutPins
    val newBadges: NewBadges
    val notificationShade: NotificationShade
    val pieConfigStore: PieConfigStore
    val pieConfigImporter: PieConfigImporter
    val configExporter: ConfigExporter
    val importRecordStore: ImportRecordStore
    val pieItemResolver: PieItemResolver
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

    /**
     * Launcher settings (ADR 0002): Themed Icons, Icon Shape, last-import metadata. One DataStore
     * per file and process, so every store shares this instance.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsDataStore(application: Application): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { application.preferencesDataStoreFile("launcher_settings") }

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
