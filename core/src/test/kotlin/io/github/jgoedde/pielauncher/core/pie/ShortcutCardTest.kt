package io.github.jgoedde.pielauncher.core.pie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutCardTest {
    // At density 1: icons 26 px in radius, cards 184 px wide and 10 px off the icon, rows 44 px with 6 px padding.
    private val metrics = PieMetrics.scaled(density = 1f)
    private val window = Bounds(0f, 0f, 1000f, 2000f)

    private fun place(
        item: Point,
        centre: Point,
        rows: Int = 3,
    ) = ShortcutCard.place(item, centre, rows, window, metrics)

    @Test
    fun `the card opens beside the icon, away from the Pie centre`() {
        assertEquals(Bounds(656f, 974f, 840f, 1118f), place(item = Point(620f, 1000f), centre = Point(500f, 1000f)))
        assertEquals(Bounds(160f, 974f, 344f, 1118f), place(item = Point(380f, 1000f), centre = Point(500f, 1000f)))
    }

    @Test
    fun `the card opens to the left when there is no room on the right`() {
        assertEquals(Bounds(700f, 974f, 884f, 1118f), place(item = Point(920f, 1000f), centre = Point(800f, 1000f)))
    }

    @Test
    fun `the lowest item near the bottom opens its card upward`() {
        val card = place(item = Point(500f, 1920f), centre = Point(500f, 1800f), rows = 5)

        assertEquals(Bounds(536f, 1714f, 720f, 1946f), card)
    }

    @Test
    fun `the topmost item opens its card upward when there is room`() {
        assertEquals(Bounds(536f, 806f, 720f, 906f), place(item = Point(500f, 880f), centre = Point(500f, 1000f), rows = 2))
    }

    @Test
    fun `a card too tall for either side starts at the window top margin`() {
        val short = Bounds(0f, 0f, 1000f, 300f)

        val card = ShortcutCard.place(Point(500f, 150f), Point(500f, 150f), rows = 8, window = short, metrics = metrics)

        assertEquals(8f, card.top)
        assertEquals(8f + 8 * 44 + 12, card.bottom)
    }

    @Test
    fun `a finger on the card picks the row under it`() {
        val card = Bounds(656f, 974f, 840f, 1118f)

        assertEquals(0, ShortcutCard.rowAt(card, rows = 3, finger = Point(700f, 976f), metrics = metrics))
        assertEquals(1, ShortcutCard.rowAt(card, rows = 3, finger = Point(700f, 1030f), metrics = metrics))
        assertEquals(2, ShortcutCard.rowAt(card, rows = 3, finger = Point(700f, 1117f), metrics = metrics))
        assertNull(ShortcutCard.rowAt(card, rows = 3, finger = Point(650f, 1030f), metrics = metrics))
    }
}
