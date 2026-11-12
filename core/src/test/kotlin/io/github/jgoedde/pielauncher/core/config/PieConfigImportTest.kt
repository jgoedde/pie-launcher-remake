package io.github.jgoedde.pielauncher.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieConfigImportTest {
    private val dialer = LaunchableApp("com.android.dialer", "com.android.dialer.Main", "Phone")
    private val camera = LaunchableApp("app.grapheneos.camera", "app.grapheneos.camera.Main", "Camera")

    private val device =
        DeviceTargets(
            activities = listOf(dialer, camera),
            defaultActivities = mapOf(dialer.packageName to dialer.activity, camera.packageName to camera.activity),
            shortcuts = setOf(ShortcutTarget("org.mozilla.firefox", "https://app.example.org/")),
        )

    private fun accepted(
        json: String,
        targets: DeviceTargets = device,
    ): PieConfigImport.Accepted {
        val result = PieConfigImport.check(json, targets)
        assertTrue("expected Accepted, was $result", result is PieConfigImport.Accepted)
        return result as PieConfigImport.Accepted
    }

    @Test
    fun `a config whose targets all resolve is accepted without warnings and counts its Layers and Items`() {
        val result =
            accepted(
                """
                {"version": 1, "baseLayer": "home", "layers": [
                  {"id": "home", "name": "Home", "items": [
                    {"type": "app", "package": "com.android.dialer"},
                    {"type": "shortcut", "package": "org.mozilla.firefox", "shortcutId": "https://app.example.org/"},
                    {"type": "layer", "layer": "media"}
                  ]},
                  {"id": "media", "name": "Media", "items": [{"type": "app", "package": "app.grapheneos.camera"}]}
                ]}
                """,
            )

        assertEquals(emptyList<ConfigWarning>(), result.warnings)
        assertEquals(2, result.layerCount)
        assertEquals(4, result.itemCount)
        assertEquals(listOf("home", "media"), result.config.layers.map { it.id })
    }

    private fun home(vararg items: String) =
        """{"version": 1, "baseLayer": "home", "layers": [{"id": "home", "name": "Home", "items": [${items.joinToString()}]}]}"""

    @Test
    fun `any error rejects the file with every error`() {
        val result = PieConfigImport.check("""{"version": 1, "baseLayer": "nope", "layers": []}""", device)

        assertEquals(
            PieConfigImport.Rejected(
                listOf(
                    ConfigError("$.layers", "must have at least 1 entry"),
                    ConfigError("$.baseLayer", "names no Layer: \"nope\""),
                ),
            ),
            result,
        )
    }

    @Test
    fun `app, activity and Shortcut references that do not resolve are warnings`() {
        val result =
            accepted(
                home(
                    """{"type": "app", "package": "org.example.gone"}""",
                    """{"type": "app", "package": "com.android.dialer", "activity": "com.android.dialer.Gone"}""",
                    """{"type": "shortcut", "package": "org.mozilla.firefox", "shortcutId": "gone"}""",
                    """{"type": "app", "package": "com.android.dialer", "activity": "com.android.dialer.Main"}""",
                ),
            )

        assertEquals(
            listOf(
                ConfigWarning("$.layers[0].items[0]", "app org.example.gone is not installed or does not start a launcher activity"),
                ConfigWarning("$.layers[0].items[1]", "activity com.android.dialer/com.android.dialer.Gone is not a launcher activity"),
                ConfigWarning("$.layers[0].items[2]", "Shortcut \"gone\" of org.mozilla.firefox not found"),
            ),
            result.warnings,
        )
    }

    @Test
    fun `Shortcut references are not checked while Shortcuts cannot be read`() {
        val result =
            accepted(
                home("""{"type": "shortcut", "package": "org.mozilla.firefox", "shortcutId": "gone"}"""),
                device.copy(shortcuts = null),
            )

        assertEquals(emptyList<ConfigWarning>(), result.warnings)
    }

    @Test
    fun `a Layer that no chain of Layer Switches reaches from the Base Layer is a warning`() {
        val dial = """{"type": "app", "package": "com.android.dialer"}"""
        val result =
            accepted(
                """
                {"version": 1, "baseLayer": "home", "layers": [
                  {"id": "home", "name": "Home", "items": [{"type": "layer", "layer": "media"}]},
                  {"id": "media", "name": "Media", "items": [{"type": "layer", "layer": "tools"}]},
                  {"id": "tools", "name": "Tools", "items": [$dial]},
                  {"id": "orphan", "name": "Orphan", "items": [{"type": "layer", "layer": "home"}]}
                ]}
                """,
            )

        assertEquals(
            listOf(ConfigWarning("$.layers[3]", "Layer \"orphan\" is not reachable from the Base Layer \"home\"")),
            result.warnings,
        )
    }

    @Test
    fun `a target named twice in one Layer is a warning, also when one names the default activity`() {
        val result =
            accepted(
                """
                {"version": 1, "baseLayer": "home", "layers": [
                  {"id": "home", "name": "Home", "items": [
                    {"type": "app", "package": "com.android.dialer"},
                    {"type": "layer", "layer": "media"},
                    {"type": "app", "package": "com.android.dialer", "activity": "com.android.dialer.Main"},
                    {"type": "layer", "layer": "media"},
                    {"type": "app", "package": "app.grapheneos.camera"}
                  ]},
                  {"id": "media", "name": "Media", "items": [{"type": "app", "package": "app.grapheneos.camera"}]}
                ]}
                """,
            )

        assertEquals(
            listOf(
                ConfigWarning("$.layers[0].items[2]", "same target as $.layers[0].items[0]"),
                ConfigWarning("$.layers[0].items[3]", "same target as $.layers[0].items[1]"),
            ),
            result.warnings,
        )
    }

    @Test
    fun `an app item without activity whose package has several launcher activities names the launched one and the alternatives`() {
        val main = LaunchableApp("org.example.suite", "org.example.suite.Mail", "Mail")
        val calendar = LaunchableApp("org.example.suite", "org.example.suite.Calendar", "Calendar")
        val notes = LaunchableApp("org.example.suite", "org.example.suite.Notes", "Notes")
        val targets =
            device.copy(
                activities = device.activities + listOf(main, calendar, notes),
                defaultActivities = device.defaultActivities + (main.packageName to main.activity),
            )

        val result =
            accepted(
                home(
                    """{"type": "app", "package": "org.example.suite"}""",
                    """{"type": "app", "package": "org.example.suite", "activity": "org.example.suite.Notes"}""",
                ),
                targets,
            )

        assertEquals(
            listOf(
                ConfigWarning(
                    "$.layers[0].items[0]",
                    "org.example.suite has 3 launcher activities and launches org.example.suite.Mail (Mail); " +
                        "set \"activity\" for another: org.example.suite.Calendar (Calendar), org.example.suite.Notes (Notes)",
                ),
            ),
            result.warnings,
        )
    }
}
