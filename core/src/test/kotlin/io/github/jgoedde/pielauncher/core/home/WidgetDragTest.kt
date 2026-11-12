package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDragTest {
    // Widget 1 (2 x 1) at the top left, widget 2 (2 x 1) to its right.
    private val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 2, 1)))

    @Test
    fun `over free cells the target cells are previewed at once and nothing moves`() {
        val drag = WidgetDrag.pickUp(layout, 1, nowMs = 0)!!.moveTo(DragTarget.Cell(HomeUpper, 1, 2), nowMs = 10)

        assertEquals(HomeUpper to CellRect(1, 2, 2, 1), drag.outline)
        assertEquals(CellRect(2, 0, 2, 1), drag.preview.widget(2)!!.cells)
    }

    @Test
    fun `over an occupied cell neighbours move aside only after the finger rests 650 ms`() {
        val drag = WidgetDrag.pickUp(layout, 1, nowMs = 0)!!.moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 100)

        val early = drag.tick(nowMs = 100 + 649)
        assertNull(early.outline)
        assertEquals(CellRect(2, 0, 2, 1), early.preview.widget(2)!!.cells)
        assertEquals(750L, drag.reorderDueAtMs)

        val rested = drag.tick(nowMs = 100 + 650)
        assertEquals(HomeUpper to CellRect(2, 0, 2, 1), rested.outline)
        assertEquals(CellRect(0, 0, 2, 1), rested.preview.widget(2)!!.cells)
        assertNull(rested.reorderDueAtMs)
    }

    @Test
    fun `moving to another cell restarts the rest and puts moved neighbours back`() {
        val rested =
            WidgetDrag
                .pickUp(layout, 1, nowMs = 0)!!
                .moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 0)
                .tick(nowMs = 650)

        val moved = rested.moveTo(DragTarget.Cell(HomeUpper, 1, 0), nowMs = 700)

        assertNull(moved.outline)
        assertEquals(CellRect(2, 0, 2, 1), moved.preview.widget(2)!!.cells)
        assertEquals(1350L, moved.reorderDueAtMs)
    }

    @Test
    fun `staying on the same cell keeps the rest running`() {
        val drag =
            WidgetDrag
                .pickUp(layout, 1, nowMs = 0)!!
                .moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 0)
                .moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 400)

        assertEquals(650L, drag.reorderDueAtMs)
    }

    @Test
    fun `a drop commits the reflow that was shown`() {
        val rested =
            WidgetDrag
                .pickUp(layout, 1, nowMs = 0)!!
                .moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 0)
                .tick(nowMs = 650)

        val dropped = rested.drop().changed()

        assertEquals(rested.preview.widgets.toSet(), dropped.layout.widgets.toSet())
    }

    @Test
    fun `a drop before the rest still reflows, as the drop would after it`() {
        val drag = WidgetDrag.pickUp(layout, 1, nowMs = 0)!!.moveTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 0)

        val dropped = drag.drop().changed()

        assertEquals(CellRect(2, 0, 2, 1), dropped.layout.widget(1)!!.cells)
        assertEquals(CellRect(0, 0, 2, 1), dropped.layout.widget(2)!!.cells)
    }

    @Test
    fun `over the Pie Zone nothing is previewed and a drop there is rejected`() {
        val drag = WidgetDrag.pickUp(layout, 1, nowMs = 0)!!.moveTo(DragTarget.PieZone, nowMs = 0).tick(nowMs = 1000)

        assertNull(drag.outline)
        assertEquals(LayoutResult.Rejected(Rejection.PieZone), drag.drop())
    }

    @Test
    fun `off the zones (over the drop-target bar) nothing is previewed and a drop leaves the layout as it was`() {
        val drag = WidgetDrag.pickUp(layout, 1, nowMs = 0)!!.moveTo(null, nowMs = 0).tick(nowMs = 1000)

        assertNull(drag.outline)
        assertEquals(layout.widgets, drag.preview.widgets)
        assertEquals(LayoutResult.Unchanged, drag.drop())
    }

    @Test
    fun `a widget not yet on the Page (from the Widget Picker) drags through the same path`() {
        val new = DraggedWidget(7, CellSpan(2, 1), bounds(default = CellSpan(2, 1)))

        val dropped =
            WidgetDrag
                .start(layout, new, nowMs = 0)
                .moveTo(DragTarget.Cell(HomeUpper, 0, 1), nowMs = 0)
                .drop()
                .changed()

        assertEquals(LaidOutWidget(7, HomeUpper, CellRect(0, 1, 2, 1), new.bounds), dropped.widget)
    }
}
