package io.github.jgoedde.pielauncher.core.home

import kotlin.math.roundToInt

/** The Home Page is the leftmost Page: its index in a widget row. */
const val HOME_PAGE = 0

/**
 * The Widget Zones of the Pages (PRD 3.2, 3.3). The Home Page has two: the upper zone takes the
 * height the Pie Zone and the lower zone leave; the lower zone is exactly [LOWER_ZONE_ROWS] cell
 * rows. Every other Page is one zone over its full height ([page]). All share one cell size, the
 * cell gap and the zone padding at the side edges. Sizes in px, measured inside the mandatory
 * system gesture insets.
 */
data class HomeZones(
    val cellPx: Float,
    val upperHeightPx: Float,
    val lowerHeightPx: Float,
    val upper: CellGrid,
    val lower: CellGrid,
    val pieZoneHeightPx: Float = 0f,
    /** The one zone of a non-Home Page. */
    val page: CellGrid = CellGrid.of(0f, 0f, 0f),
) {
    /** A whole Page's height: the Home Page's zones plus its Pie Zone. */
    val pageHeightPx: Float get() = upperHeightPx + pieZoneHeightPx + lowerHeightPx

    /** The grid of [zone]: the one place that switches between the zones. */
    fun grid(zone: WidgetZoneId): CellGrid =
        when (zone) {
            WidgetZoneId.HomeUpper -> upper
            WidgetZoneId.HomeLower -> lower
            WidgetZoneId.Page -> page
        }

    fun heightPx(zone: WidgetZoneId): Float =
        when (zone) {
            WidgetZoneId.HomeUpper -> upperHeightPx
            WidgetZoneId.HomeLower -> lowerHeightPx
            WidgetZoneId.Page -> pageHeightPx
        }

    /** Where [zone] starts, in px from the top of the Page (the top of the upper zone). */
    fun topPx(zone: WidgetZoneId): Float =
        when (zone) {
            WidgetZoneId.HomeUpper, WidgetZoneId.Page -> 0f
            WidgetZoneId.HomeLower -> upperHeightPx + pieZoneHeightPx
        }

    /**
     * What a dragged widget of [span] is over on [page] (PRD 7.3), in Page px. On the Home Page
     * the finger picks the zone, or the Pie Zone; above or below the Page it counts as the nearest
     * zone. Any other Page is all one zone. The widget's
     * top-left corner ([left], [top]) snaps to the nearest cell, kept inside the zone at [span]
     * (cut to the zone's grid).
     */
    fun dragTarget(
        fingerX: Float,
        fingerY: Float,
        left: Float,
        top: Float,
        span: CellSpan,
        page: Int = HOME_PAGE,
    ): DragTarget {
        val zone =
            when {
                page != HOME_PAGE -> WidgetZoneId.Page
                fingerY < upperHeightPx -> WidgetZoneId.HomeUpper
                fingerY < upperHeightPx + pieZoneHeightPx -> return DragTarget.PieZone
                else -> WidgetZoneId.HomeLower
            }
        val grid = grid(zone)

        fun nearest(
            offset: Float,
            pitch: Float,
            count: Int,
            size: Int,
        ) = if (pitch <= 0f) 0 else (offset / pitch).roundToInt().coerceAtMost(count - minOf(size, count)).coerceAtLeast(0)
        return DragTarget.Cell(
            zone,
            column = nearest(left - grid.horizontalPadding, grid.cellWidth + grid.gap, grid.columns, span.width),
            row = nearest(top - topPx(zone) - grid.verticalPadding, grid.cellHeight + grid.gap, grid.rows, span.height),
        )
    }

    /** Every Widget Zone of [page] with its grid, top to bottom. */
    fun grids(page: Int): Map<WidgetZoneId, CellGrid> = WidgetZoneId.of(page).associateWith(::grid)

    /**
     * The grid widgets are sized against (Widget Catalog): the zone with more rows, converted to
     * dp with [pxPerDp].
     */
    fun catalogGrid(pxPerDp: Float): CatalogGrid {
        val largest = if (upper.rows >= lower.rows) upper else lower
        return CatalogGrid(
            columns = largest.columns,
            rows = largest.rows,
            cellWidth = largest.cellWidth / pxPerDp,
            cellHeight = largest.cellHeight / pxPerDp,
            spacing = largest.gap / pxPerDp,
        )
    }

    companion object {
        const val LOWER_ZONE_ROWS = 2

        fun of(
            widthPx: Float,
            heightPx: Float,
            pieZoneHeightPx: Float,
            cellGapPx: Float,
            zonePaddingPx: Float = 0f,
        ): HomeZones {
            val cell = CellGrid.cellSizeFor(widthPx - 2 * zonePaddingPx, cellGapPx)
            val lowerHeight = cell * LOWER_ZONE_ROWS + cellGapPx * (LOWER_ZONE_ROWS - 1)
            val upperHeight = (heightPx - pieZoneHeightPx - lowerHeight).coerceAtLeast(0f)
            return HomeZones(
                cellPx = cell,
                upperHeightPx = upperHeight,
                lowerHeightPx = lowerHeight,
                upper = CellGrid.of(widthPx, upperHeight, cellGapPx, zonePaddingPx),
                lower = CellGrid.of(widthPx, lowerHeight, cellGapPx, zonePaddingPx),
                pieZoneHeightPx = pieZoneHeightPx,
                page = CellGrid.of(widthPx, upperHeight + pieZoneHeightPx + lowerHeight, cellGapPx, zonePaddingPx),
            )
        }
    }
}
