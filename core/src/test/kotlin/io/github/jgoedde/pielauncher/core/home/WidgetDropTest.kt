package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Test

/** A widget dragged in from the Widget Picker: dropped, then bound and configured, then placed (PRD 7.4). */
class WidgetDropTest {
    // Widget 1 (2 x 1) at the top left of the 4 x 3 upper zone.
    private val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)))

    private fun dropNew(
        on: WidgetLayout,
        bounds: SpanBounds,
        target: DragTarget,
    ): WidgetDrop =
        WidgetDrop.of(
            WidgetDrag
                .start(on, DraggedWidget.new(bounds), nowMs = 0)
                .moveTo(target, nowMs = 0)
                .drop()
                .changed(),
        )

    @Test
    fun `a new widget dropped on a neighbour is placed there once bound, and the neighbour moves aside`() {
        val drop = dropNew(layout, bounds(default = CellSpan(2, 1)), DragTarget.Cell(HomeUpper, 0, 0))

        val placed = layout.place(id = 7, bounds(default = CellSpan(2, 1)), drop).changed()

        assertEquals(CellRect(0, 0, 2, 1), placed.layout.widget(7)!!.cells)
        assertEquals(CellRect(0, 1, 2, 1), placed.layout.widget(1)!!.cells)
    }

    @Test
    fun `a drop whose cells were taken while the bind dialog was up finds no room`() {
        val drop = dropNew(layout, bounds(default = CellSpan(2, 1)), DragTarget.Cell(HomeUpper, 2, 2))
        val meanwhile = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(3, 2, 1, 1)))

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), meanwhile.place(id = 7, bounds(default = CellSpan(2, 1)), drop))
    }

    @Test
    fun `a neighbour the drop moves that was removed meanwhile is left out`() {
        val drop = dropNew(layout, bounds(default = CellSpan(2, 1)), DragTarget.Cell(HomeUpper, 0, 0))

        val placed = homePage().place(id = 7, bounds(default = CellSpan(2, 1)), drop).changed()

        assertEquals(listOf(7), placed.layout.widgets.map { it.id })
    }

    @Test
    fun `a new widget shrunk to fit the one-row lower zone is placed at the shrunk span`() {
        val tall = bounds(default = CellSpan(2, 2), min = CellSpan(2, 1))
        val drop = dropNew(layout, tall, DragTarget.Cell(HomeLower, 0, 0))

        assertEquals(CellRect(0, 0, 2, 1), layout.place(id = 7, tall, drop).changed().widget.cells)
    }

    @Test
    fun `a drop survives the trip to the add trampoline as ints`() {
        val drop = WidgetDrop(HomeLower, CellRect(1, 0, 2, 1), mapOf(3 to CellRect(3, 0, 1, 1), 4 to CellRect(0, 0, 1, 1)))

        assertEquals(drop, WidgetDrop.fromInts(drop.toInts()))
    }

    @Test
    fun `span bounds survive the trip from the picker to Home as ints`() {
        val bounds = bounds(default = CellSpan(3, 2), min = CellSpan(2, 1), max = CellSpan(4, 2), horizontal = true, vertical = false)

        assertEquals(bounds, SpanBounds.fromInts(bounds.toInts()))
    }

    @Test
    fun `malformed ints decode to nothing`() {
        assertEquals(null, WidgetDrop.fromInts(intArrayOf(9, 0, 0)))
        assertEquals(null, SpanBounds.fromInts(intArrayOf(1, 1)))
    }
}
