package io.github.jgoedde.pielauncher.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PieConfigCodecTest {
    private fun decoded(json: String): PieConfig {
        val result = PieConfigCodec.decode(json)
        assertTrue("expected Decoded, was $result", result is PieConfigDecoding.Decoded)
        return (result as PieConfigDecoding.Decoded).config
    }

    @Test
    fun `the spec example decodes into its Layers and Pie Items`() {
        val config = decoded(File(System.getProperty("pie.specDir"), "pie-config.example.json").readText())

        assertEquals("home", config.baseLayer)
        assertEquals(listOf("home", "media", "tools"), config.layers.map { it.id })
        val home = config.layers.first()
        assertEquals(PieColor.Role.Primary, home.effectiveColor)
        assertEquals(PieItem.App("com.android.dialer"), home.items[0])
        assertEquals(PieItem.Shortcut("org.mozilla.firefox", "https://app.example.org/"), home.items[3])
        assertEquals(PieItem.LayerSwitch("media"), home.items[5])
        val tools = config.layers[2]
        assertEquals(PieItem.App("com.android.settings", "com.android.settings.Settings"), tools.items[2])
        assertEquals("build", tools.effectiveIcon)
        assertEquals(PieColor.Hex(0xE8A33D), config.layers[1].effectiveColor)
    }

    private fun errors(json: String): List<ConfigError> {
        val result = PieConfigCodec.decode(json)
        assertTrue("expected Rejected, was $result", result is PieConfigDecoding.Rejected)
        return (result as PieConfigDecoding.Rejected).errors
    }

    private fun config(
        layers: String,
        baseLayer: String = "home",
        version: String = "1",
    ) = """{ "version": $version, "baseLayer": "$baseLayer", "layers": [$layers] }"""

    private fun layer(
        id: String = "home",
        items: String = APP,
        extra: String = "",
    ) = """{ "id": "$id", "name": "Name"$extra, "items": [$items] }"""

    @Test
    fun `an unknown field is an error at its JSON path`() {
        val json = config(layer(items = """{ "type": "app", "package": "com.example.app", "label": "x" }"""))

        assertEquals(listOf("\$.layers[0].items[0].label"), errors(json).map { it.path })
    }

    @Test
    fun `the schema key is allowed`() {
        decoded("""{ "${'$'}schema": "https://example.org/s.json", "version": 1, "baseLayer": "home", "layers": [${layer()}] }""")
    }

    @Test
    fun `a version higher than supported is rejected`() {
        val errors = errors(config(layer(), version = "2"))

        assertEquals(listOf(ConfigError("\$.version", "version 2 is higher than supported (1)")), errors)
    }

    @Test
    fun `a baseLayer naming no Layer is an error`() {
        assertEquals(listOf("\$.baseLayer"), errors(config(layer(), baseLayer = "nope")).map { it.path })
    }

    @Test
    fun `a repeated Layer id is an error at the repeat`() {
        assertEquals(listOf("\$.layers[1].id"), errors(config(layer() + "," + layer())).map { it.path })
    }

    @Test
    fun `a Layer Switch to an unknown Layer or to its own Layer is an error`() {
        val json = config(layer(items = """$APP, { "type": "layer", "layer": "home" }, { "type": "layer", "layer": "gone" }"""))

        assertEquals(
            listOf("\$.layers[0].items[1].layer", "\$.layers[0].items[2].layer"),
            errors(json).map { it.path },
        )
    }

    @Test
    fun `a Layer holds one to twelve Pie Items`() {
        decoded(config(layer(items = List(12) { APP }.joinToString())))
        assertEquals(listOf("\$.layers[0].items"), errors(config(layer(items = List(13) { APP }.joinToString()))).map { it.path })
        assertEquals(listOf("\$.layers[0].items"), errors(config(layer(items = ""))).map { it.path })
    }

    @Test
    fun `schema violations and semantic errors are all reported at once`() {
        val json =
            config(
                layer(
                    items = """{ "type": "app", "package": "nodots" }, { "type": "layer", "layer": "home" }""",
                    extra = ""","color": "red"""",
                ),
                baseLayer = "missing",
            )

        assertEquals(
            setOf("\$.baseLayer", "\$.layers[0].color", "\$.layers[0].items[0].package", "\$.layers[0].items[1].layer"),
            errors(json).map { it.path }.toSet(),
        )
    }

    @Test
    fun `broken JSON is one error at the root`() {
        assertEquals(listOf("\$"), errors("{ \"version\": ").map { it.path })
    }

    @Test
    fun `encoding gives the same config back, pretty-printed, without filling in defaults`() {
        val text = config(layer())
        val config = decoded(text)

        val normalized = PieConfigCodec.encode(config)

        assertEquals(config, decoded(normalized))
        assertTrue(normalized.contains("\n    "))
        assertTrue("color" !in normalized && "icon" !in normalized)
    }

    private companion object {
        const val APP = """{ "type": "app", "package": "com.example.app" }"""
    }
}
