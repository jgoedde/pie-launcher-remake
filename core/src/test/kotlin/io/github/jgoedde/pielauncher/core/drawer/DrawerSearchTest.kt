package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DrawerSearchTest {
    private fun search(
        query: String,
        vararg labels: String,
    ): List<String> = DrawerSearch(labels.toList(), Locale.ENGLISH) { it }.search(query)

    @Test
    fun `a blank query lists every entry alphabetically`() {
        assertEquals(listOf("Camera", "Files", "Maps"), search("  ", "Maps", "Camera", "Files"))
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(listOf("Camera"), search(" cam ", "Camera", "Files"))
    }

    @Test
    fun `label prefix ranks above substring`() {
        assertEquals(listOf("Camera", "Scamp"), search("cam", "Scamp", "Camera", "Files"))
    }

    @Test
    fun `word prefix ranks between label prefix and substring`() {
        assertEquals(
            listOf("Maps", "Google Maps", "Amaps"),
            search("map", "Amaps", "Google Maps", "Maps"),
        )
    }

    @Test
    fun `word initials match in the word tier`() {
        assertEquals(listOf("Gmail", "Google Maps"), search("gm", "Google Maps", "Gmail", "Calendar"))
    }

    @Test
    fun `matching ignores case and accents on both sides`() {
        assertEquals(listOf("Ärzte"), search("ARZ", "Café", "Ärzte"))
        assertEquals(listOf("Café"), search("cafè", "Café", "Ärzte"))
    }

    @Test
    fun `typos match in the fuzzy tier, after substring hits`() {
        assertEquals(listOf("Spotify"), search("sptoify", "Spotify", "Settings"))
        assertEquals(listOf("Signal", "Gnarl"), search("gnal", "Gnarl", "Signal", "Maps"))
    }
}
