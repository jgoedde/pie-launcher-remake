package io.github.jgoedde.pielauncher.core.home

/**
 * Where a new widget dragged onto Home (from the Widget Picker, or a Pin Request) was dropped:
 * its [page] and [zone], its [cells] (its span, shrunk if the drop shrank it), and the neighbours
 * of that zone the drop moved, at their new cells. The widget is bound and configured only after
 * the drop, so this is kept until then and committed with [WidgetLayout.place]; a cancel simply
 * drops it, and nothing has moved. The Page may be one the drag just made, past the last one.
 */
data class WidgetDrop(
    val page: Int,
    val zone: WidgetZoneId,
    val cells: CellRect,
    val moved: Map<Int, CellRect> = emptyMap(),
) {
    /** This drop as ints, for an Intent extra: Page, zone, cells, then id and cells per moved neighbour. */
    fun toInts(): IntArray =
        (listOf(page, zone.ordinal) + cells.ints() + moved.flatMap { (id, cells) -> listOf(id) + cells.ints() }).toIntArray()

    companion object {
        private const val HEAD = 6

        /** The drop [toInts] wrote; null when [ints] are not one. */
        fun fromInts(ints: IntArray): WidgetDrop? {
            if (ints.size < HEAD || (ints.size - HEAD) % 5 != 0 || ints[0] < HOME_PAGE) return null
            val zone = WidgetZoneId.entries.getOrNull(ints[1]) ?: return null
            val moved = (HEAD until ints.size step 5).associate { at -> ints[at] to ints.rect(at + 1) }
            return WidgetDrop(ints[0], zone, ints.rect(2), moved)
        }

        /** The drop a [WidgetDrag.drop] of a new widget on [page] committed. */
        fun of(
            page: Int,
            result: LayoutResult.Changed,
        ): WidgetDrop = WidgetDrop(page, result.widget.zone, result.widget.cells, result.moved.associate { it.id to it.cells })
    }
}

private fun CellRect.ints() = listOf(column, row, width, height)

private fun IntArray.rect(at: Int) = CellRect(this[at], this[at + 1], this[at + 2], this[at + 3])
