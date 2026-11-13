package io.github.jgoedde.pielauncher.core.home

/** What the pin sheet's "Add to home screen" does for a widget Pin Request (PRD 7.6), known before the sheet shows. */
sealed interface PinAdd {
    /** Enabled: the widget lands at [span]. */
    data class Ready(
        val span: CellSpan,
    ) : PinAdd

    /** Disabled, saying why: no room, or too large for this home screen. */
    data class Disabled(
        val reason: Rejection,
    ) : PinAdd
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

    /** The request [token] stands for, still held; null when it was taken or released. */
    @Synchronized
    fun peek(token: Long): R? = held[token]

    /** The request [token] stands for, handed over once; null when it was taken or released. */
    @Synchronized
    fun take(token: Long): R? = held.remove(token)

    @Synchronized
    fun release(token: Long) {
        held.remove(token)
    }
}
