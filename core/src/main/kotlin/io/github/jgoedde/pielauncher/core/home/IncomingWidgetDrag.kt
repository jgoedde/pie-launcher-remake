package io.github.jgoedde.pielauncher.core.home

/** What a widget drag onto Home carries, as far as `:core` needs to know (ADR 0001, ADR 0006). */
sealed interface WidgetDragCarry {
    /** A provider from the Widget Picker; Home keeps its Android names. */
    data object Provider : WidgetDragCarry

    /** A widget Pin Request from the pin sheet, held in a [PinHandOff] under [handle]. */
    data class Pin(
        val handle: Long,
    ) : WidgetDragCarry
}

/** What a widget drag from outside starts on Home. */
sealed interface DropStart {
    /** The widget add at [target]; a held Pin Request goes with it, to be taken by the add. */
    data class Add(
        val target: WidgetAddTarget,
    ) : DropStart

    /**
     * No add: Home releases the Pin Request [release] the drag held, if any, and tells Edit Mode
     * the add ended, so a Page the drag made goes.
     */
    data class NoAdd(
        val release: Long?,
    ) : DropStart
}

/**
 * One widget drag from outside onto Home (from the Widget Picker or the pin sheet), from its start
 * to its end: it decides what the drop starts. The drag starts at most one add; a Pin Request it
 * [held] is handed to that add, else released, once.
 */
class IncomingWidgetDrag(
    /** The Pin Request the drag holds, as known from its start; null for a Widget Picker drag. */
    private val held: Long?,
) {
    private var over = false

    /**
     * Dropped on Home: [carry] is what the drop carries (null when unreadable), [drop] where it
     * landed (null without target cells), [infoFound] whether the widget's provider info is still
     * there. Only the first call decides; later ones start nothing.
     */
    fun drop(
        carry: WidgetDragCarry?,
        drop: WidgetDrop?,
        infoFound: Boolean,
    ): DropStart {
        if (over) return DropStart.NoAdd(null)
        over = true
        val release = (carry as? WidgetDragCarry.Pin)?.handle ?: held
        if (carry == null || drop == null || !infoFound) return DropStart.NoAdd(release)
        return DropStart.Add(
            when (carry) {
                WidgetDragCarry.Provider -> WidgetAddTarget.Drop(drop)
                is WidgetDragCarry.Pin -> WidgetAddTarget.Pin(carry.handle, drop)
            },
        )
    }

    /** The drag ended; without a drop on Home before, it starts nothing. Null once a drop decided. */
    fun end(): DropStart? {
        if (over) return null
        over = true
        return DropStart.NoAdd(held)
    }
}
