package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditModeTest {
    private val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 2, 1)))

    @Test
    fun `at rest the Pie is on`() {
        assertTrue(EditMode.Idle.pieEnabled)
    }

    @Test
    fun `a long-press on a widget opens its popup and turns the Pie off`() {
        val mode = EditMode.Idle.longPress(1)

        assertEquals(EditMode.Selected(1), mode)
        assertFalse(mode.pieEnabled)
    }

    @Test
    fun `moving past touch slop after the long-press closes the popup and moves the widget`() {
        val mode = EditMode.Idle.longPress(1).startMove(layout, nowMs = 0)

        val dragging = mode as EditMode.Dragging
        assertEquals(1, dragging.drag.dragged.id)
        assertFalse(mode.pieEnabled)
    }

    @Test
    fun `the drag follows the finger and shows the reorder after the rest`() {
        val mode =
            EditMode.Idle
                .longPress(1)
                .startMove(layout, nowMs = 0)
                .dragTo(DragTarget.Cell(HomeUpper, 2, 0), nowMs = 0)
                .tick(nowMs = WidgetDrag.REORDER_TIMEOUT_MS)

        assertEquals(
            CellRect(0, 0, 2, 1),
            (mode as EditMode.Dragging)
                .drag.preview
                .widget(2)!!
                .cells,
        )
    }

    @Test
    fun `lifting the finger after a move drops the widget and leaves Edit Mode`() {
        val release =
            EditMode.Idle
                .longPress(1)
                .startMove(layout, nowMs = 0)
                .dragTo(DragTarget.Cell(HomeLower, 0, 0), nowMs = 0)
                .release()

        assertEquals(EditMode.Idle, release.mode)
        assertEquals(LaidOutWidget(1, HomeLower, CellRect(0, 0, 2, 1), layout.widget(1)!!.bounds), release.result.changed().widget)
    }

    @Test
    fun `a rejected drop springs the widget back and leaves Edit Mode`() {
        val release =
            EditMode.Idle
                .longPress(1)
                .startMove(layout, nowMs = 0)
                .dragTo(DragTarget.PieZone, nowMs = 0)
                .release()

        assertEquals(EditMode.Idle, release.mode)
        assertEquals(LayoutResult.Rejected(Rejection.PieZone), release.result)
    }

    @Test
    fun `lifting the finger without moving keeps the popup open`() {
        val release = EditMode.Idle.longPress(1).release()

        assertEquals(EditMode.Selected(1), release.mode)
        assertEquals(LayoutResult.Unchanged, release.result)
    }

    @Test
    fun `back or Home leaves Edit Mode from any state`() {
        val selected = EditMode.Idle.longPress(1)
        val dragging = selected.startMove(layout, nowMs = 0)

        assertEquals(EditMode.Idle, selected.leave())
        assertEquals(EditMode.Idle, dragging.leave())
    }

    @Test
    fun `a long-press elsewhere during a drag changes nothing`() {
        val dragging = EditMode.Idle.longPress(1).startMove(layout, nowMs = 0)

        assertEquals(dragging, dragging.longPress(2))
    }

    @Test
    fun `a widget just added opens its Resize Frame without the popup`() {
        assertEquals(EditMode.Selected(1, popupOpen = false), EditMode.Idle.added(1))
        assertFalse(EditMode.Idle.added(1).pieEnabled)
    }

    @Test
    fun `the popup can close while the Resize Frame stays open`() {
        assertEquals(EditMode.Selected(1, popupOpen = false), EditMode.Idle.longPress(1).closePopup())
        assertEquals(EditMode.Idle, EditMode.Idle.closePopup())
    }

    @Test
    fun `dragging a handle resizes the selected widget and closes the popup`() {
        val resizing =
            EditMode.Idle
                .longPress(1)
                .startResize(layout, ResizeEdge.Bottom)
                .resizeBy(75f)

        val resize = (resizing as EditMode.Resizing).resize
        assertEquals(ResizeEdge.Bottom, resize.edge)
        assertEquals(CellSpan(2, 2), resize.span)
        assertFalse(resizing.pieEnabled)
    }

    @Test
    fun `letting go of a handle commits the new size and keeps the frame open`() {
        val release =
            EditMode.Idle
                .longPress(1)
                .startResize(layout, ResizeEdge.Bottom)
                .resizeBy(75f)
                .release()

        assertEquals(EditMode.Selected(1, popupOpen = false), release.mode)
        assertEquals(
            CellRect(0, 0, 2, 2),
            release.result
                .changed()
                .widget.cells,
        )
    }

    @Test
    fun `a handle drag that never passed the threshold changes nothing`() {
        val release =
            EditMode.Idle
                .longPress(1)
                .startResize(layout, ResizeEdge.Bottom)
                .resizeBy(10f)
                .release()

        assertEquals(EditMode.Selected(1, popupOpen = false), release.mode)
        assertEquals(LayoutResult.Unchanged, release.result)
    }

    @Test
    fun `a resize only starts from a selected widget on an edge with a handle`() {
        val fixed = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), SpanBounds.fixed(CellSpan(2, 1))))

        assertEquals(EditMode.Idle, EditMode.Idle.startResize(layout, ResizeEdge.Right))
        assertEquals(EditMode.Selected(1), EditMode.Idle.longPress(1).startResize(fixed, ResizeEdge.Right))
    }

    @Test
    fun `back, Home or a tap outside closes the Resize Frame mid-resize`() {
        val resizing = EditMode.Idle.longPress(1).startResize(layout, ResizeEdge.Right)

        assertEquals(EditMode.Idle, resizing.leave())
        assertEquals(resizing, resizing.longPress(2))
    }

    @Test
    fun `a move only starts from a selected widget that is on the Page`() {
        assertEquals(EditMode.Idle, EditMode.Idle.startMove(layout, nowMs = 0))
        assertEquals(EditMode.Selected(9), EditMode.Idle.longPress(9).startMove(layout, nowMs = 0))
    }
}
