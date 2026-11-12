package io.github.jgoedde.pielauncher.config

import android.app.Application
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.os.UserManager
import android.util.Log
import dev.zacsweers.metro.Inject
import io.github.jgoedde.pielauncher.core.Clock
import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import io.github.jgoedde.pielauncher.core.inventory.Inventory
import io.github.jgoedde.pielauncher.core.inventory.InventoryShortcut
import io.github.jgoedde.pielauncher.core.inventory.ShortcutKind
import io.github.jgoedde.pielauncher.platform.AppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Collator

/** The exports of PRD 9.5, each a single file written to a document the user created through SAF. */
@Inject
class ConfigExporter(
    private val application: Application,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val apps: AppsRepository,
    private val store: PieConfigStore,
    private val clock: Clock,
) {
    /** A byte copy of the stored, normalized Pie Config. False when it cannot be read or written. */
    suspend fun exportConfig(uri: Uri): Boolean {
        val bytes = store.storedBytes() ?: return false
        return write(uri, bytes)
    }

    /** The Inventory of the main profile, from the apps repository. False when it cannot be written. */
    suspend fun exportInventory(uri: Uri): Boolean {
        val text =
            withContext(Dispatchers.IO) {
                // The repository starts empty; this launcher's own Settings entry makes the real list non-empty.
                val launchable =
                    apps.apps.first { it.isNotEmpty() }.map {
                        LaunchableApp(
                            it.packageName,
                            it.componentName.className,
                            it.label,
                        )
                    }
                val collator = Collator.getInstance()
                Inventory.encode(
                    exportedAtMillis = clock.nowMillis(),
                    apps = launchable,
                    defaultActivities = defaultActivities(launchable),
                    pinned = pinned(),
                    published = published(),
                    labelOrder = { a, b -> collator.compare(a, b) },
                )
            }
        return write(uri, text.encodeToByteArray())
    }

    private fun defaultActivities(launchable: List<LaunchableApp>): Map<String, String> {
        val packageManager = application.packageManager
        return launchable
            .map { it.packageName }
            .distinct()
            .mapNotNull { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.component?.let { pkg to it.className } }
            .toMap()
    }

    /** The launcher's pinned Shortcuts of the main profile, disabled ones included (PRD 7.7). */
    private fun pinned(): List<InventoryShortcut> {
        val mainSerial = userManager.getSerialNumberForUser(Process.myUserHandle())
        return apps.pinnedShortcuts.value.records
            .filter { it.key.userSerial == mainSerial }
            .map { InventoryShortcut(it.key.packageName, it.key.id, it.label, ShortcutKind.Pinned, it.disabled) }
    }

    /** Every app's manifest and dynamic Shortcuts; empty without Shortcut access (not the default home app). */
    private fun published(): List<InventoryShortcut> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = ShortcutQuery().setQueryFlags(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC)
        return try {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty().map { it.toInventory() }
        } catch (e: SecurityException) {
            Log.w(TAG, "No Shortcut access", e)
            emptyList()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Shortcuts unavailable", e)
            emptyList()
        }
    }

    private fun ShortcutInfo.toInventory() =
        InventoryShortcut(
            packageName = `package`,
            shortcutId = id,
            label = (shortLabel ?: longLabel ?: id).toString(),
            kind = if (isDeclaredInManifest) ShortcutKind.Manifest else ShortcutKind.Dynamic,
            disabled = !isEnabled,
        )

    private suspend fun write(
        uri: Uri,
        bytes: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // "wt": truncate, in case the user picked an existing, longer file.
                val stream = application.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("no stream")
                stream.use { it.write(bytes) }
                true
            } catch (e: Exception) {
                if (e !is IOException && e !is SecurityException) throw e
                Log.w(TAG, "Cannot write $uri", e)
                false
            }
        }

    private companion object {
        const val TAG = "ConfigExporter"
    }
}
