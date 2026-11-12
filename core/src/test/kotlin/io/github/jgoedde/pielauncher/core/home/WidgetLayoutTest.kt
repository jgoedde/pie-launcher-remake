package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun `tap-add places a widget at its default span in the first free spot, row by row`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 3, 1)))

        val added = layout.add(2, bounds(default = CellSpan(2, 1)), listOf(HomeUpper, HomeLower)).changed()

        assertEquals(LaidOutWidget(2, HomeUpper, CellRect(0, 1, 2, 1), bounds(default = CellSpan(2, 1))), added.widget)
        assertEquals(added.widget, added.layout.widget(2))
    }

    @Test
    fun `tap-add tries the zones in order and uses a later one only when the earlier ones are full`() {
        val fullUpper = homePage(widget(1, HomeUpper, CellRect(0, 0, 4, 3)))

        assertEquals(
            HomeLower,
            fullUpper
                .add(2, bounds(CellSpan(2, 1)), listOf(HomeUpper, HomeLower))
                .changed()
                .widget.zone,
        )
        assertEquals(
            HomeLower,
            homePage()
                .add(2, bounds(CellSpan(2, 1)), listOf(HomeLower, HomeUpper))
                .changed()
                .widget.zone,
        )
    }

    @Test
    fun `tap-add never shrinks a widget below its default span to make it fit`() {
        // Only a 1 x 1 hole is left; the widget could shrink to 1 x 1 but is added at its default 2 x 1 only.
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 4, 2)), widget(3, HomeUpper, CellRect(0, 2, 3, 1)))

        val result = layout.add(2, bounds(default = CellSpan(2, 1), min = CellSpan(1, 1)), listOf(HomeUpper))

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), result)
    }

    @Test
    fun `a default span taller than a zone is cut to the zone's rows when the min span allows`() {
        val added = homePage().add(2, bounds(default = CellSpan(4, 2), min = CellSpan(2, 1)), listOf(HomeLower)).changed()

        assertEquals(CellRect(0, 0, 4, 1), added.widget.cells)
    }

    @Test
    fun `a widget whose min span exceeds every Widget Zone of the Page is too large`() {
        val tall = bounds(default = CellSpan(2, 4), min = CellSpan(2, 4))

        assertEquals(LayoutResult.Rejected(Rejection.TooLarge), homePage().add(2, tall, listOf(HomeUpper, HomeLower)))
    }

    @Test
    fun `a widget that fits some zone of the Page, just not the ones tried, finds no room`() {
        val twoRows = bounds(default = CellSpan(2, 2), min = CellSpan(2, 2))

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), homePage().add(2, twoRows, listOf(HomeLower)))
    }

    @Test
    fun `a widget dropped on free cells moves there and nothing else moves`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 2, 1)))

        val dropped = layout.drop(layout.pickUp(1)!!, DragTarget.Cell(HomeUpper, 1, 2)).changed()

        assertEquals(CellRect(1, 2, 2, 1), dropped.layout.widget(1)!!.cells)
        assertEquals(CellRect(2, 0, 2, 1), dropped.layout.widget(2)!!.cells)
        assertEquals(emptyList<LaidOutWidget>(), dropped.moved)
    }

    @Test
    fun `a drop on the Pie Zone is rejected and the layout stays as it was`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)))

        assertEquals(LayoutResult.Rejected(Rejection.PieZone), layout.drop(layout.pickUp(1)!!, DragTarget.PieZone))
        assertEquals(LayoutResult.Rejected(Rejection.PieZone), layout.dragOver(layout.pickUp(1)!!, DragTarget.PieZone))
    }

    @Test
    fun `a 2x2 dragged onto a 2x1 pushes it down`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)))
        val new = DraggedWidget(9, CellSpan(2, 2), bounds(CellSpan(2, 2)))

        val preview = layout.dragOver(new, DragTarget.Cell(HomeUpper, 0, 0)).changed()

        assertEquals(CellRect(0, 0, 2, 2), preview.widget.cells)
        assertEquals(listOf(widget(1, HomeUpper, CellRect(0, 2, 2, 1))), preview.moved)
        assertEquals(CellRect(0, 2, 2, 1), preview.layout.widget(1)!!.cells)
    }

    @Test
    fun `a drop commits the reflow its drag over showed`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 2, 1)))
        val dragged = layout.pickUp(2)!!
        val target = DragTarget.Cell(HomeUpper, 0, 0)

        assertEquals(
            layout
                .dragOver(dragged, target)
                .changed()
                .layout.widgets,
            layout
                .drop(dragged, target)
                .changed()
                .layout.widgets,
        )
    }

    @Test
    fun `neighbours under the dragged widget move into free space when pushing hits the zone edge`() {
        // The left two columns are full; a 2x2 dropped on the top-left moves the two top widgets into the free 2x2 on the right.
        val layout =
            homePage(
                widget(1, HomeUpper, CellRect(0, 0, 2, 1)),
                widget(2, HomeUpper, CellRect(0, 1, 2, 1)),
                widget(3, HomeUpper, CellRect(0, 2, 2, 1)),
                widget(4, HomeUpper, CellRect(2, 0, 2, 1)),
            )

        val dropped = layout.drop(DraggedWidget(9, CellSpan(2, 2), bounds(CellSpan(2, 2))), DragTarget.Cell(HomeUpper, 0, 0)).changed()

        assertEquals(CellRect(0, 0, 2, 2), dropped.widget.cells)
        assertEquals(CellRect(2, 1, 2, 1), dropped.layout.widget(1)!!.cells)
        assertEquals(CellRect(2, 2, 2, 1), dropped.layout.widget(2)!!.cells)
    }

    @Test
    fun `the dragged widget takes the nearest empty space when no push makes room`() {
        // Pushing the 4x2 widget is blocked everywhere; the only gap, 2x1 at the bottom right, takes the drop.
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 4, 2)), widget(2, HomeUpper, CellRect(0, 2, 2, 1)))

        val dropped = layout.drop(DraggedWidget(9, CellSpan(2, 1), bounds(CellSpan(2, 1))), DragTarget.Cell(HomeUpper, 0, 0)).changed()

        assertEquals(CellRect(2, 2, 2, 1), dropped.widget.cells)
        assertEquals(emptyList<LaidOutWidget>(), dropped.moved)
    }

    @Test
    fun `a widget dropped back on its own cells stays there and moves nothing`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(1, 1, 2, 2)), widget(2, HomeUpper, CellRect(0, 0, 1, 1)))

        val dropped = layout.drop(layout.pickUp(1)!!, DragTarget.Cell(HomeUpper, 1, 1)).changed()

        assertEquals(layout.widgets.toSet(), dropped.layout.widgets.toSet())
        assertEquals(emptyList<LaidOutWidget>(), dropped.moved)
    }

    @Test
    fun `a dragged widget that fits nowhere shrinks its width first, then its height`() {
        // Ten 1x1 widgets leave two lone free cells; a 2x2 can't fit, and shrinks to 1x2 before it would try 2x1.
        val free = setOf(3 to 0, 3 to 2)
        val ones = (0 until 3).flatMap { r -> (0 until 4).map { c -> c to r } }.filter { it !in free }
        val layout = homePage(*ones.mapIndexed { i, (c, r) -> widget(i + 1, HomeUpper, CellRect(c, r, 1, 1)) }.toTypedArray())

        val narrower = layout.drop(DraggedWidget(99, CellSpan(2, 2), bounds(CellSpan(2, 2))), DragTarget.Cell(HomeUpper, 0, 0))
        val flatter =
            layout.drop(DraggedWidget(99, CellSpan(2, 2), bounds(CellSpan(2, 2), min = CellSpan(2, 1))), DragTarget.Cell(HomeUpper, 0, 0))

        assertEquals(CellRect(0, 0, 1, 2), narrower.changed().widget.cells)
        assertEquals(CellRect(0, 0, 2, 1), flatter.changed().widget.cells)
    }

    @Test
    fun `no solution even at the min span rejects the drop`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 4, 3)), widget(2, HomeLower, CellRect(0, 0, 2, 1)))

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), layout.drop(layout.pickUp(2)!!, DragTarget.Cell(HomeUpper, 0, 0)))
    }

    @Test
    fun `a widget moves across the Pie Zone into the other Widget Zone`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)))

        val dropped = layout.drop(layout.pickUp(1)!!, DragTarget.Cell(HomeLower, 1, 0)).changed()

        assertEquals(listOf(widget(1, HomeLower, CellRect(1, 0, 2, 1))), dropped.layout.widgets)
    }

    @Test
    fun `a 3x2 moved into the lower zone shrinks to 3x1 when its min height allows, else is rejected`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 3, 2), bounds(CellSpan(3, 2), min = CellSpan(3, 1))))
        val rigid = homePage(widget(1, HomeUpper, CellRect(0, 0, 3, 2), bounds(CellSpan(3, 2), min = CellSpan(3, 2))))

        val shrunk = layout.drop(layout.pickUp(1)!!, DragTarget.Cell(HomeLower, 0, 0)).changed()

        assertEquals(widget(1, HomeLower, CellRect(0, 0, 3, 1), bounds(CellSpan(3, 2), min = CellSpan(3, 1))), shrunk.widget)
        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), rigid.drop(rigid.pickUp(1)!!, DragTarget.Cell(HomeLower, 0, 0)))
    }

    @Test
    fun `reflow never pushes a widget out of its zone`() {
        // The lower zone is full and its widget can't make room within it; it is never pushed up into the upper zone.
        val layout = homePage(widget(1, HomeLower, CellRect(0, 0, 4, 1)), widget(2, HomeUpper, CellRect(0, 0, 1, 1)))

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), layout.drop(layout.pickUp(2)!!, DragTarget.Cell(HomeLower, 0, 0)))
    }

    @Test
    fun `a resize handle changes the span by one cell once dragged past two-thirds of a cell`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 1, 1)))

        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Right, 0.6f))
        assertEquals(
            CellRect(0, 0, 2, 1),
            layout
                .resize(1, ResizeEdge.Right, 0.7f)
                .changed()
                .widget.cells,
        )
        assertEquals(
            CellRect(0, 0, 2, 1),
            layout
                .resize(1, ResizeEdge.Right, 1.6f)
                .changed()
                .widget.cells,
        )
        assertEquals(
            CellRect(0, 0, 3, 1),
            layout
                .resize(1, ResizeEdge.Right, 1.7f)
                .changed()
                .widget.cells,
        )
        assertEquals(
            CellRect(0, 0, 1, 3),
            layout
                .resize(1, ResizeEdge.Bottom, 2.9f)
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `dragging the left or top handle outward grows the widget toward that side`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(2, 1, 1, 1)))

        assertEquals(
            CellRect(1, 1, 2, 1),
            layout
                .resize(1, ResizeEdge.Left, -1f)
                .changed()
                .widget.cells,
        )
        assertEquals(
            CellRect(2, 0, 1, 2),
            layout
                .resize(1, ResizeEdge.Top, -1f)
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `a resize stays within the min and max span and the zone edges`() {
        val layout =
            homePage(widget(1, HomeUpper, CellRect(1, 0, 2, 2), bounds(CellSpan(2, 2), min = CellSpan(2, 1), max = CellSpan(4, 2))))

        assertEquals(
            CellRect(1, 0, 3, 2),
            layout
                .resize(1, ResizeEdge.Right, 5f)
                .changed()
                .widget.cells,
        )
        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Left, 1f))
        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Bottom, 1f))
        assertEquals(
            CellRect(1, 0, 2, 1),
            layout
                .resize(1, ResizeEdge.Bottom, -3f)
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `a widget resizes only on the axes it allows`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), bounds(CellSpan(2, 1), horizontal = false)))

        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Right, 1f))
        assertEquals(
            CellRect(0, 0, 2, 2),
            layout
                .resize(1, ResizeEdge.Bottom, 1f)
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `growing a widget pushes its neighbours in the resize direction`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 1, 1)))

        val resized = layout.resize(1, ResizeEdge.Right, 1f).changed()

        assertEquals(CellRect(0, 0, 3, 1), resized.widget.cells)
        assertEquals(listOf(widget(2, HomeUpper, CellRect(3, 0, 1, 1))), resized.moved)
    }

    @Test
    fun `a widget grows only as far as its neighbours can move`() {
        val blocked = homePage(widget(1, HomeLower, CellRect(0, 0, 2, 1)), widget(2, HomeLower, CellRect(2, 0, 2, 1)))
        val partly = homePage(widget(1, HomeLower, CellRect(0, 0, 2, 1)), widget(2, HomeLower, CellRect(3, 0, 1, 1)))

        assertEquals(LayoutResult.Unchanged, blocked.resize(1, ResizeEdge.Right, 1f))
        assertEquals(
            CellRect(0, 0, 3, 1),
            partly
                .resize(1, ResizeEdge.Right, 2f)
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `a widget with fixed bounds, such as one whose provider is gone, never resizes`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), SpanBounds.fixed(CellSpan(2, 1))))

        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Right, 1f))
        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Bottom, 1f))
    }

    @Test
    fun `a widget below its min span can be resized back up even on a locked axis`() {
        val shrunk = bounds(CellSpan(2, 1), min = CellSpan(2, 1), max = CellSpan(2, 1), horizontal = false, vertical = false)
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 1, 1), shrunk))

        assertEquals(
            CellRect(0, 0, 2, 1),
            layout
                .resize(1, ResizeEdge.Right, 3f)
                .changed()
                .widget.cells,
        )
        assertEquals(LayoutResult.Unchanged, layout.resize(1, ResizeEdge.Bottom, 1f))
    }

    @Test
    fun `the Resize Frame has handles only on the axes a widget can resize on`() {
        val both = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)))
        val vertical = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), bounds(CellSpan(2, 1), horizontal = false)))
        val noRange =
            homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 2), bounds(CellSpan(2, 2), min = CellSpan(2, 1), max = CellSpan(2, 3))))
        val fixed = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), SpanBounds.fixed(CellSpan(2, 1))))

        assertEquals(ResizeEdge.entries.toSet(), both.resizeEdges(1))
        assertEquals(setOf(ResizeEdge.Top, ResizeEdge.Bottom), vertical.resizeEdges(1))
        assertEquals(setOf(ResizeEdge.Top, ResizeEdge.Bottom), noRange.resizeEdges(1))
        assertEquals(emptySet<ResizeEdge>(), fixed.resizeEdges(1))
        assertEquals(emptySet<ResizeEdge>(), both.resizeEdges(9))
    }

    @Test
    fun `a widget below its min span gets handles on that axis even when it is locked`() {
        val shrunk = bounds(CellSpan(2, 1), min = CellSpan(2, 1), max = CellSpan(2, 1), horizontal = false, vertical = false)
        val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 1, 1), shrunk))

        assertEquals(setOf(ResizeEdge.Left, ResizeEdge.Right), layout.resizeEdges(1))
    }

    @Test
    fun `undo puts a removed widget back on its exact cells`() {
        val layout = homePage(widget(1, HomeUpper, CellRect(1, 1, 2, 1)), widget(2, HomeLower, CellRect(0, 0, 1, 1)))

        val removed = layout.remove(1).changed()
        val restored = removed.layout.undo(removed.widget).changed()

        assertEquals(listOf(2), removed.layout.widgets.map { it.id })
        assertEquals(layout.widgets.toSet(), restored.layout.widgets.toSet())
    }

    @Test
    fun `undo finds no room when the removed widget's cells were taken meanwhile`() {
        val removed = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1))).remove(1).changed()
        val refilled =
            removed.layout
                .add(2, bounds(CellSpan(1, 1)), listOf(HomeUpper))
                .changed()
                .layout

        assertEquals(LayoutResult.Rejected(Rejection.NoRoom), refilled.undo(removed.widget))
    }

    @Test
    fun `hit-testing finds the widget under a point and its px bounds, gaps between its own cells included`() {
        // 100 px cells, 10 px gaps: column c starts at 110 c; the upper zone has no vertical padding.
        val layout = homePage(widget(1, HomeUpper, CellRect(1, 1, 2, 1)))

        assertEquals(CellBounds(left = 110f, top = 110f, width = 210f, height = 100f), layout.boundsOf(layout.widget(1)!!))
        assertEquals(1, layout.widgetAt(HomeUpper, 150f, 150f)?.id)
        assertEquals(1, layout.widgetAt(HomeUpper, 215f, 150f)?.id)
        assertEquals(null, layout.widgetAt(HomeUpper, 105f, 150f))
        assertEquals(null, layout.widgetAt(HomeLower, 150f, 50f))
    }

    @Test
    fun `the cell under a point counts a gap to the cell before it`() {
        val layout = homePage()

        assertEquals(DragTarget.Cell(HomeUpper, 3, 2), layout.cellAt(HomeUpper, 429f, 319f))
        assertEquals(DragTarget.Cell(HomeUpper, 0, 0), layout.cellAt(HomeUpper, 105f, 105f))
        assertEquals(null, layout.cellAt(HomeUpper, 430f, 0f))
    }
}

/** The Home Page of a 430 x 1020 px window: 100 px cells with 10 px gaps, a 4 x 3 upper zone and a 4 x 1 lower zone. */
internal fun homePage(vararg widgets: LaidOutWidget) =
    WidgetLayout(HomeZones.of(widthPx = 430f, heightPx = 1020f, pieZoneHeightPx = 600f, cellGapPx = 10f).grids, widgets.toList())

internal fun bounds(
    default: CellSpan,
    min: CellSpan = CellSpan(1, 1),
    max: CellSpan = CellSpan(4, 3),
    horizontal: Boolean = true,
    vertical: Boolean = true,
) = SpanBounds(default, min, max, resizesHorizontally = horizontal, resizesVertically = vertical)

internal fun widget(
    id: Int,
    zone: WidgetZoneId,
    cells: CellRect,
    bounds: SpanBounds = bounds(default = CellSpan(cells.width, cells.height)),
) = LaidOutWidget(id, zone, cells, bounds)

internal fun LayoutResult.changed(): LayoutResult.Changed = this as? LayoutResult.Changed ?: error("expected a change, got $this")
