package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeZonesTest {
    @Test
    fun `the lower Widget Zone is two cell rows and the upper zone takes what the Pie Zone leaves`() {
        // 400 wide -> 100 px cells. 1000 tall: 500 Pie Zone, 200 lower zone, 300 upper zone.
        val zones = HomeZones.of(widthPx = 400f, heightPx = 1000f, pieZoneHeightPx = 500f, cellGapPx = 0f)

        assertEquals(200f, zones.lowerHeightPx, 0f)
        assertEquals(300f, zones.upperHeightPx, 0f)
        assertEquals(2, zones.lower.rows)
        assertEquals(3, zones.upper.rows)
        assertEquals(4, zones.upper.columns)
        assertEquals(100f, zones.cellPx, 0f)
    }

    @Test
    fun `a window too short for the Pie Zone leaves an upper zone with no rows`() {
        val zones = HomeZones.of(widthPx = 400f, heightPx = 750f, pieZoneHeightPx = 600f, cellGapPx = 0f)

        assertEquals(0f, zones.upperHeightPx, 0f)
        assertEquals(0, zones.upper.rows)
    }

    @Test
    fun `the gap shrinks the cells and the lower zone stays two cells and one gap tall`() {
        // 400 wide, 16 gap -> 88 px cells. 1000 tall: 300 Pie Zone, 192 lower (2*88 + 16), 508 upper -> 5 rows (5*88 + 4*16 = 504).
        val zones = HomeZones.of(widthPx = 400f, heightPx = 1000f, pieZoneHeightPx = 300f, cellGapPx = 16f)

        assertEquals(88f, zones.cellPx, 0f)
        assertEquals(192f, zones.lowerHeightPx, 0f)
        assertEquals(2, zones.lower.rows)
        assertEquals(5, zones.upper.rows)
    }

    @Test
    fun `widgets are sized against the larger zone's grid, in dp`() {
        // 2 px per dp: 800 wide, 32 px gap -> 176 px cells (88 dp), 16 dp gap; upper 1016 px -> 5 rows.
        val zones = HomeZones.of(widthPx = 800f, heightPx = 2000f, pieZoneHeightPx = 600f, cellGapPx = 32f)

        assertEquals(
            CatalogGrid(columns = 4, rows = 5, cellWidth = 88f, cellHeight = 88f, spacing = 16f),
            zones.catalogGrid(pxPerDp = 2f),
        )
    }

    @Test
    fun `with no room above the Pie Zone the lower zone is the larger one`() {
        val zones = HomeZones.of(widthPx = 400f, heightPx = 750f, pieZoneHeightPx = 600f, cellGapPx = 16f)

        assertEquals(2, zones.catalogGrid(pxPerDp = 1f).rows)
    }
}
