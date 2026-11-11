package io.github.jgoedde.pielauncher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class IconShapesTest {
    @Test
    fun `offers the AOSP Launcher3 shapes with the circle first`() {
        assertEquals(
            listOf("circle", "square", "four_sided_cookie", "seven_sided_cookie", "arch"),
            IconShapes.all.map { it.key },
        )
    }

    @Test
    fun `a stored key picks its Icon Shape`() {
        assertEquals("arch", IconShapes.byKey("arch").key)
    }

    @Test
    fun `no stored key or an unknown one falls back to the circle`() {
        assertEquals("circle", IconShapes.byKey(null).key)
        assertEquals("circle", IconShapes.byKey("sunny").key)
    }
}
