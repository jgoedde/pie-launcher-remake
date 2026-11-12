package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dwelling with a dragged widget at a Page edge changes the Page (PRD 7.3). */
class EdgeDwellTest {
    @Test
    fun `the finger is at an edge within the edge strip`() {
        assertEquals(PageEdge.Left, PageEdge.at(fingerX = 10f, widthPx = 400f, edgePx = 40f))
        assertEquals(PageEdge.Right, PageEdge.at(fingerX = 390f, widthPx = 400f, edgePx = 40f))
        assertNull(PageEdge.at(fingerX = 200f, widthPx = 400f, edgePx = 40f))
    }

    @Test
    fun `dwelling 500 ms at the right edge changes to the next Page`() {
        val dwell = EdgeDwell.None.at(PageEdge.Right, nowMs = 1000)

        assertNull(dwell.turn(nowMs = 1499, page = 0, pageCount = 3, canAppend = true))
        assertEquals(1, dwell.turn(nowMs = 1500, page = 0, pageCount = 3, canAppend = true)?.page)
    }

    @Test
    fun `dwelling at the left edge changes to the previous Page, never left of the Home Page`() {
        val dwell = EdgeDwell.None.at(PageEdge.Left, nowMs = 0)

        assertEquals(1, dwell.turn(nowMs = 500, page = 2, pageCount = 3, canAppend = true)?.page)
        assertNull(dwell.turn(nowMs = 500, page = HOME_PAGE, pageCount = 3, canAppend = true))
    }

    @Test
    fun `at the right end a new Page is made, unless the last Page holds nothing but the dragged widget`() {
        val dwell = EdgeDwell.None.at(PageEdge.Right, nowMs = 0)

        assertEquals(3, dwell.turn(nowMs = 500, page = 2, pageCount = 3, canAppend = true)?.page)
        assertNull(dwell.turn(nowMs = 500, page = 2, pageCount = 3, canAppend = false))
    }

    @Test
    fun `leaving the edge restarts the dwell`() {
        val dwell =
            EdgeDwell.None
                .at(PageEdge.Right, nowMs = 0)
                .at(null, nowMs = 300)
                .at(PageEdge.Right, nowMs = 400)

        assertNull(dwell.turn(nowMs = 600, page = 0, pageCount = 3, canAppend = true))
        assertEquals(900L, dwell.dueAtMs)
    }

    @Test
    fun `staying at the edge after a change turns again after another dwell`() {
        val turned = EdgeDwell.None.at(PageEdge.Right, nowMs = 0).turn(nowMs = 500, page = 0, pageCount = 3, canAppend = true)!!

        assertEquals(1000L, turned.dwell.dueAtMs)
        assertEquals(PageEdge.Right, turned.dwell.at(PageEdge.Right, nowMs = 700).edge)
        assertNull(EdgeDwell.None.dueAtMs)
    }
}
