package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RejectionOutcomeTest {
    @Test
    fun `a rejection says too large only when the widget is too large for this home screen`() {
        assertEquals(WidgetAddOutcome.TooLarge, Rejection.TooLarge.outcome)
        assertEquals(WidgetAddOutcome.NoRoom, Rejection.NoRoom.outcome)
        assertEquals(WidgetAddOutcome.NoRoom, Rejection.PieZone.outcome)
    }
}
