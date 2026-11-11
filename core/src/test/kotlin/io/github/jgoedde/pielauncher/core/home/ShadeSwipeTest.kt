package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeSwipeTest {
    private val slop = 10f

    @Test
    fun `a mostly downward drag past touch slop opens the shade`() {
        assertTrue(ShadeSwipe.opensShade(dx = 4f, dy = 12f, touchSlop = slop))
    }

    @Test
    fun `an upward drag never opens the shade`() {
        assertFalse(ShadeSwipe.opensShade(dx = 0f, dy = -30f, touchSlop = slop))
    }

    @Test
    fun `a drag that is at least as sideways as downward leaves the shade closed`() {
        assertFalse(ShadeSwipe.opensShade(dx = 20f, dy = 20f, touchSlop = slop))
        assertFalse(ShadeSwipe.opensShade(dx = -25f, dy = 20f, touchSlop = slop))
    }

    @Test
    fun `a downward drag still within touch slop is not yet a swipe`() {
        assertFalse(ShadeSwipe.opensShade(dx = 0f, dy = 10f, touchSlop = slop))
    }
}
