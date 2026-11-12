package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsEntryTest {
    @Test
    fun `an app with a picker widget offers the Widgets entry`() {
        assertTrue(offersWidgets("com.clock", listOf("com.weather", "com.clock")))
    }

    @Test
    fun `an app without picker widgets has no Widgets entry`() {
        assertFalse(offersWidgets("com.notes", listOf("com.weather", "com.clock")))
    }

    @Test
    fun `no app offers the Widgets entry when no provider is installed`() {
        assertFalse(offersWidgets("com.clock", emptyList()))
    }
}
