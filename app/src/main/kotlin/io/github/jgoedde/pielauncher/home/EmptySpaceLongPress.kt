package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A long-press on empty Widget Zone space (PRD 7.2): the finger rests within touch slop for the
 * long-press timeout at a point where [isEmpty] holds. It then calls [onLongPress] with that point
 * and swallows the rest of the gesture, so it never also drags the App Drawer. A drag, a lift or a
 * touch already taken by a widget cancels it. Put it after the swipe detectors in the chain so it
 * sees each event first.
 */
@Composable
fun Modifier.emptySpaceLongPress(
    isEmpty: (Offset) -> Boolean,
    onLongPress: (Offset) -> Unit,
): Modifier {
    val currentIsEmpty by rememberUpdatedState(isEmpty)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val held =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                    false
                } ?: true
            if (!held || !currentIsEmpty(down.position)) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongPress(down.position)
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
