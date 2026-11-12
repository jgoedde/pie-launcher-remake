package io.github.jgoedde.pielauncher.config

import android.app.Application
import android.content.pm.LauncherApps
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.BuildConfig
import io.github.jgoedde.pielauncher.core.config.DefaultConfig
import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieConfigCodec
import io.github.jgoedde.pielauncher.core.config.PieConfigDecoding
import io.github.jgoedde.pielauncher.core.config.PieConfigSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    private val done = MutableStateFlow(false)

    /** True once the first load finished; [config] still null then means the file is broken. */
    val loaded: StateFlow<Boolean> = done.asStateFlow()

    /** Keeps an import from racing the first load. */
    private val lock = Mutex()

    init {
        appScope.launch(Dispatchers.IO) {
            lock.withLock {
                current.value = load()
                done.value = true
            }
        }
    }

    /**
     * Makes [config] the active Pie Config: [normalized] (its normalized file) replaces the stored
     * file, and the Pie picks it up at once. False when the file cannot be written; nothing changes then.
     */
    suspend fun replace(
        config: PieConfig,
        normalized: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                write(normalized).also {
                    if (it) {
                        current.value = config
                        done.value = true
                    }
                }
            }
        }

    /**
     * Regenerates the Default Config (PRD 9.6) and makes it the active Pie Config. False when there
     * are no launchable apps or the file cannot be written; nothing changes then.
     */
    suspend fun resetToDefault(): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val generated = generateDefault() ?: return@withLock false
                write(PieConfigCodec.encode(generated)).also {
                    if (it) {
                        current.value = generated
                        done.value = true
                    }
                }
            }
        }

    /** The stored file as is, for the Pie Config export; null when there is none or it cannot be read. */
    suspend fun storedBytes(): ByteArray? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!file.baseFile.exists()) return@withLock null
                try {
                    file.readFully()
                } catch (e: IOException) {
                    Log.e(TAG, "Cannot read the Pie Config", e)
                    null
                }
            }
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
        return config?.copy(schema = PieConfigSchema.url(BuildConfig.VERSION_TAG))
    }

    /** Atomic: on failure the previous file stays. */
    private fun write(text: String): Boolean {
        val out = file.startWrite()
        return try {
            out.write(text.encodeToByteArray())
            file.finishWrite(out)
            true
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Cannot write the Pie Config", e)
            false
        }
    }

    private companion object {
        const val TAG = "PieConfigStore"
        const val FILE_NAME = "pie-config.json"
    }
}
