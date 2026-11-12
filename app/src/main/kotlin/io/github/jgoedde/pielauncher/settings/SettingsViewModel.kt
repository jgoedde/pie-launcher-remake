package io.github.jgoedde.pielauncher.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jgoedde.pielauncher.config.ConfigExporter
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

    data class Review(
        val accepted: PieConfigImport.Accepted,
    ) : ImportDialog

    data class Rejected(
        val errors: List<ConfigError>,
    ) : ImportDialog
}

/** A Pie Config task whose progress and outcome the task pill shows. */
enum class SettingsTask { Import, ExportConfig, ExportInventory, Reset }

enum class TaskState { Working, Done, Failed }

/** One run of [task]; [run] tells a repeated run of the same task apart. */
data class TaskFeedback(
    val run: Long,
    val task: SettingsTask,
    val state: TaskState,
)

class SettingsViewModel(
    config: StateFlow<PieConfig?>,
    loaded: StateFlow<Boolean>,
    record: Flow<ImportRecord?>,
    apps: StateFlow<List<LauncherApp>>,
    resolver: PieItemResolver,
    private val importer: PieConfigImporter,
    private val exporter: ConfigExporter,
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
        dialog.value = null
        track(SettingsTask.Import) { importer.apply(review.accepted) }
    }

    fun dismissImport() {
        dialog.value = null
    }

    private val confirmingReset = MutableStateFlow(false)

    /** The Reset to Default Config confirmation (PRD 10.3); it stays open across "Export first". */
    val resetConfirmation: StateFlow<Boolean> = confirmingReset.asStateFlow()

    private val feedback = MutableStateFlow<TaskFeedback?>(null)

    /** The latest task, for the task pill; null until the first one. */
    val taskFeedback: StateFlow<TaskFeedback?> = feedback.asStateFlow()

    private var runs = 0L

    fun exportConfig(uri: Uri) = track(SettingsTask.ExportConfig) { exporter.exportConfig(uri) }

    fun exportInventory(uri: Uri) = track(SettingsTask.ExportInventory) { exporter.exportInventory(uri) }

    fun askReset() {
        confirmingReset.value = true
    }

    fun confirmReset() {
        if (!confirmingReset.value) return
        confirmingReset.value = false
        track(SettingsTask.Reset) { importer.resetToDefault() }
    }

    fun dismissReset() {
        confirmingReset.value = false
    }

    private fun track(
        task: SettingsTask,
        work: suspend () -> Boolean,
    ) {
        val run = ++runs
        feedback.value = TaskFeedback(run, task, TaskState.Working)
        viewModelScope.launch {
            feedback.value = TaskFeedback(run, task, if (work()) TaskState.Done else TaskState.Failed)
        }
    }

    private fun describe(item: PieItem): String =
        when (item) {
            is PieItem.App -> item.activity?.let { "${item.packageName}/$it" } ?: item.packageName
            is PieItem.Shortcut -> "Shortcut ${item.shortcutId} of ${item.packageName}"
            is PieItem.LayerSwitch -> "Layer Switch to ${item.layer}"
        }
}
