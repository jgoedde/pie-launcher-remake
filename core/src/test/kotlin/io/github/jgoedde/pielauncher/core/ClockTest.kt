package io.github.jgoedde.pielauncher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test
    fun `fixed clock always reports the instant it was created with`() {
        val clock = Clock.fixed(epochMillis = 1_767_225_600_000)

        assertEquals(1_767_225_600_000, clock.nowMillis())
        assertEquals(1_767_225_600_000, clock.nowMillis())
    }
}
