package io.github.jgoedde.pielauncher.core.home

import kotlin.math.floor

/**
 * The cell grid of one Widget Zone: a fixed number of columns of square cells, as many rows as
 * fit, and the leftover height split evenly above and below the rows (cells are never stretched).
 *
 * Sizes are in px. Layout rounds sizes to whole pixels, so a zone that falls short of a row by
 * at most half a pixel (rounding error) still gets that row.
 */
data class CellGrid(
    val columns: Int,
    val rows: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    /** Empty space above the first row, and again below the last row. */
    val verticalPadding: Float,
) {
    /** The px bounds of [cells] inside the zone, rows starting below the zone's top padding. */
    fun boundsOf(cells: CellRect): PxRect {
        val left = cells.column * cellWidth
        val top = verticalPadding + cells.row * cellHeight
        return PxRect(left, top, left + cells.width * cellWidth, top + cells.height * cellHeight)
    }

    companion object {
        const val COLUMNS = 4

        /** px; absorbs layout rounding so a zone sized to N cells holds N rows. */
        private const val ROUNDING_TOLERANCE = 0.5f

        /** The square cell edge for a zone of [zoneWidth]: one column wide. */
        fun cellSizeFor(zoneWidth: Float): Float = zoneWidth / COLUMNS

        fun of(
            zoneWidth: Float,
            zoneHeight: Float,
        ): CellGrid {
            val cell = cellSizeFor(zoneWidth)
            val rows = if (cell > 0f) floor((zoneHeight + ROUNDING_TOLERANCE) / cell).toInt().coerceAtLeast(0) else 0
            return CellGrid(
                columns = COLUMNS,
                rows = rows,
                cellWidth = cell,
                cellHeight = cell,
                verticalPadding = ((zoneHeight - rows * cell) / 2f).coerceAtLeast(0f),
            )
        }
    }
}
