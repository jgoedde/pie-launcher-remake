package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 100 px cells with 10 px gaps: a handle travels 110 px per cell, so the threshold is 72.6 px.
class WidgetResizeTest {
    private val layout = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1)), widget(2, HomeUpper, CellRect(2, 0, 1, 1)))

    @Test
    fun `the span changes once the handle has travelled two-thirds of a cell and pitch, with a tick`() {
        val start = WidgetResize.start(layout, 1, ResizeEdge.Right)!!

        val short = start.dragBy(70f)
        val past = short.dragBy(75f)
        val further = past.dragBy(100f)

        assertEquals(CellSpan(2, 1), short.span)
        assertFalse(short.ticked)
        assertEquals(CellSpan(3, 1), past.span)
        assertTrue(past.ticked)
        assertEquals(CellSpan(3, 1), further.span)
        assertFalse(further.ticked)
    }

    @Test
    fun `the preview shows the resized widget and the pushed neighbours`() {
        val resize = WidgetResize.start(layout, 1, ResizeEdge.Right)!!.dragBy(80f)

        assertEquals(CellRect(0, 0, 3, 1), resize.preview.widget(1)!!.cells)
        assertEquals(CellRect(3, 0, 1, 1), resize.preview.widget(2)!!.cells)
        assertEquals(CellRect(0, 0, 3, 1), (resize.result as LayoutResult.Changed).widget.cells)
    }

    @Test
    fun `dragging back below the threshold restores the span and the neighbours`() {
        val back = WidgetResize.start(layout, 1, ResizeEdge.Right)!!.dragBy(80f).dragBy(20f)

        assertEquals(CellSpan(2, 1), back.span)
        assertTrue(back.ticked)
        assertEquals(layout, back.preview)
        assertEquals(LayoutResult.Unchanged, back.result)
    }

    @Test
    fun `a vertical handle measures its travel in rows`() {
        val resize = WidgetResize.start(layout, 1, ResizeEdge.Bottom)!!.dragBy(75f)

        assertEquals(CellSpan(2, 2), resize.span)
    }

    @Test
    fun `the frame follows the dragged edge freely while the widget snaps by cells`() {
        val resize = WidgetResize.start(layout, 1, ResizeEdge.Left)!!

        assertEquals(CellBounds(left = 0f, top = 0f, width = 210f, height = 100f), resize.frame)
        assertEquals(CellBounds(left = 40f, top = 0f, width = 170f, height = 100f), resize.dragBy(40f).frame)
        assertEquals(
            CellBounds(left = 0f, top = 0f, width = 280f, height = 100f),
            WidgetResize.start(layout, 1, ResizeEdge.Right)!!.dragBy(70f).frame,
        )
    }

    @Test
    fun `there is no resize on an edge without a handle or of an unknown widget`() {
        val fixed = homePage(widget(1, HomeUpper, CellRect(0, 0, 2, 1), SpanBounds.fixed(CellSpan(2, 1))))

        assertNull(WidgetResize.start(fixed, 1, ResizeEdge.Right))
        assertNull(WidgetResize.start(layout, 9, ResizeEdge.Right))
    }
}
