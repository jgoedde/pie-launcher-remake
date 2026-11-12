package io.github.jgoedde.pielauncher.core.home

/** What the pin sheet's "Add to home screen" does for a widget Pin Request (PRD 7.6). */
sealed interface PinAdd {
    /** Enabled: the widget lands in [zone] at [span]. */
    data class Ready(
        val zone: WidgetZoneId,
        val span: CellSpan,
    ) : PinAdd

    /** Disabled, saying why: no room, or too large for this home screen. */
    data class Disabled(
        val reason: Rejection,
    ) : PinAdd
}

/** The pin sheet's rules, computed before the sheet shows. */
object PinSheet {
    /** "Add to home screen" for the result of adding the widget with no long-pressed zone ([PageLayouts.add]). */
    fun add(result: LayoutResult): PinAdd =
        when (result) {
            is LayoutResult.Changed -> PinAdd.Ready(result.widget.zone, result.widget.cells.span)
            is LayoutResult.Rejected -> PinAdd.Disabled(if (result.reason == Rejection.TooLarge) Rejection.TooLarge else Rejection.NoRoom)
            LayoutResult.Unchanged -> PinAdd.Disabled(Rejection.NoRoom)
        }
}

/**
 * Holds Pin Requests while a pin sheet drag is on its way to Home (ADR 0006): the drag carries
 * only a token, and the drop takes the request it stands for, once. Thread-safe.
 */
class PinHandOff<R : Any> {
    private val held = mutableMapOf<Long, R>()
    private var next = 0L

    /** Holds [request] until [take] or [release]; the token goes with the drag. */
    @Synchronized
    fun hold(request: R): Long = (++next).also { held[it] = request }

    /** The request [token] stands for, kept until [take] or [release]; null when it was taken or released. */
    @Synchronized
    fun get(token: Long): R? = held[token]

    /** The request [token] stands for, handed over once; null when it was taken or released. */
    @Synchronized
    fun take(token: Long): R? = held.remove(token)

    @Synchronized
    fun release(token: Long) {
        held.remove(token)
    }
}
