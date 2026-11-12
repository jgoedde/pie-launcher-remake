package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPlacementTest {
    @Test
    fun `an empty zone places a widget in its top-left cell`() {
        val spot = WidgetPlacement.firstFreeSpot(columns = 4, rows = 5, occupied = emptyList(), span = CellSpan(2, 2))

        assertEquals(CellRect(column = 0, row = 0, width = 2, height = 2), spot)
    }

    @Test
    fun `the first free spot is found row by row, left to right`() {
        // Row 0 holds a 3-wide widget; a 2x1 widget no longer fits beside it, so it goes to row 1.
        val occupied = listOf(CellRect(0, 0, 3, 1))

        assertEquals(CellRect(0, 1, 2, 1), WidgetPlacement.firstFreeSpot(4, 3, occupied, CellSpan(2, 1)))
        assertEquals(CellRect(3, 0, 1, 1), WidgetPlacement.firstFreeSpot(4, 3, occupied, CellSpan(1, 1)))
    }

    @Test
    fun `a widget never overlaps an occupied cell of a taller widget`() {
        // A 2x3 widget on the left; a 3x1 widget must go below it.
        val occupied = listOf(CellRect(0, 0, 2, 3))

        assertEquals(CellRect(0, 3, 3, 1), WidgetPlacement.firstFreeSpot(4, 4, occupied, CellSpan(3, 1)))
    }

    @Test
    fun `no spot when the zone is full`() {
        val occupied = listOf(CellRect(0, 0, 4, 1))

        assertNull(WidgetPlacement.firstFreeSpot(4, 1, occupied, CellSpan(1, 1)))
    }

    @Test
    fun `no spot when the widget is larger than the zone`() {
        assertNull(WidgetPlacement.firstFreeSpot(4, 1, emptyList(), CellSpan(2, 2)))
        assertNull(WidgetPlacement.firstFreeSpot(4, 3, emptyList(), CellSpan(5, 1)))
    }

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

    @Test
    fun `a widget whose default size does not fit falls back to its minimum size`() {
        val zone = ZoneSlots("upper", columns = 4, rows = 1, occupied = emptyList())

        val placed = WidgetPlacement.place(listOf(zone), WidgetSize(180, 40, CellSpan(4, 2)), cellPx = 100f)

        assertEquals(Placed("upper", CellRect(0, 0, 2, 1)), placed)
    }

    @Test
    fun `zones are tried in order, and a later zone is used only when the earlier ones are full`() {
        val upper = ZoneSlots("upper", columns = 4, rows = 1, occupied = listOf(CellRect(0, 0, 4, 1)))
        val lower = ZoneSlots("lower", columns = 4, rows = 1, occupied = emptyList())

        assertEquals(Placed("lower", CellRect(0, 0, 2, 1)), WidgetPlacement.place(listOf(upper, lower), WidgetSize(180, 40), 100f))
        val freeUpper = upper.copy(occupied = emptyList())
        assertEquals(Placed("upper", CellRect(0, 0, 2, 1)), WidgetPlacement.place(listOf(freeUpper, lower), WidgetSize(180, 40), 100f))
    }

    @Test
    fun `no placement when even the minimum size fits nowhere`() {
        val zone = ZoneSlots("lower", columns = 4, rows = 1, occupied = emptyList())

        assertNull(WidgetPlacement.place(listOf(zone), WidgetSize(180, 150), cellPx = 100f))
    }
}
