package io.github.jgoedde.pielauncher.config

import android.app.Application
import android.content.pm.LauncherApps
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.config.DefaultConfig
import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieConfigCodec
import io.github.jgoedde.pielauncher.core.config.PieConfigDecoding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.Collator

/**
 * The active Pie Config. The JSON file in internal storage is the source of truth (ADR 0002); it is
 * decoded on start. On first start, when there is no file yet, the Default Config is generated and
 * written in normalized form, then treated exactly like an imported file.
 */
@SingleIn(AppScope::class)
@Inject
class PieConfigStore(
    application: Application,
    private val launcherApps: LauncherApps,
    private val defaultApps: DefaultApps,
    appScope: CoroutineScope,
) {
    private val file = AtomicFile(File(application.filesDir, FILE_NAME))

    private val current = MutableStateFlow<PieConfig?>(null)

    /** Null until the file is decoded, and while it cannot be. */
    val config: StateFlow<PieConfig?> = current.asStateFlow()

    init {
        appScope.launch(Dispatchers.IO) { current.value = load() }
    }

    private fun load(): PieConfig? {
        if (!file.baseFile.exists()) {
            val generated = generateDefault() ?: return null
            write(PieConfigCodec.encode(generated))
        }
        val text =
            try {
                file.readFully().decodeToString()
            } catch (e: IOException) {
                Log.e(TAG, "Cannot read the Pie Config", e)
                return null
            }
        return when (val decoded = PieConfigCodec.decode(text)) {
            is PieConfigDecoding.Decoded -> decoded.config
            is PieConfigDecoding.Rejected -> {
                Log.e(TAG, "Stored Pie Config is invalid: ${decoded.errors.joinToString { "${it.path}: ${it.message}" }}")
                null
            }
        }
    }

    private fun generateDefault(): PieConfig? {
        val launchable =
            launcherApps.getActivityList(null, Process.myUserHandle()).map {
                LaunchableApp(it.componentName.packageName, it.componentName.className, it.label.toString())
            }
        val collator = Collator.getInstance()
        val config = DefaultConfig.build(defaultApps.packages(), launchable) { a, b -> collator.compare(a, b) }
        if (config == null) Log.w(TAG, "No launchable apps yet; the Default Config is generated on a later start")
        return config
    }

    private fun write(text: String) {
        val out = file.startWrite()
        try {
            out.write(text.encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Cannot write the Pie Config", e)
        }
    }

    private companion object {
        const val TAG = "PieConfigStore"
        const val FILE_NAME = "pie-config.json"
    }
}
