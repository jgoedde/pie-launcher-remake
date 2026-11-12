package io.github.jgoedde.pielauncher.core.home

/**
 * Where a new widget dragged onto Home (from the Widget Picker, or a Pin Request) was dropped:
 * its [zone] and [cells] (its span, shrunk if the drop shrank it), and the neighbours of that
 * zone the drop moved, at their new cells. The widget is bound and configured only after the
 * drop, so this is kept until then and committed with [WidgetLayout.place]; a cancel simply
 * drops it, and nothing has moved.
 */
data class WidgetDrop(
    val zone: WidgetZoneId,
    val cells: CellRect,
    val moved: Map<Int, CellRect> = emptyMap(),
) {
    /** This drop as ints, for an Intent extra: zone, cells, then id and cells per moved neighbour. */
    fun toInts(): IntArray =
        (listOf(zone.ordinal) + cells.ints() + moved.flatMap { (id, cells) -> listOf(id) + cells.ints() }).toIntArray()

    companion object {
        /** The drop [toInts] wrote; null when [ints] are not one. */
        fun fromInts(ints: IntArray): WidgetDrop? {
            if (ints.size < 5 || (ints.size - 5) % 5 != 0) return null
            val zone = WidgetZoneId.entries.getOrNull(ints[0]) ?: return null
            val moved = (5 until ints.size step 5).associate { at -> ints[at] to ints.rect(at + 1) }
            return WidgetDrop(zone, ints.rect(1), moved)
        }

        /** The drop a [WidgetDrag.drop] of a new widget committed. */
        fun of(result: LayoutResult.Changed): WidgetDrop =
            WidgetDrop(result.widget.zone, result.widget.cells, result.moved.associate { it.id to it.cells })
    }
}

private fun CellRect.ints() = listOf(column, row, width, height)

private fun IntArray.rect(at: Int) = CellRect(this[at], this[at + 1], this[at + 2], this[at + 3])
