package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeZonesTest {
    @Test
    fun `the lower Widget Zone is one cell row and the upper zone takes what the Pie Zone leaves`() {
        // 400 wide -> 100 px cells. 1000 tall: 600 Pie Zone, 100 lower zone, 300 upper zone.
        val zones = HomeZones.of(widthPx = 400f, heightPx = 1000f, pieZoneHeightPx = 600f)

        assertEquals(100f, zones.lowerHeightPx, 0f)
        assertEquals(300f, zones.upperHeightPx, 0f)
        assertEquals(1, zones.lower.rows)
        assertEquals(3, zones.upper.rows)
        assertEquals(4, zones.upper.columns)
        assertEquals(100f, zones.cellPx, 0f)
    }

    @Test
    fun `a window too short for the Pie Zone leaves an upper zone with no rows`() {
        val zones = HomeZones.of(widthPx = 400f, heightPx = 650f, pieZoneHeightPx = 600f)

        assertEquals(0f, zones.upperHeightPx, 0f)
        assertEquals(0, zones.upper.rows)
    }
}
