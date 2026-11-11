package io.github.jgoedde.pielauncher.icons

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.icons.ThemePicker
import io.github.jgoedde.pielauncher.platform.IconSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The ThemePicker provider that "Wallpaper & style" talks to, found through the Home Activity's
 * `com.android.launcher3.themedicon.option` and `com.android.launcher3.grid.control` meta-data.
 * The manifest guards it with `BIND_WALLPAPER`. Calls arrive on binder threads, so blocking on
 * DataStore here is fine. `call` (`get_preview`) keeps the default null answer.
 */
class ThemePickerProvider : ContentProvider() {
    private val settings: IconSettingsStore
        get() = (requireContext().applicationContext as PieLauncherApplication).graph.iconSettings

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val rows = ThemePicker.query(uri.path, runBlocking { settings.settings.first() }) ?: return null
        return MatrixCursor(rows.columns.toTypedArray(), rows.rows.size).apply { rows.rows.forEach { addRow(it) } }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val change = ThemePicker.update(uri.path, values?.valueSet()?.associate { it.key to it.value }.orEmpty()) ?: return 0
        runBlocking { settings.apply(change) }
        requireContext().contentResolver.notifyChange(uri, null)
        return 1
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/launcher_grid"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
