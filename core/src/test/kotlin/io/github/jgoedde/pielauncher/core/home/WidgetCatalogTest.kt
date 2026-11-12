package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCatalogTest {
    // 4 x 5 grid of 80 dp cells with 16 dp between cells: n cells span 96n - 16 dp.
    private val grid = CatalogGrid(columns = 4, rows = 5, cellWidth = 80f, cellHeight = 80f, spacing = 16f)

    @Test
    fun `a size of exactly n cells plus the gaps between them spans n cells`() {
        assertEquals(1, WidgetCatalog.span(size = 80f, cell = 80f, spacing = 16f))
        assertEquals(2, WidgetCatalog.span(size = 176f, cell = 80f, spacing = 16f))
        assertEquals(3, WidgetCatalog.span(size = 177f, cell = 80f, spacing = 16f))
    }

    @Test
    fun `a zero or tiny size still spans one cell`() {
        assertEquals(1, WidgetCatalog.span(size = 0f, cell = 80f, spacing = 16f))
        assertEquals(1, WidgetCatalog.span(size = 1f, cell = 80f, spacing = 16f))
    }

    @Test
    fun `the default span comes from the min size`() {
        val spans = WidgetCatalog.spans(ProviderSizes(minWidth = 176f, minHeight = 80f), grid)

        assertEquals(CellSpan(2, 1), spans.default)
    }

    @Test
    fun `a target cell size inside the resize range wins over the min size`() {
        val sizes = resizable(minWidth = 176f, minHeight = 80f, targetCells = CellSpan(3, 2))

        assertEquals(CellSpan(3, 2), WidgetCatalog.spans(sizes, grid).default)
    }

    @Test
    fun `a target cell size above the max resize size is ignored`() {
        // Max resize width is 2 cells, so a 3-wide target lies outside [min, max].
        val sizes = resizable(minWidth = 176f, minHeight = 80f, maxResizeWidth = 176f, targetCells = CellSpan(3, 2))

        assertEquals(CellSpan(2, 1), WidgetCatalog.spans(sizes, grid).default)
    }

    @Test
    fun `a target cell size below the min resize size is ignored`() {
        // Min resize height is 2 cells, so a 1-high target lies outside [min, max].
        val sizes = resizable(minWidth = 176f, minHeight = 176f, minResizeHeight = 176f, targetCells = CellSpan(3, 1))

        assertEquals(CellSpan(2, 2), WidgetCatalog.spans(sizes, grid).default)
    }

    @Test
    fun `the min span comes from the min resize size`() {
        val sizes = resizable(minWidth = 272f, minHeight = 176f, minResizeWidth = 80f, minResizeHeight = 176f)

        assertEquals(CellSpan(1, 2), WidgetCatalog.spans(sizes, grid).min)
    }

    @Test
    fun `a min resize size above the min size is capped at the default span`() {
        // minResizeWidth (3 cells) has no effect when it is larger than minWidth (2 cells).
        val sizes = resizable(minWidth = 176f, minHeight = 80f, minResizeWidth = 272f)

        val spans = WidgetCatalog.spans(sizes, grid)
        assertEquals(CellSpan(2, 1), spans.default)
        assertEquals(CellSpan(2, 1), spans.min)
    }

    @Test
    fun `the max span comes from the max resize size`() {
        val sizes = resizable(minWidth = 80f, minHeight = 80f, maxResizeWidth = 272f, maxResizeHeight = 176f)

        assertEquals(CellSpan(3, 2), WidgetCatalog.spans(sizes, grid).max)
    }

    @Test
    fun `a missing max resize size makes the whole grid the max span`() {
        val sizes = resizable(minWidth = 80f, minHeight = 80f)

        assertEquals(CellSpan(4, 5), WidgetCatalog.spans(sizes, grid).max)
    }

    @Test
    fun `a default span wider than the grid is clamped to its 4 columns`() {
        val sizes = resizable(minWidth = 600f, minHeight = 80f)

        assertEquals(CellSpan(4, 1), WidgetCatalog.spans(sizes, grid).default)
    }

    @Test
    fun `a max resize size beyond the grid is clamped to the grid`() {
        val sizes = resizable(minWidth = 80f, minHeight = 80f, maxResizeWidth = 900f, maxResizeHeight = 900f)

        assertEquals(CellSpan(4, 5), WidgetCatalog.spans(sizes, grid).max)
    }

    @Test
    fun `an axis resizes when its resize mode bit is set and it has a real range`() {
        val spans = WidgetCatalog.spans(resizable(minWidth = 176f, minHeight = 80f, minResizeWidth = 80f), grid)

        assertTrue(spans.resizesHorizontally)
        assertTrue(spans.resizesVertically)
    }

    @Test
    fun `an axis without its resize mode bit is fixed at the default span`() {
        val sizes = ProviderSizes(minWidth = 176f, minHeight = 80f, minResizeWidth = 80f, resizesVertically = true)

        val spans = WidgetCatalog.spans(sizes, grid)
        assertFalse(spans.resizesHorizontally)
        assertEquals(2, spans.min.width)
        assertEquals(2, spans.max.width)
        assertTrue(spans.resizesVertically)
    }

    @Test
    fun `an axis whose min and max span are equal does not resize`() {
        // Resize bits set, but the width range is 2..2 cells.
        val sizes = resizable(minWidth = 176f, minHeight = 80f, minResizeWidth = 176f, maxResizeWidth = 176f)

        val spans = WidgetCatalog.spans(sizes, grid)
        assertFalse(spans.resizesHorizontally)
        assertTrue(spans.resizesVertically)
    }

    @Test
    fun `a widget whose min span fits the largest zone is not too large`() {
        assertFalse(WidgetCatalog.spans(ProviderSizes(minWidth = 368f, minHeight = 464f), grid).tooLarge)
    }

    @Test
    fun `a fixed widget taller than the largest zone is too large`() {
        // 6 rows of min height on a 5-row zone, and it can't resize down.
        assertTrue(WidgetCatalog.spans(ProviderSizes(minWidth = 80f, minHeight = 560f), grid).tooLarge)
    }

    @Test
    fun `a fixed widget wider than the grid is too large`() {
        assertTrue(WidgetCatalog.spans(ProviderSizes(minWidth = 464f, minHeight = 80f), grid).tooLarge)
    }

    @Test
    fun `a big widget that resizes down to fit is not too large`() {
        val sizes = resizable(minWidth = 464f, minHeight = 560f, minResizeWidth = 176f, minResizeHeight = 176f)

        val spans = WidgetCatalog.spans(sizes, grid)
        assertFalse(spans.tooLarge)
        assertEquals(CellSpan(4, 5), spans.default)
        assertEquals(CellSpan(2, 2), spans.min)
    }

    @Test
    fun `the size list reports the cells of a span plus the gaps between them`() {
        assertEquals(listOf(ReportedSize(width = 176f, height = 80f)), WidgetCatalog.sizeList(CellSpan(2, 1), grid))
        assertEquals(listOf(ReportedSize(width = 368f, height = 272f)), WidgetCatalog.sizeList(CellSpan(4, 3), grid))
    }

    @Test
    fun `the size list of one cell is exactly the cell`() {
        val tall = grid.copy(cellWidth = 90f, cellHeight = 100f)

        assertEquals(listOf(ReportedSize(width = 90f, height = 100f)), WidgetCatalog.sizeList(CellSpan(1, 1), tall))
    }

    @Test
    fun `a widget's span bounds are its spans and resize axes`() {
        val spans = WidgetCatalog.spans(resizable(minWidth = 176f, minHeight = 80f, minResizeWidth = 80f, minResizeHeight = 80f), grid)

        assertEquals(
            SpanBounds(CellSpan(2, 1), CellSpan(1, 1), CellSpan(4, 5), resizesHorizontally = true, resizesVertically = true),
            spans.bounds,
        )
    }

    private fun resizable(
        minWidth: Float,
        minHeight: Float,
        minResizeWidth: Float = 0f,
        minResizeHeight: Float = 0f,
        maxResizeWidth: Float = 0f,
        maxResizeHeight: Float = 0f,
        targetCells: CellSpan? = null,
    ) = ProviderSizes(
        minWidth = minWidth,
        minHeight = minHeight,
        minResizeWidth = minResizeWidth,
        minResizeHeight = minResizeHeight,
        maxResizeWidth = maxResizeWidth,
        maxResizeHeight = maxResizeHeight,
        targetCells = targetCells,
        resizesHorizontally = true,
        resizesVertically = true,
    )
}
