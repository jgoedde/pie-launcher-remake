package io.github.jgoedde.pielauncher.core.home

import kotlin.math.floor

/**
 * The cell grid of one Widget Zone: a fixed number of columns of square cells with a constant
 * [gap] between neighbouring cells (not at the zone edges), as many rows as fit, and the leftover
 * height split evenly above and below the rows (cells are never stretched). The columns sit
 * [horizontalPadding] in from each side edge of the zone. The gap is the only spacing between
 * widgets: they fill their cells edge to edge (Launcher3 border spacing).
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
    /** Space between two neighbouring cells, in both directions. */
    val gap: Float = 0f,
    /** Empty space left of the first column, and again right of the last column. */
    val horizontalPadding: Float = 0f,
) {
    /** Where [cells] lie inside the zone: their cells plus the gaps between them. */
    fun bounds(cells: CellRect): CellBounds =
        CellBounds(
            left = horizontalPadding + cells.column * (cellWidth + gap),
            top = verticalPadding + cells.row * (cellHeight + gap),
            width = spanLength(cells.width, cellWidth, gap),
            height = spanLength(cells.height, cellHeight, gap),
        )

    companion object {
        const val COLUMNS = 4

        /** The gap between cells, in dp (Launcher3 phone `borderSpace`). A code constant by design. */
        const val CELL_GAP_DP = 16f

        /** The inset of every Widget Zone's grid from the zone's side edges, in dp. A code constant by design. */
        const val ZONE_PADDING_DP = 16f

        /** px; absorbs layout rounding so a zone sized to N cells holds N rows. */
        private const val ROUNDING_TOLERANCE = 0.5f

        /** The square cell edge for a zone of [zoneWidth]: one column wide, with [gap] between columns. */
        fun cellSizeFor(
            zoneWidth: Float,
            gap: Float,
        ): Float = ((zoneWidth - (COLUMNS - 1) * gap) / COLUMNS).coerceAtLeast(0f)

        fun of(
            zoneWidth: Float,
            zoneHeight: Float,
            gap: Float,
            horizontalPadding: Float = 0f,
        ): CellGrid {
            val cell = cellSizeFor(zoneWidth - 2 * horizontalPadding, gap)
            val rows =
                if (cell > 0f) floor((zoneHeight + gap + ROUNDING_TOLERANCE) / (cell + gap)).toInt().coerceAtLeast(0) else 0
            return CellGrid(
                columns = COLUMNS,
                rows = rows,
                cellWidth = cell,
                cellHeight = cell,
                verticalPadding = ((zoneHeight - spanLength(rows, cell, gap)) / 2f).coerceAtLeast(0f),
                gap = gap,
                horizontalPadding = horizontalPadding,
            )
        }

        /** The length of [count] cells of [cell] with [gap] between them; 0 for none. */
        private fun spanLength(
            count: Int,
            cell: Float,
            gap: Float,
        ): Float = if (count <= 0) 0f else count * cell + (count - 1) * gap
    }
}

/** A rectangle inside a Widget Zone, in the zone's px. */
data class CellBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
