package io.github.jgoedde.pielauncher.core.home

import kotlin.math.floor

/**
 * The cell grid of one Widget Zone: a fixed number of columns of square cells, as many rows as
 * fit, and the leftover height split evenly above and below the rows (cells are never stretched).
 *
 * Units are whatever the caller measures in (dp or px), as long as both sizes use the same one.
 */
data class CellGrid(
    val columns: Int,
    val rows: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    /** Empty space above the first row, and again below the last row. */
    val verticalPadding: Float,
) {
    companion object {
        const val COLUMNS = 4

        /** The square cell edge for a zone of [zoneWidth]: one column wide. */
        fun cellSizeFor(zoneWidth: Float): Float = zoneWidth / COLUMNS

        fun of(
            zoneWidth: Float,
            zoneHeight: Float,
        ): CellGrid {
            val cell = cellSizeFor(zoneWidth)
            val rows = if (cell > 0f) floor(zoneHeight / cell).toInt().coerceAtLeast(0) else 0
            return CellGrid(
                columns = COLUMNS,
                rows = rows,
                cellWidth = cell,
                cellHeight = cell,
                verticalPadding = (zoneHeight - rows * cell) / 2f,
            )
        }
    }
}
