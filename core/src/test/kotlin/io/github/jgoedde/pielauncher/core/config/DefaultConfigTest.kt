package io.github.jgoedde.pielauncher.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultConfigTest {
    private fun app(
        packageName: String,
        label: String,
        activity: String = "$packageName.Main",
    ) = LaunchableApp(packageName, activity, label)

    private val dialer = app("com.android.dialer", "Phone")
    private val messaging = app("com.android.messaging", "Messages")
    private val camera = app("app.grapheneos.camera", "Camera")
    private val browser = app("app.vanadium.browser", "Vanadium")
    private val settings = app("com.android.settings", "Settings")

    private val others =
        listOf(
            app("org.example.zebra", "zebra"),
            app("org.example.apple", "Apple"),
            app("org.example.mango", "Mango"),
            app("org.example.banana", "banana"),
        )

    private fun packagesOf(config: PieConfig) = config.base.items.map { (it as PieItem.App).packageName }

    @Test
    fun `one Base Layer home in primary, defaults first, then other apps alphabetically up to 8 items`() {
        val config =
            DefaultConfig.build(
                defaults = listOf(dialer.packageName, messaging.packageName, camera.packageName, browser.packageName, settings.packageName),
                launchable = others + listOf(settings, browser, camera, messaging, dialer),
            )!!

        assertEquals("home", config.baseLayer)
        assertEquals(1, config.layers.size)
        assertEquals("primary", config.base.color)
        assertEquals(
            listOf(
                "com.android.dialer",
                "com.android.messaging",
                "app.grapheneos.camera",
                "app.vanadium.browser",
                "com.android.settings",
                "org.example.apple",
                "org.example.banana",
                "org.example.mango",
            ),
            packagesOf(config),
        )
    }

    @Test
    fun `defaults that did not resolve, are not launchable or repeat are skipped`() {
        val config =
            DefaultConfig.build(
                defaults = listOf(null, browser.packageName, "com.example.not.launchable", browser.packageName),
                launchable = listOf(browser) + others,
            )!!

        assertEquals("app.vanadium.browser", packagesOf(config).first())
        assertEquals(1, packagesOf(config).count { it == "app.vanadium.browser" })
        assertEquals(5, config.base.items.size)
    }

    @Test
    fun `items name the activity only when the package has several launcher activities`() {
        val config =
            DefaultConfig.build(
                defaults = emptyList(),
                launchable =
                    listOf(
                        app("org.example.multi", "Multi B", "org.example.multi.B"),
                        app("org.example.multi", "Multi A", "org.example.multi.A"),
                        camera,
                    ),
            )!!

        assertEquals(
            listOf(
                PieItem.App("app.grapheneos.camera"),
                PieItem.App("org.example.multi", "org.example.multi.A"),
                PieItem.App("org.example.multi", "org.example.multi.B"),
            ),
            config.base.items,
        )
    }

    @Test
    fun `the result is a valid Pie Config`() {
        val config = DefaultConfig.build(defaults = listOf(dialer.packageName), launchable = others + dialer)!!

        assertTrue(PieConfigCodec.decode(PieConfigCodec.encode(config)) is PieConfigDecoding.Decoded)
    }

    @Test
    fun `with no launchable apps there is no Default Config`() {
        assertNull(DefaultConfig.build(defaults = listOf("com.android.dialer"), launchable = emptyList()))
    }
}
