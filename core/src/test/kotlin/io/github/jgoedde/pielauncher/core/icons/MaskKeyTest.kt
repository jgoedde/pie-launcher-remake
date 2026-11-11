package io.github.jgoedde.pielauncher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskKeyTest {
    private val maps =
        MaskKey("com.google.android.apps.maps", item = "com.google.android.maps.MapsActivity", version = "1234", sizePx = 126)

    @Test
    fun `the same icon at the same size maps to the same file`() {
        assertEquals(maps.fileName, maps.copy().fileName)
    }

    @Test
    fun `a new version, size, item or algorithm maps to another file`() {
        val others =
            listOf(
                maps.copy(version = "1235"),
                maps.copy(sizePx = 63),
                maps.copy(item = "com.google.android.maps.NavActivity"),
                maps.copy(algorithmVersion = maps.algorithmVersion + 1),
            )
        others.forEach { assertNotEquals(maps.fileName, it.fileName) }
    }

    @Test
    fun `a package change invalidates that package's masks only`() {
        assertTrue(isMaskOf(maps.fileName, "com.google.android.apps.maps"))
        assertFalse(isMaskOf(maps.fileName, "com.google.android.apps"))
        assertFalse(isMaskOf(maps.copy(packageName = "com.google.android.apps.mapsx").fileName, "com.google.android.apps.maps"))
    }
}
