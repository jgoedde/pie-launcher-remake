package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeLower
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRemovalTest {
    private val clock = widget(1, HomeUpper, CellRect(0, 0, 2, 2))
    private val notes = widget(2, HomeLower, CellRect(0, 0, 4, 1))
    private val layout = homePage(clock, notes)

    @Test
    fun `a removed widget disappears at once but is not deleted yet`() {
        val step = WidgetRemoval.None.remove(clock, nowMs = 0)

        assertEquals(listOf(notes), step.removal.shown(layout).widgets)
        assertEquals(emptyList<LaidOutWidget>(), step.delete)
        assertEquals(WidgetRemoval.UNDO_TIMEOUT_MS, step.removal.dueAtMs)
    }

    @Test
    fun `the deletion is due when the 4 s undo window runs out, not before`() {
        val removal = WidgetRemoval.None.remove(clock, nowMs = 1000).removal

        assertEquals(emptyList<LaidOutWidget>(), removal.expire(nowMs = 4999).delete)
        val expired = removal.expire(nowMs = 5000)
        assertEquals(listOf(clock), expired.delete)
        assertEquals(layout.widgets, expired.removal.shown(layout).widgets)
        assertNull(expired.removal.dueAtMs)
    }

    @Test
    fun `undo brings the widget back on its cells and deletes nothing`() {
        val removal = WidgetRemoval.None.remove(clock, nowMs = 0).removal

        val undone = removal.undo()

        assertEquals(clock, undone.restored)
        assertEquals(layout.widgets, undone.removal.shown(layout).widgets)
        assertEquals(emptyList<LaidOutWidget>(), undone.removal.expire(nowMs = 10_000).delete)
    }

    @Test
    fun `removing another widget deletes the first one, which can no longer be undone`() {
        val first = WidgetRemoval.None.remove(clock, nowMs = 0).removal

        val second = first.remove(notes, nowMs = 1000)

        assertEquals(listOf(clock), second.delete)
        assertEquals(notes, second.removal.undo().restored)
        assertEquals(5000L, second.removal.dueAtMs)
    }

    @Test
    fun `starting another edit commits the pending removal`() {
        val committed = WidgetRemoval.None.remove(clock, nowMs = 0).removal.commit()

        assertEquals(listOf(clock), committed.delete)
        assertNull(committed.removal.undo().restored)
    }
}
