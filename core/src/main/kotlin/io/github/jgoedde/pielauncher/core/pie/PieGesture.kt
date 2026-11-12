package io.github.jgoedde.pielauncher.core.pie

import io.github.jgoedde.pielauncher.core.config.Layer
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieItem

/** The open Shortcut list of the app item at [item]: [rows] Shortcuts on [card], in window px. */
data class ShortcutList(
    val item: Int,
    val rows: Int,
    val card: Bounds,
)

sealed interface PieState {
    data object Closed : PieState

    /**
     * [hovered] is the index of the hovered Pie Item in [layer], null for none. [dwellSince] is
     * when the finger started dwelling on a hovered item that reacts to the Hover Delay, else null.
     * [hoveredRow] is the hovered row of the open [shortcuts] list.
     */
    data class Open(
        val layer: Layer,
        val centre: Point,
        val finger: Point,
        val hovered: Int?,
        val dwellSince: Long? = null,
        val shortcuts: ShortcutList? = null,
        val hoveredRow: Int? = null,
    ) : PieState
}

/** Something the UI reacts to during the drag (haptics). */
sealed interface PieEvent {
    data class HoverEntered(
        val index: Int,
    ) : PieEvent

    data class ShortcutHovered(
        val row: Int,
    ) : PieEvent

    /** A Layer Switch fired: the Pie shows its target Layer, re-centred at the finger. */
    data object LayerSwitched : PieEvent

    /** The Shortcut list of the app item at [index] opened. */
    data class ShortcutsOpened(
        val index: Int,
    ) : PieEvent
}

/** What a release does. */
sealed interface PieRelease {
    /** Start [item]: an app item or a Shortcut item whose target is available. */
    data class Launch(
        val item: PieItem,
    ) : PieRelease

    /** Start the Shortcut at [row] of [item]'s Shortcut list. */
    data class LaunchShortcut(
        val item: PieItem.App,
        val row: Int,
    ) : PieRelease

    /** Nothing happens. */
    data object Cancel : PieRelease
}

/**
 * The Pie gesture (PRD 4.1, 4.3–4.6) at touch-event rate: touch-down opens the Pie on the Base
 * Layer, drags hover Pie Items by the band model, release launches or cancels and always closes
 * the Pie. Dwelling for the Hover Delay on a Layer Switch switches Layer; on an app item with
 * Shortcuts it opens that app's Shortcut list, which is hit-tested before the ring.
 *
 * Held by the Compose pointer handler, not a ViewModel (ADR 0001). [isAvailable] says whether an
 * item's target is on the device; releasing on an Unavailable Item does nothing. [shortcutCount]
 * is how many Shortcuts an app item's list would show. Times are monotonic ms; [tick] runs every
 * frame while the Pie is open, since a still finger sends no moves.
 */
class PieGesture(
    private val metrics: PieMetrics,
    private val isAvailable: (PieItem) -> Boolean,
    private val shortcutCount: (PieItem) -> Int = { 0 },
    private val hoverDelayMillis: Long = HOVER_DELAY_MILLIS,
) {
    var state: PieState = PieState.Closed
        private set

    private var config: PieConfig? = null
    private var window = Bounds(0f, 0f, 0f, 0f)

    fun down(
        finger: Point,
        window: Bounds,
        config: PieConfig,
    ) {
        this.config = config
        this.window = window
        state = PieState.Open(config.base, PieGeometry.clampCentre(finger, window, metrics), finger, hovered = null)
    }

    fun move(
        finger: Point,
        now: Long,
    ): PieEvent? {
        val open = state as? PieState.Open ?: return null
        val list = open.shortcuts
        val row = list?.let { ShortcutCard.rowAt(it.card, it.rows, finger, metrics) }
        if (row != null) {
            state = open.copy(finger = finger, hoveredRow = row)
            return if (row != open.hoveredRow) PieEvent.ShortcutHovered(row) else null
        }
        val hit = PieGeometry.hitTest(open.centre, finger, open.layer.items.size, open.hovered, metrics)
        val hovered = (hit as? PieHit.Item)?.index
        val closes = list != null && (hit == PieHit.Centre || (hovered != null && hovered != list.item))
        val shortcuts = list.takeUnless { closes }
        val entered = hovered != open.hovered
        val dwellSince =
            when {
                !entered -> open.dwellSince
                hovered == null || hovered == shortcuts?.item || !dwells(open.layer.items[hovered]) -> null
                else -> now
            }
        state = open.copy(finger = finger, hovered = hovered, dwellSince = dwellSince, shortcuts = shortcuts, hoveredRow = null)
        return if (hovered != null && entered) PieEvent.HoverEntered(hovered) else null
    }

    /** Fires what the Hover Delay gates once it has elapsed. */
    fun tick(now: Long): PieEvent? {
        val open = state as? PieState.Open ?: return null
        val since = open.dwellSince ?: return null
        val index = open.hovered ?: return null
        if (now - since < hoverDelayMillis) return null
        return when (val item = open.layer.items[index]) {
            is PieItem.LayerSwitch -> {
                val target = config?.layer(item.layer) ?: return null
                // The finger starts in the centre of the new Layer with nothing selected.
                state = PieState.Open(target, PieGeometry.clampCentre(open.finger, window, metrics), open.finger, hovered = null)
                PieEvent.LayerSwitched
            }
            is PieItem.App -> {
                val rows = shortcutCount(item)
                val position = PieGeometry.itemPosition(open.centre, index, open.layer.items.size, metrics)
                val card = ShortcutCard.place(position, open.centre, rows, window, metrics)
                state = open.copy(dwellSince = null, shortcuts = ShortcutList(index, rows, card), hoveredRow = null)
                PieEvent.ShortcutsOpened(index)
            }
            is PieItem.Shortcut -> null
        }
    }

    /** How much of the Hover Delay has elapsed at [now], 0..1; null when nothing is dwelling. */
    fun dwellProgress(now: Long): Float? {
        val since = (state as? PieState.Open)?.dwellSince ?: return null
        return ((now - since).toFloat() / hoverDelayMillis).coerceIn(0f, 1f)
    }

    fun up(): PieRelease {
        val open = state as? PieState.Open
        state = PieState.Closed
        val list = open?.shortcuts
        val row = open?.hoveredRow
        if (list != null && row != null) {
            (open.layer.items[list.item] as? PieItem.App)?.let { return PieRelease.LaunchShortcut(it, row) }
        }
        val item = open?.hovered?.let(open.layer.items::get) ?: return PieRelease.Cancel
        return when (item) {
            is PieItem.App, is PieItem.Shortcut -> if (isAvailable(item)) PieRelease.Launch(item) else PieRelease.Cancel
            is PieItem.LayerSwitch -> PieRelease.Cancel
        }
    }

    fun cancel() {
        state = PieState.Closed
    }

    /** Whether dwelling on [item] does something: a Layer Switch, or an available app with Shortcuts. */
    private fun dwells(item: PieItem): Boolean =
        when (item) {
            is PieItem.LayerSwitch -> true
            is PieItem.App -> isAvailable(item) && shortcutCount(item) > 0
            is PieItem.Shortcut -> false
        }

    companion object {
        /** The dwell that fires a Layer Switch or opens an app's Shortcut list (PRD 4.4). */
        const val HOVER_DELAY_MILLIS = 675L
    }
}
