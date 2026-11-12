package io.github.jgoedde.pielauncher.core.config

/**
 * Where an IDE finds the JSON Schema for the Pie Configs this app writes: `spec/` at the release
 * tag the app was built from, so the schema matches the format the installed app reads.
 */
object PieConfigSchema {
    /** [versionTag] is the build's release tag (`vX.Y.Z`); null for untagged builds, which use `main`. */
    fun url(versionTag: String?): String =
        "https://raw.githubusercontent.com/jgoedde/pie-launcher-remake/${versionTag ?: "main"}/spec/pie-config.schema.json"
}
