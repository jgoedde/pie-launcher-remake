package io.github.jgoedde.pielauncher.core.home

import kotlin.math.ceil

/** A widget's size in cells. */
data class CellSpan(
    val width: Int,
    val height: Int,
)

/** A block of cells in one Widget Zone: top-left cell plus size in cells. */
data class CellRect(
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
) {
    fun overlaps(other: CellRect): Boolean =
        column < other.column + other.width &&
            other.column < column + width &&
            row < other.row + other.height &&
            other.row < row + height
}

/**
 * A widget provider's size: its minimum size in px and, if it declares one, its target size in
 * cells (`targetCellWidth`/`targetCellHeight`).
 */
data class WidgetSize(
    val minWidthPx: Int,
    val minHeightPx: Int,
    val targetCells: CellSpan? = null,
) {
    /** The fewest cells of [cellPx] that hold the minimum size; at least one in each direction. */
    fun minSpan(cellPx: Float): CellSpan = CellSpan(cellsFor(minWidthPx, cellPx), cellsFor(minHeightPx, cellPx))

    /** The target size, raised to the minimum size where it falls short; the minimum without a target. */
    fun defaultSpan(cellPx: Float): CellSpan {
        val min = minSpan(cellPx)
        val target = targetCells ?: return min
        return CellSpan(maxOf(target.width, min.width), maxOf(target.height, min.height))
    }

    private fun cellsFor(
        px: Int,
        cellPx: Float,
    ): Int = ceil(px / cellPx - ROUNDING_TOLERANCE).toInt().coerceAtLeast(1)

    private companion object {
        /** Cells; absorbs px rounding so a minimum of exactly N cells needs N, not N + 1. */
        const val ROUNDING_TOLERANCE = 0.01f
    }
}

/** One Widget Zone as a placement target: its grid and the cells its widgets already cover. */
data class ZoneSlots<K>(
    val zone: K,
    val columns: Int,
    val rows: Int,
    val occupied: List<CellRect>,
)

/** Where a widget was placed: its zone and its cells. */
data class Placed<K>(
    val zone: K,
    val cells: CellRect,
)

/** Where a new widget goes in a Widget Zone (PRD 7.4, 7.6). */
object WidgetPlacement {
    /**
     * The first spot, row by row and left to right, where [span] fits inside a [columns] x [rows]
     * grid without touching any [occupied] cell; null when there is none.
     */
    fun firstFreeSpot(
        columns: Int,
        rows: Int,
        occupied: List<CellRect>,
        span: CellSpan,
    ): CellRect? {
        for (row in 0..rows - span.height) {
            for (column in 0..columns - span.width) {
                val candidate = CellRect(column, row, span.width, span.height)
                if (occupied.none { it.overlaps(candidate) }) return candidate
            }
        }
        return null
    }

    /**
     * Places a widget of [size] in the first of [zones] (in order) that has room: at its default
     * size if that fits, else at its minimum size. Null when it fits nowhere ("No space").
     */
    fun <K> place(
        zones: List<ZoneSlots<K>>,
        size: WidgetSize,
        cellPx: Float,
    ): Placed<K>? {
        val spans = listOf(size.defaultSpan(cellPx), size.minSpan(cellPx)).distinct()
        for (zone in zones) {
            for (span in spans) {
                firstFreeSpot(zone.columns, zone.rows, zone.occupied, span)?.let { return Placed(zone.zone, it) }
            }
        }
        return null
    }
}
