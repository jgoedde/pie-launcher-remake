package io.github.jgoedde.pielauncher.config

import android.app.Application
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.net.Uri
import android.os.Process
import android.util.Log
import dev.zacsweers.metro.Inject
import io.github.jgoedde.pielauncher.BuildConfig
import io.github.jgoedde.pielauncher.core.Clock
import io.github.jgoedde.pielauncher.core.config.ConfigError
import io.github.jgoedde.pielauncher.core.config.DeviceTargets
import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import io.github.jgoedde.pielauncher.core.config.PieConfigImport
import io.github.jgoedde.pielauncher.core.config.PieConfigSchema
import io.github.jgoedde.pielauncher.core.config.ShortcutTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The Pie Config import (PRD 9.3): [check] reads a file picked through SAF and validates it against
 * what the device offers; [apply] makes an accepted file the active Pie Config. Nothing changes
 * until [apply].
 */
@Inject
class PieConfigImporter(
    private val application: Application,
    private val launcherApps: LauncherApps,
    private val store: PieConfigStore,
    private val records: ImportRecordStore,
    private val clock: Clock,
) {
    suspend fun check(uri: Uri): PieConfigImport =
        withContext(Dispatchers.IO) {
            val text =
                try {
                    read(uri)
                } catch (e: Exception) {
                    if (e !is IOException && e !is SecurityException) throw e
                    Log.w(TAG, "Cannot read $uri", e)
                    return@withContext PieConfigImport.Rejected(listOf(ConfigError("$", "cannot read the file: ${e.message}")))
                }
            PieConfigImport.check(text, deviceTargets(), PieConfigSchema.url(BuildConfig.VERSION_TAG))
        }

    /** False when the file cannot be written; the previous Pie Config then stays active. */
    suspend fun apply(accepted: PieConfigImport.Accepted): Boolean {
        if (!store.replace(accepted.config, accepted.normalized)) return false
        records.save(ImportRecord(clock.nowMillis(), accepted.warnings))
        return true
    }

    /** Replaces the active Pie Config with a freshly generated Default Config; false when that fails. */
    suspend fun resetToDefault(): Boolean {
        if (!store.resetToDefault()) return false
        records.clear()
        return true
    }

    private fun read(uri: Uri): String {
        val stream = application.contentResolver.openInputStream(uri) ?: throw IOException("no content")
        val bytes = stream.use { it.readNBytes(MAX_BYTES + 1) }
        if (bytes.size > MAX_BYTES) throw IOException("larger than ${MAX_BYTES / 1024} KiB")
        return bytes.decodeToString()
    }

    private fun deviceTargets(): DeviceTargets {
        val activities =
            launcherApps.getActivityList(null, Process.myUserHandle()).map {
                LaunchableApp(it.componentName.packageName, it.componentName.className, it.label.toString())
            }
        val packageManager = application.packageManager
        val defaults =
            activities.map { it.packageName }.distinct().mapNotNull { pkg ->
                packageManager.getLaunchIntentForPackage(pkg)?.component?.let { pkg to it.className }
            }
        return DeviceTargets(activities, defaults.toMap(), shortcuts())
    }

    /** Every enabled pinned, manifest and dynamic Shortcut; null without Shortcut access (not the default home app). */
    private fun shortcuts(): Set<ShortcutTarget>? {
        if (!launcherApps.hasShortcutHostPermission()) return null
        val query =
            ShortcutQuery().setQueryFlags(
                ShortcutQuery.FLAG_MATCH_PINNED or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC,
            )
        return try {
            launcherApps
                .getShortcuts(query, Process.myUserHandle())
                .orEmpty()
                .filter { it.isEnabled }
                .mapTo(mutableSetOf()) { ShortcutTarget(it.`package`, it.id) }
        } catch (e: SecurityException) {
            Log.w(TAG, "No Shortcut access", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Shortcuts unavailable", e)
            null
        }
    }

    private companion object {
        const val TAG = "PieConfigImporter"

        /** A Pie Config is a few KiB; anything this big is the wrong file. */
        const val MAX_BYTES = 1024 * 1024
    }
}
