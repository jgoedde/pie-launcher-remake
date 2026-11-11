package io.github.jgoedde.pielauncher.core.drawer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AlphabetIndexTest {
    private fun index(vararg labels: String) = alphabetIndex(labels.toList(), Locale.GERMAN) { it }

    @Test
    fun `only letters that have entries appear, each pointing at its first entry`() {
        assertEquals(
            listOf(IndexLetter("C", 0), IndexLetter("F", 2), IndexLetter("M", 3)),
            index("Calendar", "camera", "Files", "Maps"),
        )
    }

    @Test
    fun `accented letters fold into their base letter`() {
        assertEquals(
            listOf(IndexLetter("A", 0), IndexLetter("B", 2), IndexLetter("E", 3)),
            index("Ärzte", "Authenticator", "Bank", "Éclair", "Email"),
        )
    }

    @Test
    fun `digits and symbols share a hash bucket at the top`() {
        assertEquals(
            listOf(IndexLetter("#", 0), IndexLetter("C", 2)),
            index("1Password", "@Home", "Calendar"),
        )
        assertEquals(
            listOf(IndexLetter("#", 1), IndexLetter("C", 0)),
            index("Calendar", "+Plus"),
        )
    }
}
