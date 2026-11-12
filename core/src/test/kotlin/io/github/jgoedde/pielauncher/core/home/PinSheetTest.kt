package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PinSheetTest {
    private val grids = mapOf(WidgetZoneId.HomeUpper to grid(rows = 2), WidgetZoneId.HomeLower to grid(rows = 1))

    @Test
    fun `a widget with room is offered at the spot it would land on, upper zone first`() {
        val layout = WidgetLayout(grids, emptyList())

        val add = PinSheet.add(layout.add(PIN, SpanBounds.fixed(CellSpan(2, 1)), PinSheet.TARGETS))

        assertEquals(PinAdd.Ready(WidgetZoneId.HomeUpper, CellSpan(2, 1)), add)
    }

    @Test
    fun `a widget that fits nowhere on the Home Page disables Add with no room`() {
        val full =
            listOf(
                LaidOutWidget(1, WidgetZoneId.HomeUpper, CellRect(0, 0, 4, 2), SpanBounds.fixed(CellSpan(4, 2))),
                LaidOutWidget(2, WidgetZoneId.HomeLower, CellRect(0, 0, 4, 1), SpanBounds.fixed(CellSpan(4, 1))),
            )

        val add = PinSheet.add(WidgetLayout(grids, full).add(PIN, SpanBounds.fixed(CellSpan(1, 1)), PinSheet.TARGETS))

        assertEquals(PinAdd.Disabled(Rejection.NoRoom), add)
    }

    @Test
    fun `a widget larger than every Widget Zone disables Add as too large`() {
        val add = PinSheet.add(WidgetLayout(grids, emptyList()).add(PIN, SpanBounds.fixed(CellSpan(4, 3)), PinSheet.TARGETS))

        assertEquals(PinAdd.Disabled(Rejection.TooLarge), add)
    }

    private fun grid(rows: Int) = CellGrid(columns = 4, rows = rows, cellWidth = 100f, cellHeight = 100f, gap = 10f, verticalPadding = 0f)

    private companion object {
        const val PIN = -1
    }
}
