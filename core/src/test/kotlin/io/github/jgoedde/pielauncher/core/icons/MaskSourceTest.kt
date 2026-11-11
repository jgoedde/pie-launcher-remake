package io.github.jgoedde.pielauncher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class MaskSourceTest {
    @Test
    fun `an app icon with a monochrome layer uses that layer`() {
        assertEquals(MaskSource.MonochromeLayer, maskSource(IconOwner.App, IconTraits(hasMonochrome = true, isBitmap = false)))
    }

    @Test
    fun `an app icon without a monochrome layer gets a Generated Monochrome, bitmap or not`() {
        assertEquals(MaskSource.Generated, maskSource(IconOwner.App, IconTraits(hasMonochrome = false, isBitmap = false)))
        assertEquals(MaskSource.Generated, maskSource(IconOwner.App, IconTraits(hasMonochrome = false, isBitmap = true)))
    }

    @Test
    fun `a Shortcut with a bitmap icon such as a contact photo stays an Original Icon`() {
        assertEquals(MaskSource.Original, maskSource(IconOwner.Shortcut, IconTraits(hasMonochrome = false, isBitmap = true)))
    }

    @Test
    fun `a Shortcut with a resource or vector icon gets a Generated Monochrome unless it has a monochrome layer`() {
        assertEquals(MaskSource.Generated, maskSource(IconOwner.Shortcut, IconTraits(hasMonochrome = false, isBitmap = false)))
        assertEquals(MaskSource.MonochromeLayer, maskSource(IconOwner.Shortcut, IconTraits(hasMonochrome = true, isBitmap = false)))
    }

    @Test
    fun `a PWA always gets a Generated Monochrome of its favicon`() {
        assertEquals(MaskSource.Generated, maskSource(IconOwner.Pwa, IconTraits(hasMonochrome = false, isBitmap = true)))
        assertEquals(MaskSource.Generated, maskSource(IconOwner.Pwa, IconTraits(hasMonochrome = true, isBitmap = true)))
    }
}
