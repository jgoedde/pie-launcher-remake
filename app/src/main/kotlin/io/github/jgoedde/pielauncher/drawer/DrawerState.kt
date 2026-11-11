package io.github.jgoedde.pielauncher.drawer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far the App Drawer is open, from 0 (closed) to 1 (fully open).
 *
 * A drag moves it with the finger; on release it settles open or closed depending on the fling
 * direction, or on how far it got for a slow release. Pulling back therefore cancels.
 */
@Stable
class DrawerState internal constructor(
    private val scope: CoroutineScope,
) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** The distance in px a drag must travel to open the Drawer fully: its height. */
    var travelPx = 1f

    val isClosed: Boolean get() = progress == 0f && animation?.isActive != true

    private var animation: Job? = null

    /** [deltaY] in px, positive downwards (as Compose reports it). */
    fun dragBy(deltaY: Float) {
        animation?.cancel()
        progress = (progress - deltaY / travelPx).coerceIn(0f, 1f)
    }

    /** [velocityY] in px/s, positive downwards. */
    fun settle(velocityY: Float) {
        val open =
            when {
                velocityY < -FLING_VELOCITY -> true
                velocityY > FLING_VELOCITY -> false
                else -> progress > 0.5f
            }
        animateTo(if (open) 1f else 0f, initialVelocity = -velocityY / travelPx)
    }

    fun open() = animateTo(1f)

    fun close() = animateTo(0f)

    private fun animateTo(
        target: Float,
        initialVelocity: Float = 0f,
    ) {
        animation?.cancel()
        animation =
            scope.launch {
                animate(progress, target, initialVelocity, spring(stiffness = STIFFNESS)) { value, _ ->
                    progress = value.coerceIn(0f, 1f)
                }
            }
    }

    private companion object {
        /** px/s; above this the fling direction decides, below it the distance does. */
        const val FLING_VELOCITY = 1_000f
        const val STIFFNESS = 600f
    }
}

@Composable
fun rememberDrawerState(): DrawerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DrawerState(scope) }
}
