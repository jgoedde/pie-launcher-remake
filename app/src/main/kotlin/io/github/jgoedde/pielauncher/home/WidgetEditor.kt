package io.github.jgoedde.pielauncher.home

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.jgoedde.pielauncher.core.home.CellRect
import io.github.jgoedde.pielauncher.core.home.DraggedWidget
import io.github.jgoedde.pielauncher.core.home.EdgeDwell
import io.github.jgoedde.pielauncher.core.home.EditMode
import io.github.jgoedde.pielauncher.core.home.HOME_PAGE
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.PageEdge
import io.github.jgoedde.pielauncher.core.home.PageLayouts
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.ResizeEdge
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetDrag
import io.github.jgoedde.pielauncher.core.home.WidgetDrop
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetRemoval
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.widgets.WidgetTouchListener

/** A button of the drop-target bar shown at the top during a drag (PRD 7.3). */
internal enum class DropAction { Remove, WidgetSettings }

/** The dragged widget's picture, following the finger: drawn at [finger] minus [grab], in window px. */
internal data class DragImage(
    val image: ImageBitmap,
    val grab: Offset,
    val finger: Offset,
)

/** What Edit Mode does to the world outside the Home Page. */
internal interface WidgetEditActions {
    /** Persist [layout] of [page]: a committed move and its reflow. */
    fun save(
        page: Int,
        layout: WidgetLayout,
    )

    /** Delete widget [id] for good (its undo window is over). */
    fun discard(id: Int)

    fun isReconfigurable(id: Int): Boolean

    /** Open widget [id]'s configure screen again ("Widget settings"). */
    fun reconfigure(id: Int)

    /** A drop was refused; the widget sprang back. */
    fun rejected(reason: Rejection)
}

/**
 * The Home Page's Edit Mode (PRD 7.2–7.5) on Compose state: the [EditMode] from `:core`, the
 * removal waiting for its undo window ([WidgetRemoval]), and the drag's picture. It turns the
 * long-press wrapper's touch reports into Edit Mode transitions. The Pages tell it their layouts
 * and geometry ([laidOut]) so window points can be mapped to Widget Zone cells.
 *
 * It holds the [page] shown, which the pager follows: a drag dwelling at a Page edge changes it
 * ([EdgeDwell]), making a new Page at the right end, and the drag goes on over that Page.
 *
 * In [EditMode.Selected] and [EditMode.Resizing] the Resize Frame is open ([frame]); its handle
 * drags resize the widget and commit on release. A new widget dragged in from outside
 * ([startNewDrag]) takes the same move path.
 */
