package io.github.jgoedde.pielauncher.core.config

/** A Shortcut on the device that a `shortcut` Pie Item can name. */
data class ShortcutTarget(
    val packageName: String,
    val shortcutId: String,
)

/**
 * What the device offers right now, for the import warnings. [defaultActivities] maps a package to
 * the activity `getLaunchIntentForPackage` returns. [shortcuts] is null while the launcher cannot
 * read Shortcuts (it is not the default home app); Shortcut references are then not checked.
 */
data class DeviceTargets(
    val activities: List<LaunchableApp>,
    val defaultActivities: Map<String, String>,
    val shortcuts: Set<ShortcutTarget>?,
)

/** One import warning: the file is still accepted. [path] is a JSONPath into the file. */
data class ConfigWarning(
    val path: String,
    val message: String,
)

/** The outcome of checking a file picked for import (PRD 9.3), before the user applies it. */
sealed interface PieConfigImport {
    /** Any error rejects the whole file; nothing changes. */
    data class Rejected(
        val errors: List<ConfigError>,
    ) : PieConfigImport

    /** [normalized] is what Apply writes: pretty-printed, same content, `$schema` set to this app's schema. */
    data class Accepted(
        val config: PieConfig,
        val normalized: String,
        val warnings: List<ConfigWarning>,
    ) : PieConfigImport {
        val layerCount: Int get() = config.layers.size
        val itemCount: Int get() = config.layers.sumOf { it.items.size }
    }

    companion object {
        /** [schemaUrl] replaces the file's own `$schema` (see [PieConfigSchema]). */
        fun check(
            text: String,
            targets: DeviceTargets,
            schemaUrl: String,
        ): PieConfigImport =
            when (val decoded = PieConfigCodec.decode(text)) {
                is PieConfigDecoding.Rejected -> Rejected(decoded.errors)
                is PieConfigDecoding.Decoded -> {
                    val config = decoded.config.copy(schema = schemaUrl)
                    Accepted(config, PieConfigCodec.encode(config), ImportWarnings(targets).of(config))
                }
            }
    }
}
