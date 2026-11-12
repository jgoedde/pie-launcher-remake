package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.Page
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tap-add and widget Pin Requests over all Pages (PRD 3.1, 7.4, 7.6). */
class PageLayoutsTest {
    private val homeGrids = mapOf(HomeUpper to grid(rows = 3), HomeLower to grid(rows = 1))
    private val pageGrids = mapOf(Page to grid(rows = 5))

    private fun pages(vararg widgets: List<LaidOutWidget>) =
        PageLayouts(
            widgets.mapIndexed { page, onPage -> WidgetLayout(if (page == HOME_PAGE) homeGrids else pageGrids, onPage) },
            newPage = WidgetLayout(pageGrids, emptyList()),
        )

    private val small = SpanBounds.fixed(CellSpan(2, 1))

    @Test
    fun `a widget added with no long-pressed zone goes to the Home Page, upper zone first`() {
        val placed = pages(emptyList(), emptyList()).add(7, small, first = null)

        assertEquals(PageZone(HOME_PAGE, HomeUpper), placed.zone)
    }

    @Test
    fun `a widget tap-added from the lower zone's menu tries the lower zone first`() {
        val placed = pages(emptyList()).add(7, small, first = PageZone(HOME_PAGE, HomeLower))

        assertEquals(PageZone(HOME_PAGE, HomeLower), placed.zone)
    }

    @Test
    fun `when the long-pressed zone is full the widget lands in the other zone of the Home Page`() {
        val placed = pages(listOf(widget(1, HomeLower, CellRect(0, 0, 4, 1)))).add(7, small, first = PageZone(HOME_PAGE, HomeLower))

        assertEquals(PageZone(HOME_PAGE, HomeUpper), placed.zone)
    }

    @Test
    fun `a widget tap-added from a non-Home Page's menu lands on that Page`() {
        val placed = pages(emptyList(), emptyList(), emptyList()).add(7, small, first = PageZone(2, Page))

        assertEquals(PageZone(2, Page), placed.zone)
    }

    @Test
    fun `with the Home Page full the widget goes to the next Page with room`() {
        val placed = pages(fullHome(), emptyList()).add(7, small, first = null)

        assertEquals(PageZone(1, Page), placed.zone)
        assertEquals(CellRect(0, 0, 2, 1), (placed.result as LayoutResult.Changed).widget.cells)
    }

    @Test
    fun `with no room on any Page a Page is appended for the widget`() {
        val placed = pages(fullHome(), listOf(widget(1, Page, CellRect(0, 0, 4, 5)))).add(7, small, first = null)

        assertEquals(PageZone(2, Page), placed.zone)
        assertEquals(listOf(7), (placed.result as LayoutResult.Changed).layout.widgets.map { it.id })
    }

    @Test
    fun `a widget that fits no Widget Zone at all is too large`() {
        val placed = pages(emptyList()).add(7, SpanBounds.fixed(CellSpan(4, 6)), first = null)

        assertEquals(LayoutResult.Rejected(Rejection.TooLarge), placed.result)
        assertEquals(null, placed.zone)
    }

    private fun fullHome() = listOf(widget(1, HomeUpper, CellRect(0, 0, 4, 3)), widget(2, HomeLower, CellRect(0, 0, 4, 1)))

    private fun widget(
        id: Int,
        zone: WidgetZoneId,
        cells: CellRect,
    ) = LaidOutWidget(id, zone, cells, SpanBounds.fixed(cells.span))

    private fun grid(rows: Int) = CellGrid(columns = 4, rows = rows, cellWidth = 100f, cellHeight = 100f, gap = 10f, verticalPadding = 0f)
}
