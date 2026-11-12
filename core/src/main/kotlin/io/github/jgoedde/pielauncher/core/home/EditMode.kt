package io.github.jgoedde.pielauncher.core.home

/**
 * The Home Page's Edit Mode (PRD 7.2, GLOSSARY): from the long-press on a widget until its move
 * has ended and its Resize Frame has closed. Outside [Idle] the Pie is off and the
 * Pie Zone is drawn blocked. Back or Home [leave]s it from any state. Immutable: every
 * transition returns the next state; one that does not apply returns the state unchanged.
 */
sealed interface EditMode {
    /** At rest: no widget is being edited. */
    data object Idle : EditMode

    /**
     * Widget [widgetId] with its Resize Frame open: after a long-press (the finger may still be
     * down) with its popup open, or after an add or a handle drag with the popup closed.
     */
    data class Selected(
        val widgetId: Int,
        val popupOpen: Boolean = true,
    ) : EditMode

    /** A Resize Frame handle is dragged; the popup is closed. */
    data class Resizing(
        val resize: WidgetResize,
    ) : EditMode

    /** A widget follows the finger over the Page: one picked up here, or (#61) one dragged in from the Widget Picker. */
    data class Dragging(
        val drag: WidgetDrag,
    ) : EditMode

    /** What a finger lift leads to: the next [mode], and the drop's [result] ([LayoutResult.Unchanged] when nothing dropped). */
    data class Release(
        val mode: EditMode,
        val result: LayoutResult,
    )

    val pieEnabled: Boolean get() = this == Idle

    /** A long-press on widget [id] selects it, unless a drag or a resize is under way. */
    fun longPress(id: Int): EditMode = if (this is Dragging || this is Resizing) this else Selected(id)

    /** Widget [id] was just added: its Resize Frame opens, with no popup (Launcher3). */
    fun added(id: Int): EditMode = if (this is Dragging || this is Resizing) this else Selected(id, popupOpen = false)

    /** The popup closes; the Resize Frame stays open. */
    fun closePopup(): EditMode = if (this is Selected) copy(popupOpen = false) else this

    /** A finger went down on the [edge] handle of the selected widget of [layout]. */
    fun startResize(
        layout: WidgetLayout,
        edge: ResizeEdge,
    ): EditMode {
        if (this !is Selected) return this
        return WidgetResize.start(layout, widgetId, edge)?.let(::Resizing) ?: this
    }

    /** The handle is [deltaPx] from where it went down. */
    fun resizeBy(deltaPx: Float): EditMode = if (this is Resizing) Resizing(resize.dragBy(deltaPx)) else this

    /** The finger moved past touch slop after the long-press: the selected widget of [layout] is picked up. */
    fun startMove(
        layout: WidgetLayout,
        nowMs: Long,
    ): EditMode {
        if (this !is Selected) return this
        return WidgetDrag.pickUp(layout, widgetId, nowMs)?.let(::Dragging) ?: this
    }

    /** A drag that started elsewhere (#61: a Widget Picker preview dragged onto Home) takes over. */
    fun startDrag(drag: WidgetDrag): EditMode = Dragging(drag)

    /** The dragged widget is over [target] at [nowMs]. */
    fun dragTo(
        target: DragTarget?,
        nowMs: Long,
    ): EditMode = if (this is Dragging) Dragging(drag.moveTo(target, nowMs)) else this

    /** Time passes during a drag (the 650 ms reorder rest). */
    fun tick(nowMs: Long): EditMode = if (this is Dragging) Dragging(drag.tick(nowMs)) else this

    /**
     * The finger lifts. A drag drops (committed, or rejected and sprung back) and Edit Mode ends;
     * a resize commits its span and the frame stays open, snapped to the widget; a selected
     * widget keeps its popup open.
     */
    fun release(): Release =
        when (this) {
            is Dragging -> Release(Idle, drag.drop())
            is Resizing -> Release(Selected(resize.widgetId, popupOpen = false), resize.result)
            else -> Release(this, LayoutResult.Unchanged)
        }

    /** Back, Home, a tap outside or a popup choice: Edit Mode ends. A drag under way springs back. */
    fun leave(): EditMode = Idle
}
