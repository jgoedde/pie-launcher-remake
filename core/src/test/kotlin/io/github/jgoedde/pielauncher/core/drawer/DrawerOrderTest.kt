package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DrawerOrderTest {
    @Test
    fun `Drawer Entries sort alphabetically ignoring case`() {
        val sorted = listOf("signal", "Maps", "camera", "Firefox").sortedForDrawer(Locale.ENGLISH) { it }

        assertEquals(listOf("camera", "Firefox", "Maps", "signal"), sorted)
    }

    @Test
    fun `accented letters sort with their base letter`() {
        val sorted = listOf("Files", "Ärzte", "Éclair", "Drive", "Bank").sortedForDrawer(Locale.GERMAN) { it }

        assertEquals(listOf("Ärzte", "Bank", "Drive", "Éclair", "Files"), sorted)
    }

    @Test
    fun `collation follows the locale's alphabet`() {
        // In Swedish, Ö is its own letter after Z; in German it folds into O.
        val labels = listOf("Örebro", "Zoom", "Opera")

        assertEquals(listOf("Opera", "Zoom", "Örebro"), labels.sortedForDrawer(Locale.forLanguageTag("sv")) { it })
        assertEquals(listOf("Opera", "Örebro", "Zoom"), labels.sortedForDrawer(Locale.GERMAN) { it })
    }
}
