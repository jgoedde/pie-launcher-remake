package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The dot grid that marks the Pie Zone at rest. */
class DotGridTest {
    @Test
    fun `dots sit one step apart, centred in the area`() {
        // 100 x 40 at step 20: five columns, two rows, 10 px margin left over on every side.
        val centres = DotGrid.centres(width = 100f, height = 40f, step = 20f)

        assertEquals(
            listOf(10f, 30f, 50f, 70f, 90f).flatMap { x -> listOf(x to 10f, x to 30f) },
            centres,
        )
    }
}
