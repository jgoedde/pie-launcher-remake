package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Empty non-Home Pages go away when Edit Mode ends (PRD 3.1). */
class PageCompactionTest {
    @Test
    fun `an empty Page between two others is removed and the Pages to its right move left`() {
        // Widget 1 on the Home Page, 2 on Page 1, 3 on Page 3: Page 2 is empty.
        val moves = PageCompaction.moves(mapOf(1 to 0, 2 to 1, 3 to 3))

        assertEquals(mapOf(3 to 2), moves)
    }

    @Test
    fun `the Home Page stays even when empty`() {
        assertEquals(mapOf(4 to 1, 5 to 2), PageCompaction.moves(mapOf(4 to 2, 5 to 5)))
    }

    @Test
    fun `Pages with no gaps stay as they are`() {
        assertEquals(emptyMap<Int, Int>(), PageCompaction.moves(mapOf(1 to 0, 2 to 1, 3 to 1, 4 to 2)))
    }

    @Test
    fun `there are Pages up to the rightmost one holding a widget, and always the Home Page`() {
        assertEquals(1, PageCompaction.pageCount(emptyList()))
        assertEquals(3, PageCompaction.pageCount(listOf(0, 2, 2)))
    }
}
