package io.github.jgoedde.pielauncher.core.home

/**
 * One drag of a Resize Frame handle (PRD 7.2, Launcher3 `AppWidgetResizeFrame`): the [edge] of
 * widget [widgetId] dragged some px from where the finger went down. Every step resizes [base],
 * the layout the drag started on, by the whole delta ([WidgetLayout.resize]), so dragging back
 * restores the span and the pushed neighbours. A cell of travel is a cell plus the gap. The
 * frame's dragged edge follows the finger freely; the widget snaps by cells. Immutable.
 */
class WidgetResize private constructor(
    /** The layout the handle drag started on; never changes during the drag. */
    val base: WidgetLayout,
    val widgetId: Int,
    val edge: ResizeEdge,
    private val deltaPx: Float,
    /** The resize of [base] for the delta so far: [LayoutResult.Unchanged] when bounded, blocked or short of the threshold. */
    val result: LayoutResult,
    /** Whether this step changed the widget's span: one `SEGMENT_TICK`. */
    val ticked: Boolean,
) {
    /** The layout to draw: the resized widget and the neighbours it pushed at their new spots. */
    val preview: WidgetLayout get() = (result as? LayoutResult.Changed)?.layout ?: base

    /** The widget's span as resized so far. */
    val span: CellSpan get() = preview.widget(widgetId)!!.cells.span

    /** The frame in its zone's px: the widget's bounds at the start, the dragged edge moved by the finger. */
    val frame: CellBounds
        get() {
            val start = base.boundsOf(base.widget(widgetId)!!)
            return when (edge) {
                ResizeEdge.Left -> {
                    val left = minOf(start.left + deltaPx, start.left + start.width)
                    start.copy(left = left, width = start.left + start.width - left)
                }
                ResizeEdge.Top -> {
                    val top = minOf(start.top + deltaPx, start.top + start.height)
                    start.copy(top = top, height = start.top + start.height - top)
                }
                ResizeEdge.Right -> start.copy(width = (start.width + deltaPx).coerceAtLeast(0f))
                ResizeEdge.Bottom -> start.copy(height = (start.height + deltaPx).coerceAtLeast(0f))
            }
        }

    /** The handle is now [deltaPx] from where it started (positive is right or down). */
    fun dragBy(deltaPx: Float): WidgetResize {
        val widget = base.widget(widgetId)!!
        val grid = base.grids.getValue(widget.zone)
        val pitch = if (edge.horizontal) grid.cellWidth + grid.gap else grid.cellHeight + grid.gap
        val result = if (pitch > 0f) base.resize(widgetId, edge, deltaPx / pitch) else LayoutResult.Unchanged
        val next = WidgetResize(base, widgetId, edge, deltaPx, result, ticked = false)
        return WidgetResize(base, widgetId, edge, deltaPx, result, ticked = next.span != span)
    }

    companion object {
        /** Starts dragging the [edge] handle of widget [id] of [layout]; null when that edge has no handle. */
        fun start(
            layout: WidgetLayout,
            id: Int,
            edge: ResizeEdge,
        ): WidgetResize? {
            if (edge !in layout.resizeEdges(id)) return null
            return WidgetResize(layout, id, edge, 0f, LayoutResult.Unchanged, ticked = false)
        }
    }
}
