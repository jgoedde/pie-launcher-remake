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
import io.github.jgoedde.pielauncher.core.home.EditMode
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.ResizeEdge
import io.github.jgoedde.pielauncher.core.home.WidgetDrag
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetRemoval
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
    /** Persist [layout]: a committed move and its reflow. */
    fun save(layout: WidgetLayout)

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
 * long-press wrapper's touch reports into Edit Mode transitions. The Home Page tells it its
 * geometry ([laidOut]) so window points can be mapped to Widget Zone cells.
 *
 * In [EditMode.Selected] and [EditMode.Resizing] the Resize Frame is open ([frame]); its handle
 * drags resize the widget and commit on release. #61 hooks in at [startDrag] (a Widget Picker
 * drag arriving over Home takes the same move path).
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

    /** A committed drop shown until the stored rows catch up: (the layout it was made on, the result). */
    private var committed by mutableStateOf<Pair<WidgetLayout, WidgetLayout>?>(null)

    /** Widgets deleted for good, hidden until their rows are gone. */
    private var discarded by mutableStateOf(emptySet<Int>())

    // State, so the Resize Frame follows the Page's layout and position.
    private var base by mutableStateOf(WidgetLayout(emptyMap(), emptyList()))
    private var zones by mutableStateOf<HomeZones?>(null)
    private var pageOrigin by mutableStateOf(Offset.Zero)

    /** Whether the drag in progress is of a widget that offers "Widget settings". */
    var draggedIsReconfigurable by mutableStateOf(false)
        private set

    /** The Home Page laid out [layout] on [zones], with the top of its upper zone at [origin] in the window. */
    fun laidOut(
        layout: WidgetLayout,
        zones: HomeZones,
        origin: Offset,
    ) {
        base = layout
        this.zones = zones
        pageOrigin = origin
    }

    /** [layout] as Home shows it outside a drag: a just-committed drop, minus removed widgets. */
    fun shown(layout: WidgetLayout): WidgetLayout {
        val justDropped = committed?.takeIf { it.first === layout }?.second ?: layout
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

    /** The touch reports of widget [id]'s long-press wrapper. */
    fun touchesOf(id: Int): WidgetTouchListener = Touches(id)

    /** Back, Home, a tap outside the Resize Frame: Edit Mode ends; a drag under way springs back, a resize commits. */
    fun leave() {
        endResize()
        mode = mode.leave()
        endDrag()
    }

    /** Widget [id] was just added: its Resize Frame opens (Launcher3), once the widget shows. */
    fun added(id: Int) {
        mode = mode.added(id)
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
                        shown(base).let { layout -> layout.widget(mode.widgetId)?.let { it.zone to layout.boundsOf(it) } }
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
                is EditMode.Selected -> shown(base).resizeEdges(mode.widgetId)
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
        mode = mode.startResize(shown(base), edge)
    }

    /** The handle is [deltaPx] from where it went down; true when the widget's span just changed (a tick). */
    fun resizeBy(deltaPx: Float): Boolean {
        mode = mode.resizeBy(deltaPx)
        return (mode as? EditMode.Resizing)?.resize?.ticked == true
    }

    /** The handle was let go: the new size is committed and the frame snaps to the widget. */
    fun endResize() {
        if (mode !is EditMode.Resizing) return
        val release = mode.release()
        mode = release.mode
        commit(release.result)
    }

    /** "Remove" in the popup or on the drop-target bar: the widget disappears, with an undo. */
    fun remove(id: Int) {
        mode = mode.leave()
        val widget = shown(base).widget(id) ?: return
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

    /** Time passes during a drag: the 650 ms reorder rest. */
    fun tick() {
        mode = mode.tick(clock())
    }

    /** #61: a drag that started outside Home (a Widget Picker preview) takes the same path from here. */
    fun startDrag(drag: WidgetDrag) {
        mode = mode.startDrag(drag)
    }

    private fun discard(widgets: List<LaidOutWidget>) {
        if (widgets.isEmpty()) return
        discarded = discarded + widgets.map { it.id }
        widgets.forEach { actions.discard(it.id) }
    }

    private fun endDrag() {
        dragImage = null
        hovered = null
    }

    private fun dragTo(finger: Offset) {
        val drag = (mode as? EditMode.Dragging)?.drag ?: return
        val image = dragImage ?: return
        dragImage = image.copy(finger = finger)
        hovered = dropTargets.entries.firstOrNull { it.value.contains(finger) }?.key
        val zones = zones ?: return
        val onPage = finger - pageOrigin
        val topLeft = onPage - image.grab
        val target = if (hovered != null) null else zones.dragTarget(onPage.x, onPage.y, topLeft.x, topLeft.y, drag.dragged.span)
        mode = mode.dragTo(target, clock())
    }

    private inner class Touches(
        private val id: Int,
    ) : WidgetTouchListener {
        override fun onLongPress(frame: View) {
            if (mode is EditMode.Dragging) return
            // Another edit starts: a removal still waiting for its undo is final now (its cells may be taken).
            val step = removal.commit()
            removal = step.removal
            discard(step.delete)
            mode = mode.longPress(id)
        }

        override fun onDragStart(
            frame: View,
            image: Bitmap,
            fingerX: Float,
            fingerY: Float,
        ) {
            mode = mode.startMove(shown(base), clock())
            if (mode !is EditMode.Dragging) return
            draggedIsReconfigurable = actions.isReconfigurable(id)
            dropTargets.clear()
            val bounds = frame.windowBounds()
            val finger = Offset(fingerX, fingerY)
            dragImage = DragImage(image.asImageBitmap(), finger - bounds.topLeft, finger)
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
            commit(release.result)
        }
    }

    /** Shows and saves a committed drop or resize; reports a refused drop. */
    private fun commit(result: LayoutResult) {
        when (result) {
            is LayoutResult.Changed -> {
                committed = base to result.layout
                actions.save(result.layout)
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
