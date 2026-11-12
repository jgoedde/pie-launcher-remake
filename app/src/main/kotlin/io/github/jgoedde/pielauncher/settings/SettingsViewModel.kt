package io.github.jgoedde.pielauncher.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.config.ImportRecord
import io.github.jgoedde.pielauncher.config.PieConfigImporter
import io.github.jgoedde.pielauncher.core.config.ConfigError
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieConfigImport
import io.github.jgoedde.pielauncher.core.config.PieItem
import io.github.jgoedde.pielauncher.pie.PieItemResolver
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Pie Config category's status row: [record] is null while the Default Config was never
 * replaced; [layerCount] is null while the stored file cannot be decoded.
 */
data class ConfigStatus(
    val layerCount: Int?,
    val record: ImportRecord?,
)

/** A Pie Item of the active Pie Config whose target is missing right now. [item] is 1-based. */
data class UnavailableItem(
    val layerName: String,
    val item: Int,
    val target: String,
)

/** The import confirmation (PRD 10.3), one step at a time. */
sealed interface ImportDialog {
    data object Checking : ImportDialog

    data object Applying : ImportDialog

    data class Review(
        val accepted: PieConfigImport.Accepted,
    ) : ImportDialog

    data class Rejected(
        val errors: List<ConfigError>,
    ) : ImportDialog

    data object ApplyFailed : ImportDialog
}

class SettingsViewModel(
    config: StateFlow<PieConfig?>,
    loaded: StateFlow<Boolean>,
    record: Flow<ImportRecord?>,
    apps: StateFlow<List<LauncherApp>>,
    resolver: PieItemResolver,
    private val importer: PieConfigImporter,
) : ViewModel() {
    /** Null until the Pie Config is decoded. */
    val status: StateFlow<ConfigStatus?> =
        combine(config, record, loaded) { config, record, loaded ->
            if (config == null && !loaded) null else ConfigStatus(config?.layers?.size, record)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val unavailable: StateFlow<List<UnavailableItem>> =
        combine(config, apps) { config, apps ->
            if (config == null) return@combine emptyList()
            val resolved = resolver.resolve(config, apps)
            config.layers.flatMap { layer ->
                layer.items.mapIndexedNotNull { i, item ->
                    if (resolved.isAvailable(item)) null else UnavailableItem(layer.name, i + 1, describe(item))
                }
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dialog = MutableStateFlow<ImportDialog?>(null)
    val importDialog: StateFlow<ImportDialog?> = dialog.asStateFlow()

    /** Checks the picked file; the dialog then shows its summary and warnings, or its errors. */
    fun import(uri: Uri) {
        dialog.value = ImportDialog.Checking
        viewModelScope.launch {
            dialog.value =
                when (val result = importer.check(uri)) {
                    is PieConfigImport.Accepted -> ImportDialog.Review(result)
                    is PieConfigImport.Rejected -> ImportDialog.Rejected(result.errors)
                }
        }
    }

    fun apply() {
        val review = dialog.value as? ImportDialog.Review ?: return
        dialog.value = ImportDialog.Applying
        viewModelScope.launch {
            dialog.value = if (importer.apply(review.accepted)) null else ImportDialog.ApplyFailed
        }
    }

    fun dismissImport() {
        dialog.value = null
    }

    private fun describe(item: PieItem): String =
        when (item) {
            is PieItem.App -> item.activity?.let { "${item.packageName}/$it" } ?: item.packageName
            is PieItem.Shortcut -> "Shortcut ${item.shortcutId} of ${item.packageName}"
            is PieItem.LayerSwitch -> "Layer Switch to ${item.layer}"
        }
}
