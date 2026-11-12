package io.github.jgoedde.pielauncher.core.inventory

import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

/** How an app publishes a Shortcut; a Pie Config references all three the same way. */
@Serializable
enum class ShortcutKind {
    @SerialName("pinned")
    Pinned,

    @SerialName("manifest")
    Manifest,

    @SerialName("dynamic")
    Dynamic,
}

/** One Shortcut as the Inventory lists it. [disabled]: its app disabled it; it is not launchable now. */
data class InventoryShortcut(
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val kind: ShortcutKind,
    val disabled: Boolean = false,
)

/**
 * The Inventory export (PRD 9.5): every launchable app and Shortcut with the identifiers a Pie
 * Config can reference. See `spec/inventory.example.json`.
 */
object Inventory {
    private val json =
        Json {
            prettyPrint = true
            explicitNulls = false
        }

    /**
     * [defaultActivities] maps a package to the activity `getLaunchIntentForPackage` returns, the
     * one an app item without `activity` launches. [pinned] are the launcher's pinned Shortcuts,
     * [published] the apps' manifest and dynamic ones; a Shortcut in both is listed once, with its
     * [published] kind. Each list is sorted by label in [labelOrder].
     */
    fun encode(
        exportedAtMillis: Long,
        apps: List<LaunchableApp>,
        defaultActivities: Map<String, String>,
        pinned: List<InventoryShortcut>,
        published: List<InventoryShortcut>,
        labelOrder: Comparator<String> = String.CASE_INSENSITIVE_ORDER,
    ): String {
        val publishedIds = published.mapTo(mutableSetOf()) { it.packageName to it.shortcutId }
        val shortcuts = published + pinned.filter { (it.packageName to it.shortcutId) !in publishedIds }
        val document =
            Document(
                exportedAt = Instant.ofEpochMilli(exportedAtMillis).truncatedTo(ChronoUnit.SECONDS).toString(),
                apps =
                    apps
                        .sortedWith(compareBy(labelOrder) { it.label })
                        .map { App(it.packageName, it.activity, it.label, defaultActivities[it.packageName] == it.activity) },
                shortcuts =
                    shortcuts
                        .sortedWith(compareBy(labelOrder) { it.label })
                        .map { Shortcut(it.packageName, it.shortcutId, it.label, it.kind, it.disabled.takeIf { d -> d }) },
            )
        return json.encodeToString(Document.serializer(), document)
    }

    @Serializable
    private data class Document(
        val exportedAt: String,
        val apps: List<App>,
        val shortcuts: List<Shortcut>,
    )

    @Serializable
    private data class App(
        @SerialName("package") val packageName: String,
        val activity: String,
        val label: String,
        val default: Boolean,
    )

    @Serializable
    private data class Shortcut(
        @SerialName("package") val packageName: String,
        val shortcutId: String,
        val label: String,
        val kind: ShortcutKind,
        val disabled: Boolean?,
    )
}
