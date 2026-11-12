package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAddOrderTest {
    @Test
    fun `a widget tap-added from the lower zone's menu tries the lower zone, then the upper one`() {
        assertEquals(listOf(WidgetZoneId.HomeLower, WidgetZoneId.HomeUpper), WidgetZoneId.addOrder(WidgetZoneId.HomeLower))
    }

    @Test
    fun `a widget tap-added from the upper zone's menu tries the upper zone first`() {
        assertEquals(listOf(WidgetZoneId.HomeUpper, WidgetZoneId.HomeLower), WidgetZoneId.addOrder(WidgetZoneId.HomeUpper))
    }

    @Test
    fun `a widget added with no long-pressed zone goes to the Home Page, upper zone first`() {
        assertEquals(listOf(WidgetZoneId.HomeUpper, WidgetZoneId.HomeLower), WidgetZoneId.addOrder(null))
    }

    @Test
    fun `a layout add in that order lands in the other zone when the long-pressed one is full`() {
        val grids = mapOf(WidgetZoneId.HomeUpper to grid(rows = 3), WidgetZoneId.HomeLower to grid(rows = 1))
        val full = LaidOutWidget(1, WidgetZoneId.HomeLower, CellRect(0, 0, 4, 1), SpanBounds.fixed(CellSpan(4, 1)))
        val layout = WidgetLayout(grids, listOf(full))

        val result = layout.add(2, SpanBounds.fixed(CellSpan(2, 1)), WidgetZoneId.addOrder(WidgetZoneId.HomeLower))

        assertEquals(WidgetZoneId.HomeUpper, (result as LayoutResult.Changed).widget.zone)
    }

    private fun grid(rows: Int) = CellGrid(columns = 4, rows = rows, cellWidth = 100f, cellHeight = 100f, gap = 10f, verticalPadding = 0f)
}
