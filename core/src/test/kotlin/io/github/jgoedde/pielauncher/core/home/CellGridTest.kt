package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CellGridTest {
    @Test
    fun `a zone is four square cells wide and spreads leftover height evenly above and below the rows`() {
        // 400 wide -> 100 per cell; 450 tall -> 4 rows, 50 left over.
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 450f)

        assertEquals(4, grid.columns)
        assertEquals(100f, grid.cellWidth, 0f)
        assertEquals(100f, grid.cellHeight, 0f)
        assertEquals(4, grid.rows)
        assertEquals(25f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a zone exactly one cell tall holds one row with no padding`() {
        val grid = CellGrid.of(zoneWidth = 412f, zoneHeight = 103f)

        assertEquals(1, grid.rows)
        assertEquals(0f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a row that does not fully fit is dropped rather than squeezed`() {
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 399f)

        assertEquals(3, grid.rows)
        assertEquals(100f, grid.cellHeight, 0f)
        assertEquals(49.5f, grid.verticalPadding, 0f)
    }

    @Test
    fun `a zone shorter than one cell has no rows`() {
        val grid = CellGrid.of(zoneWidth = 400f, zoneHeight = 60f)

        assertEquals(0, grid.rows)
        assertEquals(30f, grid.verticalPadding, 0f)
    }
}
