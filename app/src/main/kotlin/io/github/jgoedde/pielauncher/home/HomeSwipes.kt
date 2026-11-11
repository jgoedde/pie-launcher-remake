package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import io.github.jgoedde.pielauncher.core.home.ShadeSwipe
import io.github.jgoedde.pielauncher.drawer.DrawerState

/**
 * Vertical swipes in a Widget Zone. Once a drag crosses touch slop it is
 * either a swipe down that opens the notification shade (see [ShadeSwipe]; the rest of the gesture
 * is swallowed) or it moves the App Drawer with the finger and settles it on release.
 */
@Composable
fun Modifier.homeSwipes(
    drawer: DrawerState,
    onSwipeDown: () -> Unit,
): Modifier {
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    return pointerInput(drawer) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var overSlop = 0f
            val slopChange =
                awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume()
                    overSlop = over
                } ?: return@awaitEachGesture
            val travel = slopChange.position - down.position
            if (!drawer.isShown && ShadeSwipe.opensShade(travel.x, travel.y, viewConfiguration.touchSlop)) {
                currentOnSwipeDown()
                verticalDrag(slopChange.id) { it.consume() }
                return@awaitEachGesture
            }
            val velocity = VelocityTracker()
            velocity.addPointerInputChange(down)
            velocity.addPointerInputChange(slopChange)
            drawer.dragBy(overSlop)
            val completed =
                verticalDrag(slopChange.id) { change ->
                    velocity.addPointerInputChange(change)
                    drawer.dragBy(change.position.y - change.previousPosition.y)
                    change.consume()
                }
            drawer.settle(if (completed) velocity.calculateVelocity().y else 0f)
        }
    }
}
