package io.github.jgoedde.pielauncher.core.home

/**
 * The Home Page split into its two Widget Zones (PRD 3.2): the upper zone takes the height the
 * Pie Zone and the lower zone leave; the lower zone is exactly [LOWER_ZONE_ROWS] cell rows.
 * Sizes in px, measured inside the mandatory system gesture insets.
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

    companion object {
        const val LOWER_ZONE_ROWS = 1

        fun of(
            widthPx: Float,
            heightPx: Float,
            pieZoneHeightPx: Float,
        ): HomeZones {
            val cell = CellGrid.cellSizeFor(widthPx)
            val lowerHeight = cell * LOWER_ZONE_ROWS
            val upperHeight = (heightPx - pieZoneHeightPx - lowerHeight).coerceAtLeast(0f)
            return HomeZones(
                cellPx = cell,
                upperHeightPx = upperHeight,
                lowerHeightPx = lowerHeight,
                upper = CellGrid.of(widthPx, upperHeight),
                lower = CellGrid.of(widthPx, lowerHeight),
            )
        }
    }
}
