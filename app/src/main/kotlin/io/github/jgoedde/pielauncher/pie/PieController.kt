package io.github.jgoedde.pielauncher.pie

import android.os.SystemClock
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
 * Compose for the overlay. All positions are window px; times are [SystemClock.uptimeMillis].
 */
@Stable
class PieController(
    val metrics: PieMetrics,
) {
    /** The resolved Pie Config; the Pie does not open while it is null. */
    var pie: ResolvedPie? by mutableStateOf(null)

    /** The window minus system-bar insets; the ring and Shortcut list are kept inside it. */
    var window: Bounds by mutableStateOf(Bounds(0f, 0f, 0f, 0f))

    private val gesture =
        PieGesture(
            metrics,
            isAvailable = { resolve(it) !is ResolvedItem.Unavailable },
            shortcutCount = { (resolve(it) as? ResolvedItem.App)?.shortcuts?.size ?: 0 },
        )

    var state: PieState by mutableStateOf(PieState.Closed)
        private set

    /** How much of the Hover Delay has elapsed (the Progress Disc); null when nothing is dwelling. */
    var dwellProgress: Float? by mutableStateOf(null)
        private set

    /**
     * The Pie Config the open Pie was opened with; an update while the Pie is open (an app change)
     * waits for the next touch-down, so the Shortcut list the finger is on keeps its rows.
     */
    private var openedWith: ResolvedPie? = null

    /** Returns false when there is no Pie Config to open. */
    fun down(finger: Point): Boolean {
        val resolved = pie ?: return false
        openedWith = resolved
        gesture.down(finger, window, resolved.config)
        sync()
        return true
    }

    fun move(finger: Point): PieEvent? = gesture.move(finger, SystemClock.uptimeMillis()).also { sync() }

    /** Called every frame while the Pie is open. */
    fun tick(): PieEvent? = gesture.tick(SystemClock.uptimeMillis()).also { sync() }

    /** Closes the Pie and says what to start, if anything. */
    fun up(): LaunchTarget? {
        val release = gesture.up()
        sync()
        return when (release) {
            is PieRelease.Launch ->
                when (val target = resolve(release.item)) {
                    is ResolvedItem.App -> LaunchTarget.App(target.app)
                    is ResolvedItem.Shortcut -> LaunchTarget.Shortcut(target.shortcut)
                    else -> null
                }
            is PieRelease.LaunchShortcut ->
                (resolve(release.item) as? ResolvedItem.App)?.shortcuts?.getOrNull(release.row)?.let(LaunchTarget::Shortcut)
            PieRelease.Cancel -> null
        }
    }

    fun cancel() {
        gesture.cancel()
        sync()
    }

    fun resolve(item: PieItem): ResolvedItem = (openedWith ?: pie)?.resolve(item) ?: ResolvedItem.Unavailable

    private fun sync() {
        state = gesture.state
        dwellProgress = gesture.dwellProgress(SystemClock.uptimeMillis())
    }
}

@Composable
fun rememberPieController(): PieController {
    val density = LocalDensity.current.density
    return remember(density) { PieController(PieMetrics.scaled(density)) }
}
