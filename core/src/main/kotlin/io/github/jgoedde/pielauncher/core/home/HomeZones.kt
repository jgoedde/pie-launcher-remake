package io.github.jgoedde.pielauncher.core.home

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
        const val LOWER_ZONE_ROWS = 1

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
            )
        }
    }
}
