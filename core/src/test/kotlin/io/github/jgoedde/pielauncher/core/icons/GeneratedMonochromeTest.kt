package io.github.jgoedde.pielauncher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedMonochromeTest {
    /** A 12 px layer whose visible part is the centre 8 px; the outer row on each side counts as edge. */
    private val layerSize = 12
    private val visibleSize = 8

    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun layer(pixel: (x: Int, y: Int) -> Int) = IntArray(layerSize * layerSize) { pixel(it % layerSize, it / layerSize) }

    /** The mask as unsigned alpha rows, for readable failures. */
    private fun ByteArray.rows() = toList().map { it.toInt() and 0xFF }.chunked(visibleSize)

    private fun inGlyph(
        x: Int,
        y: Int,
    ) = x in 4..7 && y in 4..7

    @Test
    fun `a dim glyph on a dark background becomes a fully opaque glyph on a clear background`() {
        val flat = layer { x, y -> if (inGlyph(x, y)) rgb(30, 60, 90) else rgb(0, 0, 0) }

        val mask = generatedMonochrome(flat, layerSize, visibleSize)

        val expected = List(visibleSize) { y -> List(visibleSize) { x -> if (inGlyph(x + 2, y + 2)) 255 else 0 } }
        assertEquals(expected, mask.rows())
    }

    @Test
    fun `the alpha is the plain average of red, green and blue`() {
        // Red averages to 85; white (255) and black (0) pin the range so nothing is stretched.
        val flat =
            layer { x, y ->
                when {
                    x == 5 && y == 5 -> rgb(255, 0, 0)
                    x == 6 && y == 6 -> rgb(255, 255, 255)
                    else -> rgb(0, 0, 0)
                }
            }

        val rows = generatedMonochrome(flat, layerSize, visibleSize).rows()

        assertEquals(85, rows[3][3])
        assertEquals(255, rows[4][4])
    }

    @Test
    fun `a dark glyph on a white background is inverted so the glyph is opaque`() {
        val flat = layer { x, y -> if (inGlyph(x, y)) rgb(0, 0, 0) else rgb(255, 255, 255) }

        val mask = generatedMonochrome(flat, layerSize, visibleSize)

        val expected = List(visibleSize) { y -> List(visibleSize) { x -> if (inGlyph(x + 2, y + 2)) 255 else 0 } }
        assertEquals(expected, mask.rows())
    }
}
