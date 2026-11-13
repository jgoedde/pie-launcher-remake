package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** A widget add crossing the Activity seams (Widget Picker, add trampoline, drag hand-off) as primitives. */
class WidgetAddCodecTest {
    private fun roundTrip(target: WidgetAddTarget) = assertEquals(target, WidgetAddCodec.target(WidgetAddCodec.encode(target)))

    @Test
    fun `a tap-add in a long-pressed Widget Zone survives the trip`() {
        roundTrip(WidgetAddTarget.Tap(PageZone(2, WidgetZoneId.Page)))
        roundTrip(WidgetAddTarget.Tap(PageZone(HOME_PAGE, WidgetZoneId.HomeLower)))
    }

    @Test
    fun `a tap-add with no long-pressed zone survives the trip`() {
        roundTrip(WidgetAddTarget.Tap(null))
    }

    @Test
    fun `a drop survives the trip with its Page and the neighbours it moved`() {
        roundTrip(WidgetAddTarget.Drop(drop))
        roundTrip(WidgetAddTarget.Drop(WidgetDrop(HOME_PAGE, WidgetZoneId.HomeUpper, CellRect(0, 0, 4, 2))))
    }

    @Test
    fun `a drop names its Widget Zone, as Room does`() {
        assertEquals(true, WidgetAddCodec.encode(WidgetAddTarget.Drop(drop)).contains("Page"))
    }

    @Test
    fun `a widget Pin Request dragged in from the pin sheet survives the trip as its handle`() {
        roundTrip(WidgetAddTarget.Pin(handle = 42L, drop))
        roundTrip(WidgetAddTarget.Pin(handle = Long.MAX_VALUE, drop))
        roundTrip(WidgetAddTarget.PinSheet)
    }

    @Test
    fun `every add outcome survives the trip back to Home`() {
        listOf(
            WidgetAddOutcome.Added(widgetId = 17, page = 2),
            WidgetAddOutcome.NoRoom,
            WidgetAddOutcome.TooLarge,
            WidgetAddOutcome.Cancelled,
        ).forEach { assertEquals(it, WidgetAddCodec.outcome(WidgetAddCodec.encode(it))) }
    }

    @Test
    fun `span bounds survive the trip from the drag's start to Home`() {
        val bounds = SpanBounds(CellSpan(3, 2), CellSpan(2, 1), CellSpan(4, 2), resizesHorizontally = true, resizesVertically = false)

        assertEquals(bounds, WidgetAddCodec.bounds(WidgetAddCodec.encode(bounds)))
    }

    @Test
    fun `malformed targets decode to nothing`() {
        listOf(
            null,
            "",
            "tap 0",
            "tap -1 Page",
            "tap 0 Nowhere",
            "tap 0 HomeUpper 1",
            // A Widget Zone by ordinal, as drops once were.
            "drop 3 2 1 0 2 1",
            "drop 3 Page 1 0 2",
            "drop 3 Page 1 0 2 1 4 0 0 1",
            "pin drop 3 Page 1 0 2 1",
            "pin 42",
            "lift 0 HomeUpper",
        ).forEach { assertEquals(it, null, WidgetAddCodec.target(it)) }
    }

    @Test
    fun `malformed outcomes and span bounds decode to nothing`() {
        listOf(null, "", "added", "added 17", "added x 2", "added 17 -1", "no_room 1", "done").forEach {
            assertEquals(it, null, WidgetAddCodec.outcome(it))
        }
        listOf(null, "", "1 1", "3 2 2 1 4 2 1 0", "3 2 2 1 4 2 true false true").forEach {
            assertEquals(it, null, WidgetAddCodec.bounds(it))
        }
    }

    private val drop = WidgetDrop(3, WidgetZoneId.Page, CellRect(1, 0, 2, 1), mapOf(3 to CellRect(3, 0, 1, 1), 4 to CellRect(0, 0, 1, 1)))
}
