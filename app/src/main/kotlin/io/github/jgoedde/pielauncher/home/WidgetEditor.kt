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
 * #60 hooks in at [EditMode.Selected] (draw the Resize Frame next to the popup) and #61 at
 * [startDrag] (a Widget Picker drag arriving over Home takes the same move path).
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

    private var base = WidgetLayout(emptyMap(), emptyList())
    private var zones: HomeZones? = null
    private var pageOrigin = Offset.Zero

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

    /** Back, Home, a tap outside the popup: Edit Mode ends; a drag under way springs back. */
    fun leave() {
        mode = mode.leave()
        endDrag()
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
            when (val result = release.result) {
                is LayoutResult.Changed -> {
                    committed = base to result.layout
                    actions.save(result.layout)
                }
                is LayoutResult.Rejected -> actions.rejected(result.reason)
                LayoutResult.Unchanged -> Unit
            }
        }
    }
}

private fun View.windowBounds(): Rect {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Rect(location[0].toFloat(), location[1].toFloat(), (location[0] + width).toFloat(), (location[1] + height).toFloat())
}
