package io.github.jgoedde.pielauncher.core.home

/**
 * One widget drag over the Page (PRD 7.3), with Launcher3's reorder timing
 * (`Workspace.manageReorderOnDragOver`): over cells the widget fits on as they are, its target
 * cells are previewed at once; where neighbours would have to move, the reorder preview shows
 * only once the finger has rested [REORDER_TIMEOUT_MS] on one target cell. Moving to another cell
 * restarts the rest and puts the neighbours back. Every preview and the drop come from the same
 * [WidgetLayout.dragOver] on the layout the drag started on, so a drop commits what was shown.
 *
 * The dragged widget is either one picked up from the Page ([pickUp]) or a new one ([start],
 * e.g. dragged in from the Widget Picker); both take the same path. Immutable; times in ms.
 */
class WidgetDrag private constructor(
    /** The layout the drag started on; never changes during the drag. */
    val base: WidgetLayout,
    val dragged: DraggedWidget,
    /** The cell or Pie Zone the widget is over; null off the Widget Zones (e.g. over the drop-target bar). */
    val target: DragTarget?,
    private val targetSinceMs: Long,
    private val reorder: LayoutResult?,
) {
    /** Whether the reorder for [target] is on show (or needs no rest). */
    private val shown: LayoutResult.Changed? get() = reorder as? LayoutResult.Changed

    /**
     * The layout to draw while dragging: [base], or the reorder on show with the moved neighbours
     * at their temporary spots and the dragged widget at its target cells.
     */
    val preview: WidgetLayout get() = shown?.layout ?: base

    /** The neighbours the reorder on show moves, at their temporary spots. */
    val moved: List<LaidOutWidget> get() = shown?.moved.orEmpty()

    /** The dragged widget's target cells and their zone, when a drop here would land. */
    val outline: Pair<WidgetZoneId, CellRect>? get() = shown?.widget?.let { it.zone to it.cells }

    /** When the reorder for [target] is due; null when it is on show or there is nothing to wait for. */
    val reorderDueAtMs: Long? get() = if (target is DragTarget.Cell && reorder == null) targetSinceMs + REORDER_TIMEOUT_MS else null

    /** The finger is over [target] at [nowMs]. */
    fun moveTo(
        target: DragTarget?,
        nowMs: Long,
    ): WidgetDrag {
        if (target == this.target) return tick(nowMs)
        return WidgetDrag(base, dragged, target, nowMs, immediate(target))
    }

    /** Time passes: shows the reorder once the finger has rested long enough on one cell. */
    fun tick(nowMs: Long): WidgetDrag {
        val due = reorderDueAtMs ?: return this
        if (nowMs < due) return this
        return WidgetDrag(base, dragged, target, targetSinceMs, base.dragOver(dragged, target!!))
    }

    /**
     * Drops the widget over [target] (Launcher3 `MODE_ON_DROP`): the reorder for that target, rested
     * or not. Rejected on the Pie Zone or where nothing fits; [LayoutResult.Unchanged] off the zones.
     */
    fun drop(): LayoutResult = target?.let { base.drop(dragged, it) } ?: LayoutResult.Unchanged

    /** The reorder that shows without a rest: where the widget fits on [target] as the cells are. */
    private fun immediate(target: DragTarget?): LayoutResult? {
        if (target !is DragTarget.Cell) return null
        val result = base.dragOver(dragged, target) as? LayoutResult.Changed ?: return null
        val inPlace = result.moved.isEmpty() && result.widget.cells.column == target.column && result.widget.cells.row == target.row
        return result.takeIf { inPlace }
    }

    companion object {
        /** Launcher3 `Workspace.REORDER_TIMEOUT`: how long the finger rests before neighbours move. */
        const val REORDER_TIMEOUT_MS = 650L

        /** Starts moving the placed widget [id] of [layout]; null when there is no such widget. */
        fun pickUp(
            layout: WidgetLayout,
            id: Int,
            nowMs: Long,
        ): WidgetDrag? = layout.pickUp(id)?.let { start(layout, it, nowMs) }

        /** Starts dragging [dragged] over [layout], not yet over any cell. */
        fun start(
            layout: WidgetLayout,
            dragged: DraggedWidget,
            nowMs: Long,
        ): WidgetDrag = WidgetDrag(layout, dragged, null, nowMs, null)
    }
}
