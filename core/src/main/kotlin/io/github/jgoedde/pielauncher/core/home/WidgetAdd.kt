package io.github.jgoedde.pielauncher.core.home

/** Where a widget add puts the widget. */
sealed interface WidgetAddTarget {
    /**
     * Tap-add from the Widget Picker: the first free spot of the long-pressed [zone], else of any
     * Page, else on a new Page ([PageLayouts.add]); null [zone]: the Home Page, upper zone first.
     */
    data class Tap(
        val zone: PageZone?,
    ) : WidgetAddTarget

    /** Dragged in from the Widget Picker and dropped at [drop]. */
    data class Drop(
        val drop: WidgetDrop,
    ) : WidgetAddTarget

    /**
     * A widget Pin Request dragged in from the pin sheet and dropped at [drop]. [handle] stands for
     * the request (a [PinHandOff] token), so `:core` stays free of Android types (ADR 0001).
     */
    data class Pin(
        val handle: Long,
        val drop: WidgetDrop,
    ) : WidgetAddTarget
}

/** How a widget add ended. */
sealed interface WidgetAddOutcome {
    /** Widget [widgetId] is on [page]; Home shows that Page and the widget's Resize Frame opens. */
    data class Added(
        val widgetId: Int,
        val page: Int,
    ) : WidgetAddOutcome

    data object NoRoom : WidgetAddOutcome

    data object TooLarge : WidgetAddOutcome

    data object Cancelled : WidgetAddOutcome
}

/** Turns widget adds into strings for Intent and ClipDescription extras, and back; null for anything else. */
object WidgetAddCodec {
    private const val TAP = "tap"
    private const val DROP = "drop"
    private const val PIN = "pin"
    private const val ADDED = "added"
    private const val NO_ROOM = "no_room"
    private const val TOO_LARGE = "too_large"
    private const val CANCELLED = "cancelled"

    fun encode(target: WidgetAddTarget): String =
        when (target) {
            is WidgetAddTarget.Tap -> line(listOf(TAP) + target.zone?.words().orEmpty())
            is WidgetAddTarget.Drop -> line(listOf(DROP) + target.drop.words())
            is WidgetAddTarget.Pin -> line(listOf(PIN, target.handle) + target.drop.words())
        }

    fun target(encoded: String?): WidgetAddTarget? {
        val read = Reader.of(encoded) ?: return null
        return when (read.word()) {
            TAP -> WidgetAddTarget.Tap(if (read.done) null else read.zone() ?: return null)
            DROP -> read.drop()?.let(WidgetAddTarget::Drop)
            PIN -> {
                val handle = read.word()?.toLongOrNull() ?: return null
                read.drop()?.let { WidgetAddTarget.Pin(handle, it) }
            }
            else -> null
        }?.takeIf { read.done }
    }

    fun encode(outcome: WidgetAddOutcome): String =
        when (outcome) {
            is WidgetAddOutcome.Added -> line(listOf(ADDED, outcome.widgetId, outcome.page))
            WidgetAddOutcome.NoRoom -> NO_ROOM
            WidgetAddOutcome.TooLarge -> TOO_LARGE
            WidgetAddOutcome.Cancelled -> CANCELLED
        }

    fun outcome(encoded: String?): WidgetAddOutcome? {
        val read = Reader.of(encoded) ?: return null
        return when (read.word()) {
            ADDED -> {
                val widgetId = read.int() ?: return null
                val page = read.int()?.takeIf { it >= HOME_PAGE } ?: return null
                WidgetAddOutcome.Added(widgetId, page)
            }
            NO_ROOM -> WidgetAddOutcome.NoRoom
            TOO_LARGE -> WidgetAddOutcome.TooLarge
            CANCELLED -> WidgetAddOutcome.Cancelled
            else -> null
        }?.takeIf { read.done }
    }

    /** Default, min and max spans, then the axes it resizes on. */
    fun encode(bounds: SpanBounds): String =
        with(bounds) { line(default.words() + min.words() + max.words() + listOf(resizesHorizontally, resizesVertically)) }

    fun bounds(encoded: String?): SpanBounds? {
        val read = Reader.of(encoded) ?: return null
        val (default, min, max) = List(3) { read.span() ?: return null }
        val (horizontally, vertically) = List(2) { read.word()?.toBooleanStrictOrNull() ?: return null }
        return SpanBounds(default, min, max, horizontally, vertically).takeIf { read.done }
    }

    private fun line(words: List<Any>) = words.joinToString(" ")

    private fun PageZone.words(): List<Any> = listOf(page, zone.name)

    private fun CellSpan.words(): List<Any> = listOf(width, height)

    private fun CellRect.words(): List<Any> = listOf(column, row, width, height)

    /** Page, zone, cells, then id and cells per moved neighbour. */
    private fun WidgetDrop.words(): List<Any> =
        PageZone(page, zone).words() + cells.words() + moved.flatMap { (id, cells) -> listOf(id) + cells.words() }

    /** Reads [words] front to back; every read is null once a word is missing or malformed. */
    private class Reader(
        private val words: List<String>,
    ) {
        private var at = 0
        val done get() = at == words.size

        fun word(): String? = words.getOrNull(at)?.also { at++ }

        fun int(): Int? = word()?.toIntOrNull()

        fun zone(): PageZone? {
            val page = int()?.takeIf { it >= HOME_PAGE } ?: return null
            val name = word() ?: return null
            return WidgetZoneId.entries.firstOrNull { it.name == name }?.let { PageZone(page, it) }
        }

        fun span(): CellSpan? {
            val (width, height) = List(2) { int() ?: return null }
            return CellSpan(width, height)
        }

        fun rect(): CellRect? {
            val (column, row, width, height) = List(4) { int() ?: return null }
            return CellRect(column, row, width, height)
        }

        /** A [WidgetDrop] up to the last word. */
        fun drop(): WidgetDrop? {
            val zone = zone() ?: return null
            val cells = rect() ?: return null
            val moved = mutableMapOf<Int, CellRect>()
            while (!done) moved[int() ?: return null] = rect() ?: return null
            return WidgetDrop(zone.page, zone.zone, cells, moved)
        }

        companion object {
            fun of(encoded: String?): Reader? = encoded?.takeIf { it.isNotEmpty() }?.let { Reader(it.split(' ')) }
        }
    }
}
