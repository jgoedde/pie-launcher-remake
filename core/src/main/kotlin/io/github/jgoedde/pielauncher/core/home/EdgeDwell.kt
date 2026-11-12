package io.github.jgoedde.pielauncher.core.home

/** A side edge of the Page that a dragged widget can dwell at. */
enum class PageEdge {
    Left,
    Right,
    ;

    companion object {
        /** The edge whose [edgePx] wide strip [fingerX] is in, on a Page [widthPx] wide; null between them. */
        fun at(
            fingerX: Float,
            widthPx: Float,
            edgePx: Float,
        ): PageEdge? =
            when {
                fingerX < edgePx -> Left
                fingerX >= widthPx - edgePx -> Right
                else -> null
            }
    }
}

/**
 * The finger of a widget drag dwelling at a Page [edge] since [sinceMs] (PRD 7.3): after
 * [DWELL_MS] the Page changes to the neighbour on that side, and a new Page is made at the right
 * end. Staying there turns again after another dwell. Immutable; times in ms.
 */
class EdgeDwell private constructor(
    val edge: PageEdge?,
    private val sinceMs: Long,
) {
    /** A Page change: to [page], with the [dwell] restarted there. */
    class Turn(
        val page: Int,
        val dwell: EdgeDwell,
    )

    /** When the Page is due to change; null away from the edges. */
    val dueAtMs: Long? get() = edge?.let { sinceMs + DWELL_MS }

    /** The finger is at [edge] (null: at none) at [nowMs]; a new edge restarts the dwell. */
    fun at(
        edge: PageEdge?,
        nowMs: Long,
    ): EdgeDwell = if (edge == this.edge) this else EdgeDwell(edge, nowMs)

    /**
     * The Page change due by [nowMs] on [page] of [pageCount], or null. Past the last Page a new
     * Page is made only when [canAppend] (the last Page holds more than the dragged widget).
     */
    fun turn(
        nowMs: Long,
        page: Int,
        pageCount: Int,
        canAppend: Boolean,
    ): Turn? {
        val due = dueAtMs ?: return null
        if (nowMs < due) return null
        val next =
            when (edge) {
                PageEdge.Left -> (page - 1).takeIf { it >= HOME_PAGE }
                PageEdge.Right -> (page + 1).takeIf { it < pageCount || canAppend }
                null -> null
            } ?: return null
        return Turn(next, EdgeDwell(edge, nowMs))
    }

    companion object {
        /** How long the finger dwells at an edge before the Page changes (Launcher3 spring-loaded scroll). */
        const val DWELL_MS = 500L

        val None = EdgeDwell(null, 0)
    }
}
