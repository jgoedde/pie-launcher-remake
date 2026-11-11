package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class NewBadgeTest {
    private val day = 24 * 60 * 60 * 1000L
    private val addedAt = 1_000_000_000L

    @Test
    fun `a badge added just now is active`() {
        assertEquals(setOf("maps"), listOf(NewBadge("maps", addedAt)).activeAt(addedAt))
    }

    @Test
    fun `a badge is still active just before seven days have passed`() {
        assertEquals(setOf("maps"), listOf(NewBadge("maps", addedAt)).activeAt(addedAt + 7 * day - 1))
    }

    @Test
    fun `a badge expires after seven days`() {
        assertEquals(emptySet<String>(), listOf(NewBadge("maps", addedAt)).activeAt(addedAt + 7 * day))
    }

    @Test
    fun `only the expired badges drop out`() {
        val badges = listOf(NewBadge("old", addedAt), NewBadge("fresh", addedAt + 6 * day))
        assertEquals(setOf("fresh"), badges.activeAt(addedAt + 8 * day))
    }
}
