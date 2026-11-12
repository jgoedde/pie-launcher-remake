package io.github.jgoedde.pielauncher.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialSymbolsTest {
    @Test
    fun `a Material Symbols Rounded name gives its codepoint`() {
        // Codepoints as published in Google's codepoints file.
        assertEquals(0xE53B, MaterialSymbols.codepoint("layers"))
        assertEquals(0xE9B2, MaterialSymbols.codepoint("home"))
    }

    @Test
    fun `an unknown name has no codepoint`() {
        assertNull(MaterialSymbols.codepoint("no_such_symbol"))
    }
}
