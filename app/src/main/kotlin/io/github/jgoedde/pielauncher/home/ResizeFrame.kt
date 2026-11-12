package io.github.jgoedde.pielauncher.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.home.ResizeEdge
import kotlin.math.roundToInt

/** Launcher3 `AppWidgetResizeFrame.SNAP_DURATION_MS`: the frame snaps to the widget's new bounds on release. */
private const val SNAP_MS = 150

/** Launcher3 `resize_frame_touch_target_size`. */
private val HandleTouchTarget = 48.dp

private val HandleDot = 12.dp

private val FrameStroke = 2.dp

/**
 * The Resize Frame of the widget in Edit Mode (PRD 7.2, Launcher3 `AppWidgetResizeFrame`), in
 * window px: a rounded outline in the accent colour with the system widget corner radius, and a
 * dot handle at the middle of each edge the widget can resize on. Dragging a handle resizes the
 * widget cell by cell with a `SEGMENT_TICK`; the other handles fade out meanwhile, and on release
 * the frame snaps to the widget's new bounds. The inside of the frame takes no touches: they
 * still reach the widget.
 */
@Composable
internal fun ResizeFrame(editor: WidgetEditor) {
    val framed = editor.framed ?: return
    val target = editor.frame ?: return
    key(framed) {
        val resize = editor.resize
        val rect by animateRectAsState(target, if (resize != null) snap() else tween(SNAP_MS), label = "resizeFrame")
        val density = LocalDensity.current
        val accent = MaterialTheme.colorScheme.primary
        val radius = dimensionResource(android.R.dimen.system_app_widget_background_radius)
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(rect.width.toDp(), rect.height.toDp()),
            ) {
                Box(Modifier.matchParentSize().border(FrameStroke, accent, RoundedCornerShape(radius)))
                editor.frameEdges.forEach { edge ->
                    key(edge) {
                        val alpha by animateFloatAsState(if (resize != null && resize.edge != edge) 0f else 1f, label = "handle")
                        Handle(editor, edge, accent, Modifier.graphicsLayer { this.alpha = alpha })
                    }
                }
            }
        }
    }
}

/** A dot at the middle of [edge] with a 48 dp touch target centred on the frame's line. */
@Composable
private fun BoxScope.Handle(
    editor: WidgetEditor,
    edge: ResizeEdge,
    accent: Color,
    modifier: Modifier,
) {
    val view = LocalView.current
    val half = HandleTouchTarget / 2
    val (alignment, offset) =
        when (edge) {
            ResizeEdge.Left -> Alignment.CenterStart to Modifier.offset(x = -half)
            ResizeEdge.Top -> Alignment.TopCenter to Modifier.offset(y = -half)
            ResizeEdge.Right -> Alignment.CenterEnd to Modifier.offset(x = half)
            ResizeEdge.Bottom -> Alignment.BottomCenter to Modifier.offset(y = half)
        }
    // The handle moves with the frame while dragged, so the finger is tracked in window px.
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier
            .align(alignment)
            .then(offset)
            .size(HandleTouchTarget)
            .onPlaced { placed[0] = it }
            .pointerInput(editor, edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val coordinates = placed[0] ?: return@awaitEachGesture
                    editor.startResize(edge)
                    if (editor.resize == null) return@awaitEachGesture
                    down.consume()
                    val start = coordinates.localToWindow(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val delta = coordinates.localToWindow(change.position) - start
                        val ticked = editor.resizeBy(if (edge.horizontal) delta.x else delta.y)
                        if (ticked) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                    }
                    editor.endResize()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(HandleDot).background(accent, CircleShape).border(1.dp, Color.White, CircleShape))
    }
}

/**
 * Watches every touch-down on the Home screen before anything handles it: one outside the open
 * Resize Frame (and its handles' reach) closes the frame and commits, and is consumed, so the
 * tap does nothing else (Launcher3 `AbstractFloatingView`).
 */
internal fun Modifier.closesResizeFrameOnTapOutside(editor: WidgetEditor): Modifier {
    val placed = arrayOfNulls<LayoutCoordinates>(1)
    return onPlaced { placed[0] = it }.pointerInput(editor) {
        val reach = HandleTouchTarget.toPx() / 2
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val at = placed[0]?.localToWindow(down.position) ?: Offset(down.position.x, down.position.y)
            if (editor.touchDown(at, reach)) down.consume()
        }
    }
}
