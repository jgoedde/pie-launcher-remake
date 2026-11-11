package io.github.jgoedde.pielauncher.core.pie

import io.github.jgoedde.pielauncher.core.config.Layer
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieItem

sealed interface PieState {
    data object Closed : PieState

    /** [hovered] is the index of the hovered Pie Item in [layer], null for none. */
    data class Open(
        val layer: Layer,
        val centre: Point,
        val finger: Point,
        val hovered: Int?,
    ) : PieState
}

/** Something the UI reacts to during the drag (haptics). */
sealed interface PieEvent {
    data class HoverEntered(
        val index: Int,
    ) : PieEvent
}

/** What a release does. */
sealed interface PieRelease {
    /** Start [item]: an app item or a Shortcut item whose target is available. */
    data class Launch(
        val item: PieItem,
    ) : PieRelease

    /** Nothing happens. */
    data object Cancel : PieRelease
}

/**
 * The Pie gesture (PRD 4.1, 4.3) at touch-event rate: touch-down opens the Pie on the Base Layer,
 * drags hover Pie Items by the band model, release launches or cancels and always closes the Pie.
 * Held by the Compose pointer handler, not a ViewModel (ADR 0001). [isAvailable] says whether an
 * item's target is on the device; releasing on an Unavailable Item does nothing.
 */
class PieGesture(
    private val metrics: PieMetrics,
    private val isAvailable: (PieItem) -> Boolean,
) {
    var state: PieState = PieState.Closed
        private set

    fun down(
        finger: Point,
        window: Bounds,
        config: PieConfig,
    ) {
        state = PieState.Open(config.base, PieGeometry.clampCentre(finger, window, metrics), finger, hovered = null)
    }

    fun move(finger: Point): PieEvent? {
        val open = state as? PieState.Open ?: return null
        val hit = PieGeometry.hitTest(open.centre, finger, open.layer.items.size, open.hovered, metrics)
        val hovered = (hit as? PieHit.Item)?.index
        state = open.copy(finger = finger, hovered = hovered)
        return if (hovered != null && hovered != open.hovered) PieEvent.HoverEntered(hovered) else null
    }

    fun up(): PieRelease {
        val open = state as? PieState.Open
        state = PieState.Closed
        val item = open?.hovered?.let(open.layer.items::get) ?: return PieRelease.Cancel
        return when (item) {
            is PieItem.App, is PieItem.Shortcut -> if (isAvailable(item)) PieRelease.Launch(item) else PieRelease.Cancel
            is PieItem.LayerSwitch -> PieRelease.Cancel
        }
    }

    fun cancel() {
        state = PieState.Closed
    }
}