@Stable
internal class WidgetEditor(
    private val actions: WidgetEditActions,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    var mode by mutableStateOf<EditMode>(EditMode.Idle)
        private set

    var removal by mutableStateOf(WidgetRemoval.None)
        private set

    var dragImage by mutableStateOf<DragImage?>(null)
        private set

    /** The drop-target bar button the finger is over. */
    var hovered by mutableStateOf<DropAction?>(null)
        private set

    /** The drop-target bar buttons' bounds in window px, as laid out. */
    val dropTargets = mutableStateMapOf<DropAction, Rect>()

    /** Per Page, a committed drop shown until the stored rows catch up: (the layout it was made on, the result). */
    private var committed by mutableStateOf<Map<Int, Pair<WidgetLayout, WidgetLayout>>>(emptyMap())

    /** Widgets deleted for good, hidden until their rows are gone. */
    private var discarded by mutableStateOf(emptySet<Int>())

    // State, so the Resize Frame follows the Pages' layouts and position.
    private var layouts by mutableStateOf(PageLayouts(listOf(WidgetLayout(emptyMap(), emptyList())), WidgetLayout(emptyMap(), emptyList())))
    private var zones by mutableStateOf<HomeZones?>(null)
    private var pageOrigin by mutableStateOf(Offset.Zero)
    private var pageWidth = 0f
    private var edgePx = 0f

    /** The Page shown, or to scroll to. */
    var page by mutableStateOf(HOME_PAGE)
        private set

    /** At least this many Pages show: a Page a drag just made, before anything is stored on it. */
    var minPages by mutableStateOf(1)
        private set

    /** The Page a picked-up widget came from. */
    private var dragOrigin = HOME_PAGE

    private var dwell by mutableStateOf(EdgeDwell.None)
    private var lastFinger: Offset? = null

    /** Where the finger holds the dragged widget, from its top left, in px. */
    private var grab = Offset.Zero

    /** Whether the drag in progress is of a widget that offers "Widget settings". */
    var draggedIsReconfigurable by mutableStateOf(false)
        private set

    /**
     * The Pages laid out [layouts] on [zones], each Page [widthPx] wide with its top at [origin]
     * in the window; a drag dwelling within [edgePx] of a side changes the Page.
     */
    fun laidOut(
        layouts: PageLayouts,
        zones: HomeZones,
        origin: Offset,
        widthPx: Float,
        edgePx: Float,
    ) {
        this.layouts = layouts
        this.zones = zones
        pageOrigin = origin
        pageWidth = widthPx
        this.edgePx = edgePx
    }

    /** Home pressed: the Home Page shows (the pager scrolls there when another Page is shown). */
    fun showHomePage() {
        page = HOME_PAGE
    }

    /** The pager settled on [page]; a drag drives the Page itself. */
    fun pageSettled(page: Int) {
        if (mode !is EditMode.Dragging) this.page = page
    }

    /** [page] as Home shows it outside a drag: a just-committed drop, minus removed widgets. */
    fun shown(
        page: Int,
        layout: WidgetLayout = layoutOf(page),
    ): WidgetLayout {
        val justDropped = committed[page]?.takeIf { it.first === layout }?.second ?: layout
        val kept =
            if (discarded.isEmpty()) {
                justDropped
            } else {
                WidgetLayout(
                    justDropped.grids,
                    justDropped.widgets.filter {
                        it.id !in
                            discarded
                    },
                )
            }
        return removal.shown(kept)
    }

    /** When the drag next needs [tick]: its reorder rest, or its dwell at a Page edge. */
    val dueAtMs: Long?
        get() {
            val drag = (mode as? EditMode.Dragging)?.drag ?: return null
            return listOfNotNull(drag.reorderDueAtMs, dwell.dueAtMs).minOrNull()
        }

    /** The stored layout of [page]; empty past the last Page. */
    private fun layoutOf(page: Int): WidgetLayout =
        layouts.pages.getOrNull(page) ?: zones?.let { WidgetLayout(it.grids(page), emptyList()) } ?: layouts.newPage

    /** The Page widget [id] is on as shown; the Page shown when it is on none. */
    private fun pageOf(id: Int): Int = layouts.pages.indices.firstOrNull { shown(it).widget(id) != null } ?: page

    /** The touch reports of widget [id]'s long-press wrapper. */
    fun touchesOf(id: Int): WidgetTouchListener = Touches(id)

    /** Back, Home, a tap outside the Resize Frame: Edit Mode ends; a drag under way springs back, a resize commits. */
    fun leave() {
        endResize()
        // A picked-up widget springs back onto its own Page, which shows again.
        if ((mode as? EditMode.Dragging)?.drag?.dragged?.isNew == false) page = dragOrigin
        if (mode is EditMode.Dragging) minPages = 1
        mode = mode.leave()
        endDrag()
    }

    /** Widget [id] was just added on [page]: that Page shows and its Resize Frame opens (Launcher3), once the widget shows. */
    fun added(
        id: Int,
        page: Int,
    ) {
        minPages = 1
        this.page = page
        mode = mode.added(id)
    }

    /** An add ended with nothing added: a Page made for it goes again. */
    fun addEnded() {
        minPages = 1
    }

    /** The popup closed; the Resize Frame stays open. */
    fun closePopup() {
        mode = mode.closePopup()
    }

    /** The widget whose Resize Frame is open, its popup open or not; null when no frame is open. */
    val framed: Int?
        get() =
            when (val mode = mode) {
                is EditMode.Selected -> mode.widgetId
                is EditMode.Resizing -> mode.resize.widgetId
                else -> null
            }

    /** The resize under way, for the Home Page to draw its preview. */
    val resize get() = (mode as? EditMode.Resizing)?.resize

    /** The open Resize Frame's bounds in window px: the widget's, or following the dragged handle. */
    val frame: Rect?
        get() {
            val zones = zones ?: return null
            val (zone, bounds) =
                when (val mode = mode) {
                    is EditMode.Selected ->
                        shown(pageOf(mode.widgetId)).let { layout -> layout.widget(mode.widgetId)?.let { it.zone to layout.boundsOf(it) } }
                    is EditMode.Resizing -> mode.resize.let { it.base.widget(it.widgetId)!!.zone to it.frame }
                    else -> null
                } ?: return null
            val left = pageOrigin.x + bounds.left
            val top = pageOrigin.y + zones.topPx(zone) + bounds.top
            return Rect(left, top, left + bounds.width, top + bounds.height)
        }

    /** The edges of the open Resize Frame that get a handle. */
    val frameEdges: Set<ResizeEdge>
        get() =
            when (val mode = mode) {
                is EditMode.Selected -> shown(pageOf(mode.widgetId)).resizeEdges(mode.widgetId)
                is EditMode.Resizing -> mode.resize.base.resizeEdges(mode.resize.widgetId)
                else -> emptySet()
            }

    /**
     * A finger went down at window point [at]; outside the open frame, grown by [margin] (the
     * handles' reach), it closes the frame. True when it did, so the touch goes nowhere else.
     */
    fun touchDown(
        at: Offset,
        margin: Float,
    ): Boolean {
        if (mode !is EditMode.Selected) return false
        val frame = frame ?: return false
        if (frame.inflate(margin).contains(at)) return false
        leave()
        return true
    }

    /** A finger went down on the open frame's [edge] handle: the popup closes and a resize starts. */
    fun startResize(edge: ResizeEdge) {
        val id = framed ?: return
        mode = mode.startResize(shown(pageOf(id)), edge)
    }

    /** The handle is [deltaPx] from where it went down; true when the widget's span just changed (a tick). */
    fun resizeBy(deltaPx: Float): Boolean {
        mode = mode.resizeBy(deltaPx)
        return (mode as? EditMode.Resizing)?.resize?.ticked == true
    }

    /** The handle was let go: the new size is committed and the frame snaps to the widget. */
    fun endResize() {
        if (mode !is EditMode.Resizing) return
        val page = pageOf((mode as EditMode.Resizing).resize.widgetId)
        val release = mode.release()
        mode = release.mode
        commit(page, release.result)
    }

    /** "Remove" in the popup or on the drop-target bar: the widget disappears, with an undo. */
    fun remove(id: Int) {
        mode = mode.leave()
        val widget = shown(pageOf(id)).widget(id) ?: return
        val step = removal.remove(widget, clock())
        removal = step.removal
        discard(step.delete)
    }

    /** "Widget settings" in the popup or on the drop-target bar. */
    fun openSettings(id: Int) {
        mode = mode.leave()
        actions.reconfigure(id)
    }

    fun isReconfigurable(id: Int): Boolean = actions.isReconfigurable(id)

    /** Undo on the snackbar: the removed widget comes back. */
    fun undoRemoval() {
        removal = removal.undo().removal
    }

    /** The undo window may have run out. */
    fun expireRemoval() {
        val step = removal.expire(clock())
        removal = step.removal
        discard(step.delete)
    }

    /** Time passes during a drag: the 650 ms reorder rest, and the dwell at a Page edge. */
    fun tick() {
        val now = clock()
        mode = mode.tick(now)
        val drag = (mode as? EditMode.Dragging)?.drag ?: return
        val canAppend = shown(page).widgets.any { it.id != drag.dragged.id }
        val turn = dwell.turn(now, page, layouts.pages.size, canAppend) ?: return
        page = turn.page
        dwell = turn.dwell
        if (page >= layouts.pages.size) minPages = page + 1
        // The drag goes on over the new Page, from where the finger is.
        mode = mode.startDrag(WidgetDrag.start(shown(page), drag.dragged, now))
        lastFinger?.let(::dragTo)
    }

    /**
     * A new widget with [bounds] is dragged onto Home from outside (a Widget Picker preview; #62:
     * the pin sheet). It takes the move path from here, held at its centre; the system draws its
     * picture.
     */
    fun startNewDrag(bounds: SpanBounds) {
        if (mode is EditMode.Dragging) return
        commitRemoval()
        startPages()
        mode = mode.startDrag(WidgetDrag.start(shown(page), DraggedWidget.new(bounds), clock()))
        draggedIsReconfigurable = false
        val size = zones?.grid(WidgetZoneId.Page)?.bounds(CellRect(0, 0, bounds.default.width, bounds.default.height))
        grab = size?.let { Offset(it.width / 2, it.height / 2) } ?: Offset.Zero
    }

    /** The new widget's drag is at [finger] in the window, or (null) has left Home. */
    fun dragNewTo(finger: Offset?) {
        if (finger == null) {
            mode = mode.dragTo(null, clock())
        } else {
            dragTo(finger)
        }
    }

    /**
     * The new widget is dropped: where it lands, to be bound and configured there ([WidgetDrop]),
     * or null when it sprang back (a rejection is reported) or was dropped off the Widget Zones.
     */
    fun dropNew(): WidgetDrop? {
        if ((mode as? EditMode.Dragging)?.drag?.dragged?.isNew != true) return null
        val release = mode.release()
        mode = release.mode
        endDrag()
        return when (val result = release.result) {
            // A Page the drag made stays until the add is over; Pages made past it go.
            is LayoutResult.Changed -> WidgetDrop.of(page, result).also { minPages = page + 1 }
            is LayoutResult.Rejected ->
                null.also {
                    minPages = 1
                    actions.rejected(result.reason)
                }
            LayoutResult.Unchanged -> null.also { minPages = 1 }
        }
    }

    /** The new widget's drag ended without a drop on Home (cancelled, or dropped elsewhere): nothing is left behind. */
    fun endNewDrag() {
        if ((mode as? EditMode.Dragging)?.drag?.dragged?.isNew == true) leave()
    }

    private fun commitRemoval() {
        // Another edit starts: a removal still waiting for its undo is final now (its cells may be taken).
        val step = removal.commit()
        removal = step.removal
        discard(step.delete)
    }

    private fun discard(widgets: List<LaidOutWidget>) {
        if (widgets.isEmpty()) return
        discarded = discarded + widgets.map { it.id }
        widgets.forEach { actions.discard(it.id) }
    }

    private fun endDrag() {
        dragImage = null
        hovered = null
        dwell = EdgeDwell.None
        lastFinger = null
    }

    /** An edit starts: the Pages are as stored, with no Page made by an earlier drag. */
    private fun startPages() {
        minPages = 1
        dwell = EdgeDwell.None
    }

    private fun dragTo(finger: Offset) {
        val drag = (mode as? EditMode.Dragging)?.drag ?: return
        lastFinger = finger
        dragImage = dragImage?.copy(finger = finger)
        // A new widget has no drop-target bar: it is not placed yet.
        hovered = if (drag.dragged.isNew) null else dropTargets.entries.firstOrNull { it.value.contains(finger) }?.key
        val zones = zones ?: return
        val onPage = finger - pageOrigin
        val topLeft = onPage - grab
        val now = clock()
        dwell = dwell.at(if (hovered != null) null else PageEdge.at(onPage.x, pageWidth, edgePx), now)
        val target = if (hovered != null) null else zones.dragTarget(onPage.x, onPage.y, topLeft.x, topLeft.y, drag.dragged.span, page)
        mode = mode.dragTo(target, now)
    }

    private inner class Touches(
        private val id: Int,
    ) : WidgetTouchListener {
        override fun onLongPress(frame: View) {
            if (mode is EditMode.Dragging) return
            commitRemoval()
            startPages()
            mode = mode.longPress(id)
        }

        override fun onDragStart(
            frame: View,
            image: Bitmap,
            fingerX: Float,
            fingerY: Float,
        ) {
            dragOrigin = pageOf(id)
            page = dragOrigin
            mode = mode.startMove(shown(page), clock())
            if (mode !is EditMode.Dragging) return
            draggedIsReconfigurable = actions.isReconfigurable(id)
            dropTargets.clear()
            val bounds = frame.windowBounds()
            val finger = Offset(fingerX, fingerY)
            grab = finger - bounds.topLeft
            dragImage = DragImage(image.asImageBitmap(), grab, finger)
            dragTo(finger)
        }

        override fun onDrag(
            fingerX: Float,
            fingerY: Float,
        ) = dragTo(Offset(fingerX, fingerY))

        override fun onRelease(
            fingerX: Float,
            fingerY: Float,
        ) {
            if (mode !is EditMode.Dragging) return
            dragTo(Offset(fingerX, fingerY))
            when (hovered) {
                DropAction.Remove -> remove(id)
                DropAction.WidgetSettings -> openSettings(id)
                null -> drop()
            }
            endDrag()
        }

        override fun onCancel() = leave()

        private fun drop() {
            val release = mode.release()
            mode = release.mode
            if (release.result is LayoutResult.Changed) {
                // A Page the drag made and dropped on stays until the rows hold it; Pages made past it go.
                minPages = page + 1
            } else {
                // Sprung back: the widget's own Page shows again.
                page = dragOrigin
                minPages = 1
            }
            commit(page, release.result, from = dragOrigin.takeIf { it != page })
        }
    }

    /**
     * Shows and saves a committed drop or resize on [page]; reports a refused drop. A widget
     * dropped here [from] another Page leaves that one at once too.
     */
    private fun commit(
        page: Int,
        result: LayoutResult,
        from: Int? = null,
    ) {
        when (result) {
            is LayoutResult.Changed -> {
                val left =
                    from?.let { origin ->
                        val before = layoutOf(origin)
                        (before.remove(result.widget.id) as? LayoutResult.Changed)?.let { origin to (before to it.layout) }
                    }
                committed = mapOf(page to (layoutOf(page) to result.layout)) + listOfNotNull(left)
                actions.save(page, result.layout)
            }
            is LayoutResult.Rejected -> actions.rejected(result.reason)
            LayoutResult.Unchanged -> Unit
        }
    }
}

private fun View.windowBounds(): Rect {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Rect(location[0].toFloat(), location[1].toFloat(), (location[0] + width).toFloat(), (location[1] + height).toFloat())
}
