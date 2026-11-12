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
    companion object {
        /** The drop a [WidgetDrag.drop] of a new widget on [page] committed. */
        fun of(
            page: Int,
            result: LayoutResult.Changed,
        ): WidgetDrop = WidgetDrop(page, result.widget.zone, result.widget.cells, result.moved.associate { it.id to it.cells })
    }
}
