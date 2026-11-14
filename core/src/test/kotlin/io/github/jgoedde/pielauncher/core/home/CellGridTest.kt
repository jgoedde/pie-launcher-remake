package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CellGridTest {
    @Test
    fun `a zone is four square cells wide and spreads leftover height evenly above and below the rows`() {
        // 400 wide -> 100 per cell; 450 tall -> 4 rows, 50 left over.
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 450f, gap = 0f)

        assertEquals(4, grid.columns)
        assertEquals(100f, grid.cellWidth, 0f)
        assertEquals(100f, grid.cellHeight, 0f)
        assertEquals(4, grid.rows)
        assertEquals(25f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a zone exactly one cell tall holds one row with no padding`() {
        val grid = CellGrid.of(zoneWidth = 412f, zoneHeight = 103f, gap = 0f)

        assertEquals(1, grid.rows)
        assertEquals(0f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a row that does not fully fit is dropped rather than squeezed`() {
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 399f, gap = 0f)

        assertEquals(3, grid.rows)
        assertEquals(100f, grid.cellHeight, 0f)
        assertEquals(49.5f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a zone shorter than one cell has no rows`() {
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 60f, gap = 0f)

        assertEquals(0, grid.rows)
        assertEquals(30f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a zone sized to one cell but rounded down to whole pixels still holds that row`() {
        // 1081 px wide -> 270.25 px cells; the zone was laid out as 270 px tall.
        val grid = CellGrid.of(zoneWidth = 1081f, zoneHeight = 270f, gap = 0f)

        assertEquals(1, grid.rows)
    }

    @Test
    fun `cells are separated by the gap, not the zone edges`() {
        // 400 wide, 16 gap: 3 gaps -> 352 for 4 cells of 88. 400 tall: 4 rows need 4*88 + 3*16 = 400.
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 400f, gap = 16f)

        assertEquals(88f, grid.cellWidth, 0f)
        assertEquals(88f, grid.cellHeight, 0f)
        assertEquals(4, grid.rows)
        assertEquals(0f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a row whose gap does not fit is dropped and the leftover spread above and below`() {
        // 88 px cells, 16 gap: 3 rows need 296; 399 is 1 px short of 4 rows, leaving 103.
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 399f, gap = 16f)

        assertEquals(3, grid.rows)
        assertEquals(51.5f, grid.verticalPadding, 0f)
    }

    @Test
    fun `the grid is inset from the zone's side edges and its cells shrink to fit`() {
        // 432 wide, 16 padding each side -> 400 for 4 cells and 3 gaps of 16 -> 88 px cells.
        val grid = CellGrid.of(zoneWidth = 432f, zoneHeight = 400f, gap = 16f, horizontalPadding = 16f)

        assertEquals(88f, grid.cellWidth, 0f)
        assertEquals(88f, grid.cellHeight, 0f)
        assertEquals(CellBounds(left = 16f, top = 0f, width = 88f, height = 88f), grid.bounds(CellRect(0, 0, 1, 1)))
        assertEquals(CellBounds(left = 328f, top = 0f, width = 88f, height = 88f), grid.bounds(CellRect(3, 0, 1, 1)))
    }

    @Test
    fun `a widget's bounds cover its cells and the gaps between them`() {
        // 4 rows of 88 + 3 gaps = 400 -> 25 above and below.
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 450f, gap = 16f)

        assertEquals(CellBounds(left = 104f, top = 129f, width = 192f, height = 88f), grid.bounds(CellRect(1, 1, 2, 1)))
        assertEquals(CellBounds(left = 0f, top = 25f, width = 400f, height = 400f), grid.bounds(CellRect(0, 0, 4, 4)))
    }
}
