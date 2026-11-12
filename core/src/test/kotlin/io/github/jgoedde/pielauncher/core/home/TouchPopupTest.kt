package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchPopupTest {
    // A 1080 × 2400 window minus 100 px of system bars and margins top and bottom.
    private val bounds = PopupBounds(left = 0, top = 100, right = 1080, bottom = 2300)

    private fun place(
        touchX: Int,
        touchY: Int,
        width: Int = 400,
        height: Int = 300,
    ) = TouchPopup.place(touchX, touchY, width, height, bounds)

    @Test
    fun `with room all round the popup opens above the touch, starting at its left`() {
        assertEquals(PopupPosition(x = 480, y = 680), place(touchX = 500, touchY = 1000))
    }

    @Test
    fun `too close to the right edge it ends at the touch's right instead`() {
        assertEquals(PopupPosition(x = 520, y = 680), place(touchX = 900, touchY = 1000))
    }

    @Test
    fun `without room above it opens below the touch`() {
        assertEquals(PopupPosition(x = 480, y = 320), place(touchX = 500, touchY = 300))
    }

    @Test
    fun `a popup too wide for either alignment is kept inside the bounds`() {
        assertEquals(0, place(touchX = 500, touchY = 1000, width = 1000).x)
    }

    @Test
    fun `a popup too tall for either side is kept inside the bounds`() {
        assertEquals(200, place(touchX = 500, touchY = 200, height = 2100).y)
    }

    @Test
    fun `a widget's popup opens above the widget, starting at its left edge`() {
        val widget = PopupBounds(left = 100, top = 1000, right = 600, bottom = 1400)

        assertEquals(PopupPosition(x = 100, y = 700), TouchPopup.placeAbout(widget, width = 400, height = 300, bounds = bounds))
    }

    @Test
    fun `without room above a widget its popup opens below it`() {
        val widget = PopupBounds(left = 100, top = 200, right = 600, bottom = 500)

        assertEquals(PopupPosition(x = 100, y = 500), TouchPopup.placeAbout(widget, width = 400, height = 300, bounds = bounds))
    }
}
