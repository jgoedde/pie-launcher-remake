package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Test

class DragTargetTest {
    // 400 wide, no gap -> 100 px cells. 1100 tall: upper 0..300 (3 rows), Pie Zone 300..900, lower 900..1100 (2 rows).
    private val zones = HomeZones.of(widthPx = 400f, heightPx = 1100f, pieZoneHeightPx = 600f, cellGapPx = 0f)

    @Test
    fun `the finger picks the zone and the widget's top-left corner snaps to the nearest cell`() {
        val target = zones.dragTarget(fingerX = 200f, fingerY = 150f, left = 140f, top = 60f, span = CellSpan(2, 1))

        assertEquals(DragTarget.Cell(HomeUpper, 1, 1), target)
    }

    @Test
    fun `columns snap against the grid inset by the zone padding`() {
        // 432 wide, 16 padding, 16 gap -> 88 px cells, columns start at 16, 120, 224, 328.
        val padded = HomeZones.of(widthPx = 432f, heightPx = 1100f, pieZoneHeightPx = 600f, cellGapPx = 16f, zonePaddingPx = 16f)

        // 60 is 44 px right of column 0 and 60 px left of column 1.
        assertEquals(DragTarget.Cell(HomeUpper, 0, 0), padded.dragTarget(100f, 50f, left = 60f, top = 0f, span = CellSpan(1, 1)))
        assertEquals(DragTarget.Cell(HomeUpper, 1, 0), padded.dragTarget(150f, 50f, left = 120f, top = 0f, span = CellSpan(1, 1)))
    }

    @Test
    fun `a finger over the Pie Zone targets the Pie Zone`() {
        assertEquals(DragTarget.PieZone, zones.dragTarget(200f, 500f, left = 100f, top = 450f, span = CellSpan(2, 1)))
    }

    @Test
    fun `in the lower zone a widget reaching above it still lands on its row`() {
        val target = zones.dragTarget(fingerX = 50f, fingerY = 950f, left = 0f, top = 750f, span = CellSpan(2, 3))

        assertEquals(DragTarget.Cell(HomeLower, 0, 0), target)
    }

    @Test
    fun `a widget hanging over the zone edge is kept inside it at its span`() {
        val target = zones.dragTarget(fingerX = 390f, fingerY = 290f, left = 330f, top = 250f, span = CellSpan(2, 2))

        assertEquals(DragTarget.Cell(HomeUpper, 2, 1), target)
    }

    @Test
    fun `a finger above or below the Page counts as the nearest zone`() {
        assertEquals(DragTarget.Cell(HomeUpper, 0, 0), zones.dragTarget(10f, -40f, left = 0f, top = -80f, span = CellSpan(1, 1)))
        assertEquals(DragTarget.Cell(HomeLower, 3, 1), zones.dragTarget(390f, 1140f, left = 350f, top = 1100f, span = CellSpan(1, 1)))
    }

    @Test
    fun `the gap between cells counts toward the nearest cell`() {
        // 430 wide, 10 gap -> 100 px cells on a 110 px pitch.
        val gapped = HomeZones.of(widthPx = 430f, heightPx = 1130f, pieZoneHeightPx = 600f, cellGapPx = 10f)

        assertEquals(DragTarget.Cell(HomeUpper, 2, 0), gapped.dragTarget(250f, 50f, left = 170f, top = 0f, span = CellSpan(1, 1)))
    }

    @Test
    fun `on a non-Home Page there is no Pie Zone, the finger is always over the Page's one zone`() {
        val target = zones.dragTarget(fingerX = 200f, fingerY = 500f, left = 100f, top = 450f, span = CellSpan(2, 1), page = 2)

        assertEquals(DragTarget.Cell(WidgetZoneId.Page, 1, 5), target)
    }
}
