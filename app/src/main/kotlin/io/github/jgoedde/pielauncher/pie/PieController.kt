package io.github.jgoedde.pielauncher.pie

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import io.github.jgoedde.pielauncher.core.config.PieItem
import io.github.jgoedde.pielauncher.core.pie.Bounds
import io.github.jgoedde.pielauncher.core.pie.PieEvent
import io.github.jgoedde.pielauncher.core.pie.PieGesture
import io.github.jgoedde.pielauncher.core.pie.PieMetrics
import io.github.jgoedde.pielauncher.core.pie.PieRelease
import io.github.jgoedde.pielauncher.core.pie.PieState
import io.github.jgoedde.pielauncher.core.pie.Point

/**
 * Holds the `:core` [PieGesture] for the Pie Zone's pointer handler and mirrors its state into
 * Compose for the overlay. All positions are window px.
 */
@Stable
class PieController(
    val metrics: PieMetrics,
) {
    /** The resolved Pie Config; the Pie does not open while it is null. */
    var pie: ResolvedPie? by mutableStateOf(null)

    /** The window minus system-bar insets; the ring is clamped inside it. */
    var window: Bounds by mutableStateOf(Bounds(0f, 0f, 0f, 0f))

    private val gesture = PieGesture(metrics, isAvailable = { pie?.isAvailable(it) == true })

    var state: PieState by mutableStateOf(PieState.Closed)
        private set

    /** Returns false when there is no Pie Config to open. */
    fun down(finger: Point): Boolean {
        val config = pie?.config ?: return false
        gesture.down(finger, window, config)
        state = gesture.state
        return true
    }

    fun move(finger: Point): PieEvent? = gesture.move(finger).also { state = gesture.state }

    fun up(): PieRelease = gesture.up().also { state = gesture.state }

    fun cancel() {
        gesture.cancel()
        state = gesture.state
    }

    fun resolve(item: PieItem): ResolvedItem = pie?.resolve(item) ?: ResolvedItem.Unavailable
}

@Composable
fun rememberPieController(): PieController {
    val density = LocalDensity.current.density
    return remember(density) { PieController(PieMetrics.scaled(density)) }
}
