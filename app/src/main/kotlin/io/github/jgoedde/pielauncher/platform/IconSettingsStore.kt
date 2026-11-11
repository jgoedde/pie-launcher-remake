package io.github.jgoedde.pielauncher.platform

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.icons.IconSettingChange
import io.github.jgoedde.pielauncher.core.icons.IconSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The Themed Icons switch and Icon Shape, set from the system "Wallpaper & style" picker (PRD 8.4)
 * and kept in Preferences DataStore (ADR 0002). Themed Icons default on, the shape to the circle.
 */
@SingleIn(AppScope::class)
@Inject
class IconSettingsStore(
    application: Application,
) {
    // One DataStore per file and process: this class is the only owner of the file.
    private val store = PreferenceDataStoreFactory.create { application.preferencesDataStoreFile("launcher_settings") }

    val settings: Flow<IconSettings> =
        store.data
            .map { IconSettings(themedIcons = it[THEMED_ICONS] ?: true, shapeKey = it[ICON_SHAPE]) }
            .distinctUntilChanged()

    suspend fun apply(change: IconSettingChange) {
        store.edit {
            when (change) {
                is IconSettingChange.ThemedIcons -> it[THEMED_ICONS] = change.on
                is IconSettingChange.Shape -> it[ICON_SHAPE] = change.key
            }
        }
    }

    private companion object {
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
    }
}
