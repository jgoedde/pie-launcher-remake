package io.github.jgoedde.pielauncher.pie

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.jgoedde.pielauncher.core.pie.PieEvent
import io.github.jgoedde.pielauncher.core.pie.PieRelease
import io.github.jgoedde.pielauncher.core.pie.Point

/**
 * The Pie Zone's pointer handler (PRD 4.1). A touch-down opens the Pie at the finger and consumes
 * the whole gesture, so it never changes the Page or opens the Drawer or shade. A light tick marks
 * each hover enter; a release on an available app item calls [onLaunch] with its target and gets
 * a stronger tick when it started.
 */
@Composable
fun Modifier.pieTouches(
    controller: PieController,
    onLaunch: (ResolvedItem.App) -> Boolean,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentOnLaunch by rememberUpdatedState(onLaunch)
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
                        if (controller.move(change.position.inWindow()) is PieEvent.HoverEntered) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                    val release = controller.up()
                    if (release is PieRelease.Launch) {
                        val target = controller.resolve(release.item)
                        if (target is ResolvedItem.App && currentOnLaunch(target)) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    }
                } finally {
                    // Cancelled mid-drag (e.g. the window lost focus): close without launching.
                    controller.cancel()
                }
            }
        }
}
