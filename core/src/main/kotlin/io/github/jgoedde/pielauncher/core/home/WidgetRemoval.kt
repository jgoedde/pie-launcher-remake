package io.github.jgoedde.pielauncher.core.home

/**
 * Remove with undo (PRD 7.5, Launcher3 `UndoDeleteController`): a removed widget disappears at
 * once, but its id and row are deleted only when the "Item removed" snackbar's
 * [UNDO_TIMEOUT_MS] runs out. Undo puts it back on its cells. One removal is pending at a time:
 * removing another widget, or starting another edit, deletes the pending one for good, so its
 * cells can never be taken while it may still come back. Immutable; times in ms.
 */
class WidgetRemoval private constructor(
    /** The removed widget that may still come back. */
    val pending: LaidOutWidget?,
    /** When [pending] is to be deleted; null when nothing is pending. */
    val dueAtMs: Long?,
) {
    /** A [removal] and the widgets now to delete for good (id and row), if any. */
    data class Step(
        val removal: WidgetRemoval,
        val delete: List<LaidOutWidget> = emptyList(),
        val restored: LaidOutWidget? = null,
    )

    /** [layout] as the Home Page shows it: without the pending widget. */
    fun shown(layout: WidgetLayout): WidgetLayout = pending?.let { (layout.remove(it.id) as? LayoutResult.Changed)?.layout } ?: layout

    /** Removes [widget] at [nowMs]; a removal pending before is deleted for good. */
    fun remove(
        widget: LaidOutWidget,
        nowMs: Long,
    ): Step = Step(WidgetRemoval(widget, nowMs + UNDO_TIMEOUT_MS), listOfNotNull(pending))

    /** Undo: the pending widget comes back ([Step.restored]); nothing when there is none. */
    fun undo(): Step = Step(None, restored = pending)

    /** Deletes the pending widget when its undo window has run out by [nowMs]. */
    fun expire(nowMs: Long): Step = if (dueAtMs != null && nowMs >= dueAtMs) commit() else Step(this)

    /** Deletes the pending widget now, e.g. because another edit starts. */
    fun commit(): Step = Step(None, listOfNotNull(pending))

    companion object {
        /** Launcher3 `Snackbar` timeout on phones. */
        const val UNDO_TIMEOUT_MS = 4000L

        val None = WidgetRemoval(null, null)
    }
}
