package io.github.jgoedde.pielauncher.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Picture
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.children
import kotlin.math.hypot

/** What a long-press on a widget leads to, in window px (PRD 5.1, 7.2). */
interface WidgetTouchListener {
    /** The long-press fired; the widget no longer sees this touch. [frame] is the widget's view, for its bounds. */
    fun onLongPress(frame: View)

    /** The finger moved past touch slop after the long-press: a move starts, the widget drawn into [image]. */
    fun onDragStart(
        frame: View,
        image: Bitmap,
        fingerX: Float,
        fingerY: Float,
    )

    fun onDrag(
        fingerX: Float,
        fingerY: Float,
    )

    /** The finger lifted: a drop when a move had started, else the end of the long-press. */
    fun onRelease(
        fingerX: Float,
        fingerY: Float,
    )

    /** The system took the touch away (another window, a lost focus): the move springs back. */
    fun onCancel()
}

/**
 * The long-press wrapper around a hosted widget (PRD 5.1, Launcher3 `CheckLongPressHelper`): a
 * timer started on touch-down fires after 0.75 × the system long-press timeout unless the finger
 * moved past touch slop or lifted. When it fires it gives a `LONG_PRESS` haptic, takes the touch
 * away from the widget (and from the home gestures) and reports the rest of the gesture to
 * [listener]: a move past slop is a drag. On a scrollable widget the touch is claimed on
 * touch-down, so its list scrolls instead of opening the App Drawer or the shade; a widget that
 * doesn't scroll leaves vertical swipes to Home. The scroll claim is reverted when a drag starts
 * (Launcher3 `beforeDragStart`): from then on only the launcher's own claim, taken at the
 * long-press, keeps Home's swipes out of the drag. Every claim ends with the gesture.
 */
internal class WidgetFrame(
    context: Context,
) : FrameLayout(context) {
    var listener: WidgetTouchListener? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val timeout = (ViewConfiguration.getLongPressTimeout() * LONG_PRESS_TIMEOUT_FACTOR).toLong()
    private var state = State.Idle
    private var scrollClaimed = false
    private var downX = 0f
    private var downY = 0f
    private val location = IntArray(2)

    private enum class State { Idle, Pressed, LongPressed, Dragging }

    private val longPress =
        Runnable {
            if (state != State.Pressed) return@Runnable
            state = State.LongPressed
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            listener?.onLongPress(this)
        }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state = State.Pressed
                downX = ev.x
                downY = ev.y
                postDelayed(longPress, timeout)
                scrollClaimed = isScrollable(this)
                if (scrollClaimed) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE ->
                if (state == State.Pressed && beyondSlop(ev)) cancelLongPress()
            MotionEvent.ACTION_POINTER_DOWN ->
                if (state == State.Pressed) cancelLongPress()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == State.Pressed) cancelLongPress()
                scrollClaimed = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Once the long-press fired, the gesture is the launcher's: the widget gets a cancel. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = state == State.LongPressed || state == State.Dragging

    // Never a click: taps belong to the widget; this frame only sees a touch after its long-press (TalkBack: PRD 17).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val listener = listener
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE ->
                when (state) {
                    State.LongPressed ->
                        if (beyondSlop(ev) && listener != null) {
                            state = State.Dragging
                            releaseScrollClaim()
                            listener.onDragStart(this, snapshot(), windowX(ev), windowY(ev))
                        }
                    State.Dragging -> listener?.onDrag(windowX(ev), windowY(ev))
                    else -> Unit
                }
            MotionEvent.ACTION_UP -> {
                if (state == State.LongPressed || state == State.Dragging) listener?.onRelease(windowX(ev), windowY(ev))
                state = State.Idle
            }
            MotionEvent.ACTION_CANCEL -> {
                if (state == State.Dragging) listener?.onCancel()
                state = State.Idle
            }
        }
        return true
    }

    /**
     * Passes a child's request on to the home surface, but never blocks this frame's own
     * intercept: the long-press must still be able to take the touch from a widget's list.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onDetachedFromWindow() {
        cancelLongPress()
        super.onDetachedFromWindow()
    }

    override fun cancelLongPress() {
        removeCallbacks(longPress)
        if (state == State.Pressed) state = State.Idle
        super.cancelLongPress()
    }

    /** Drops the widget's scroll claim; the launcher's claim from the long-press stays for the drag. */
    private fun releaseScrollClaim() {
        if (!scrollClaimed) return
        scrollClaimed = false
        parent?.requestDisallowInterceptTouchEvent(state == State.LongPressed || state == State.Dragging)
    }

    private fun beyondSlop(ev: MotionEvent) = hypot(ev.x - downX, ev.y - downY) > slop

    private fun windowX(ev: MotionEvent): Float {
        getLocationInWindow(location)
        return location[0] + ev.x
    }

    private fun windowY(ev: MotionEvent): Float {
        getLocationInWindow(location)
        return location[1] + ev.y
    }

    /** The widget as drawn now, as a hardware bitmap (widgets may draw hardware bitmaps, which a software canvas can't). */
    private fun snapshot(): Bitmap {
        val picture = Picture()
        val canvas = picture.beginRecording(width.coerceAtLeast(1), height.coerceAtLeast(1))
        try {
            draw(canvas)
        } finally {
            picture.endRecording()
        }
        return Bitmap.createBitmap(picture)
    }

    private companion object {
        /** Launcher3 `CheckLongPressHelper.DEFAULT_LONG_PRESS_TIMEOUT_FACTOR`. */
        const val LONG_PRESS_TIMEOUT_FACTOR = 0.75f

        /** Launcher3 `checkScrollableRecursively`: a list or scroller anywhere in the widget. */
        fun isScrollable(view: View): Boolean =
            view is AdapterView<*> || view is ScrollView || (view is ViewGroup && view.children.any(::isScrollable))
    }
}
