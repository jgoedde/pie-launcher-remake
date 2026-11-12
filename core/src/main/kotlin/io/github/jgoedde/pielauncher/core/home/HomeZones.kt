package io.github.jgoedde.pielauncher.core.home

import kotlin.math.roundToInt

/**
 * The Home Page split into its two Widget Zones (PRD 3.2): the upper zone takes the height the
 * Pie Zone and the lower zone leave; the lower zone is exactly [LOWER_ZONE_ROWS] cell rows.
 * Both share one cell size and the cell gap. Sizes in px, measured inside the mandatory system
 * gesture insets.
 */
data class HomeZones(
    val cellPx: Float,
    val upperHeightPx: Float,
    val lowerHeightPx: Float,
    val upper: CellGrid,
    val lower: CellGrid,
    val pieZoneHeightPx: Float = 0f,
) {
    /** The grid of [zone]: the one place that switches between the Home Page's two zones. */
    fun grid(zone: WidgetZoneId): CellGrid =
        when (zone) {
            WidgetZoneId.HomeUpper -> upper
            WidgetZoneId.HomeLower -> lower
        }

    fun heightPx(zone: WidgetZoneId): Float =
        when (zone) {
            WidgetZoneId.HomeUpper -> upperHeightPx
            WidgetZoneId.HomeLower -> lowerHeightPx
        }

    /** Where [zone] starts, in px from the top of the Page (the top of the upper zone). */
    fun topPx(zone: WidgetZoneId): Float =
        when (zone) {
            WidgetZoneId.HomeUpper -> 0f
            WidgetZoneId.HomeLower -> upperHeightPx + pieZoneHeightPx
        }

    /**
     * What a dragged widget of [span] is over (PRD 7.3), in Page px. The finger picks the zone,
     * or the Pie Zone; above or below the Page it counts as the nearest zone. The widget's
     * top-left corner ([left], [top]) snaps to the nearest cell, kept inside the zone at [span]
     * (cut to the zone's grid).
     */
    fun dragTarget(
        fingerX: Float,
        fingerY: Float,
        left: Float,
        top: Float,
        span: CellSpan,
    ): DragTarget {
        val zone =
            when {
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
            column = nearest(left, grid.cellWidth + grid.gap, grid.columns, span.width),
            row = nearest(top - topPx(zone) - grid.verticalPadding, grid.cellHeight + grid.gap, grid.rows, span.height),
        )
    }

    /** Every Widget Zone of the Home Page with its grid, top to bottom. */
    val grids: Map<WidgetZoneId, CellGrid> get() = WidgetZoneId.entries.associateWith(::grid)

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
        ): HomeZones {
            val cell = CellGrid.cellSizeFor(widthPx, cellGapPx)
            val lowerHeight = cell * LOWER_ZONE_ROWS + cellGapPx * (LOWER_ZONE_ROWS - 1)
            val upperHeight = (heightPx - pieZoneHeightPx - lowerHeight).coerceAtLeast(0f)
            return HomeZones(
                cellPx = cell,
                upperHeightPx = upperHeight,
                lowerHeightPx = lowerHeight,
                upper = CellGrid.of(widthPx, upperHeight, cellGapPx),
                lower = CellGrid.of(widthPx, lowerHeight, cellGapPx),
                pieZoneHeightPx = pieZoneHeightPx,
            )
        }
    }
}
