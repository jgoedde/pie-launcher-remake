package io.github.jgoedde.pielauncher.pie

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.jgoedde.pielauncher.core.pie.PieEvent
import io.github.jgoedde.pielauncher.core.pie.PieState
import io.github.jgoedde.pielauncher.core.pie.Point
import kotlinx.coroutines.flow.collectLatest

/**
 * The Pie Zone's pointer handler (PRD 4.1, 4.7). A touch-down opens the Pie at the finger and
 * consumes the whole gesture, so it never changes the Page or opens the Drawer or shade. While the
 * Pie is open the Hover Delay is checked every frame, since a still finger sends no moves.
 *
 * Haptics: a click on each hover enter (Pie Item or Shortcut row), a heavy click on a Layer
 * Switch and on a Shortcut list opening, and a confirm when [onLaunch] started the release target.
 */
@Composable
fun Modifier.pieTouches(
    controller: PieController,
    onLaunch: (LaunchTarget) -> Boolean,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    LaunchedEffect(controller, haptics) {
        snapshotFlow { controller.state is PieState.Open }.collectLatest { open ->
            while (open) {
                withFrameMillis { }
                haptics.react(controller.tick())
            }
        }
    }
    // Where this element sits in the window; the gesture works in window px.
    val origin = remember { arrayOf(Offset.Zero) }
    return onGloballyPositioned { origin[0] = it.positionInWindow() }
        .pointerInput(controller) {
            fun Offset.inWindow() = (this + origin[0]).let { Point(it.x, it.y) }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                if (!controller.down(down.position.inWindow())) return@awaitEachGesture
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) break
                        haptics.react(controller.move(change.position.inWindow()))
                    }
                    val target = controller.up()
                    if (target != null && currentOnLaunch(target)) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                } finally {
                    // Cancelled mid-drag (e.g. the window lost focus): close without launching.
                    controller.cancel()
                }
            }
        }
}

private fun HapticFeedback.react(event: PieEvent?) {
    val type =
        when (event) {
            null -> return
            is PieEvent.HoverEntered, is PieEvent.ShortcutHovered -> HapticFeedbackType.VirtualKey
            PieEvent.LayerSwitched, is PieEvent.ShortcutsOpened -> HapticFeedbackType.LongPress
        }
    performHapticFeedback(type)
}
