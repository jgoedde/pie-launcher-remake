package io.github.jgoedde.pielauncher.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.config.ConfigWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** When the active Pie Config was imported and what that import warned about. */
data class ImportRecord(
    val importedAtMillis: Long,
    val warnings: List<ConfigWarning>,
)

/** The last-import metadata for the Launcher Settings status row, in Preferences DataStore (ADR 0002). */
@SingleIn(AppScope::class)
@Inject
class ImportRecordStore(
    private val store: DataStore<Preferences>,
) {
    /** Null while the Default Config was never replaced by an import. */
    val record: Flow<ImportRecord?> =
        store.data
            .map { prefs -> prefs[IMPORTED_AT]?.let { ImportRecord(it, decode(prefs[WARNINGS])) } }
            .distinctUntilChanged()

    suspend fun save(record: ImportRecord) {
        store.edit {
            it[IMPORTED_AT] = record.importedAtMillis
            it[WARNINGS] = encode(record.warnings)
        }
    }

    private fun encode(warnings: List<ConfigWarning>): String =
        JSONArray(warnings.map { JSONObject().put(PATH, it.path).put(MESSAGE, it.message) }).toString()

    private fun decode(text: String?): List<ConfigWarning> =
        try {
            val array = JSONArray(text ?: return emptyList())
            List(array.length()) { array.getJSONObject(it).let { w -> ConfigWarning(w.getString(PATH), w.getString(MESSAGE)) } }
        } catch (_: JSONException) {
            emptyList()
        }

    private companion object {
        val IMPORTED_AT = longPreferencesKey("config_imported_at")
        val WARNINGS = stringPreferencesKey("config_import_warnings")
        const val PATH = "path"
        const val MESSAGE = "message"
    }
}
