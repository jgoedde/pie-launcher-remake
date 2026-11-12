package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun `a widget needs as many cells as its minimum size covers`() {
        // 100 px cells: 180 x 40 px needs 2 x 1 cells; exactly 200 px wide is still 2.
        assertEquals(CellSpan(2, 1), WidgetSize(minWidthPx = 180, minHeightPx = 40).minSpan(cellPx = 100f))
        assertEquals(CellSpan(2, 1), WidgetSize(minWidthPx = 200, minHeightPx = 100).minSpan(cellPx = 100f))
        assertEquals(CellSpan(1, 1), WidgetSize(minWidthPx = 0, minHeightPx = 0).minSpan(cellPx = 100f))
    }

    @Test
    fun `the default size is the provider's target cell size, never below its minimum`() {
        assertEquals(CellSpan(4, 2), WidgetSize(180, 40, targetCells = CellSpan(4, 2)).defaultSpan(100f))
        assertEquals(CellSpan(3, 2), WidgetSize(250, 150, targetCells = CellSpan(1, 1)).defaultSpan(100f))
        assertEquals(CellSpan(2, 1), WidgetSize(180, 40).defaultSpan(100f))
    }
}
