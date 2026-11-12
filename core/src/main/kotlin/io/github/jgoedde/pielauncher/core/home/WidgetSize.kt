package io.github.jgoedde.pielauncher.core.home

import kotlin.math.ceil

/**
 * A widget provider's size: its minimum size in px and, if it declares one, its target size in
 * cells (`targetCellWidth`/`targetCellHeight`). Interim span source until the Widget Catalog
 * (#54) computes spans the Launcher3 way.
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
