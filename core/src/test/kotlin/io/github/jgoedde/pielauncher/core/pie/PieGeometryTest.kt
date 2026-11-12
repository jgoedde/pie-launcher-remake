package io.github.jgoedde.pielauncher.core.pie

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PieGeometryTest {
    // Radius 120, Inner Circle 90, centre 26, items 26 px from their middle to their edge, 8 px margin.
    private val metrics = testMetrics
    private val centre = Point(500f, 500f)

    /** A point [distance] px from [centre], [degrees] clockwise from 12 o'clock. */
    private fun at(
        degrees: Double,
        distance: Float,
    ): Point {
        val rad = Math.toRadians(degrees)
        return Point(centre.x + distance * sin(rad).toFloat(), centre.y - distance * cos(rad).toFloat())
    }

    private fun assertNear(
        expected: Point,
        actual: Point,
    ) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun `the default metrics follow the prototype, scaled by density`() {
        val m = PieMetrics.scaled(density = 2f)
        assertEquals(240f, m.radius)
        assertEquals(180f, m.innerRadius)
        assertEquals(52f, m.centreRadius)
    }

    @Test
    fun `items sit on the ring clockwise from 12 o'clock in config order`() {
        assertNear(Point(500f, 380f), PieGeometry.itemPosition(centre, index = 0, count = 4, metrics))
        assertNear(Point(620f, 500f), PieGeometry.itemPosition(centre, index = 1, count = 4, metrics))
        assertNear(Point(500f, 620f), PieGeometry.itemPosition(centre, index = 2, count = 4, metrics))
        assertNear(Point(380f, 500f), PieGeometry.itemPosition(centre, index = 3, count = 4, metrics))
    }

    @Test
    fun `inside the Inner Circle nothing is hovered, and its innermost part is the centre`() {
        assertEquals(PieHit.None, PieGeometry.hitTest(centre, at(0.0, 80f), count = 4, hovered = null, metrics))
        assertEquals(PieHit.Centre, PieGeometry.hitTest(centre, at(0.0, 20f), count = 4, hovered = null, metrics))
    }

    @Test
    fun `outside the Inner Circle only the direction counts, with no outer limit`() {
        assertEquals(PieHit.Item(1), PieGeometry.hitTest(centre, at(100.0, 95f), count = 4, hovered = null, metrics))
        assertEquals(PieHit.Item(1), PieGeometry.hitTest(centre, at(100.0, 5_000f), count = 4, hovered = null, metrics))
        assertEquals(PieHit.Item(3), PieGeometry.hitTest(centre, at(-80.0, 300f), count = 4, hovered = null, metrics))
        assertEquals(PieHit.Item(0), PieGeometry.hitTest(centre, at(-40.0, 300f), count = 4, hovered = null, metrics))
    }

    @Test
    fun `the hovered item stays hovered until the angle deviates by more than 65 percent of a sector`() {
        // Four items: a sector is 90 degrees, so item 0 holds until 58.5 degrees off.
        assertEquals(PieHit.Item(0), PieGeometry.hitTest(centre, at(58.0, 200f), count = 4, hovered = 0, metrics))
        assertEquals(PieHit.Item(1), PieGeometry.hitTest(centre, at(59.0, 200f), count = 4, hovered = 0, metrics))
        assertEquals(PieHit.Item(0), PieGeometry.hitTest(centre, at(-58.0, 200f), count = 4, hovered = 0, metrics))
    }

    @Test
    fun `a single item takes every direction`() {
        assertEquals(PieHit.Item(0), PieGeometry.hitTest(centre, at(180.0, 200f), count = 1, hovered = null, metrics))
    }

    @Test
    fun `the centre is clamped so the whole ring stays inside the bounds plus margin`() {
        val window = Bounds(left = 0f, top = 100f, right = 1000f, bottom = 2000f)
        // Ring extent: radius 120 + item half size 26 + margin 8 = 154.
        assertNear(Point(154f, 254f), PieGeometry.clampCentre(Point(10f, 120f), window, metrics))
        assertNear(Point(846f, 1846f), PieGeometry.clampCentre(Point(990f, 1990f), window, metrics))
        assertNear(Point(400f, 900f), PieGeometry.clampCentre(Point(400f, 900f), window, metrics))
    }

    @Test
    fun `a window too small for the ring centres the Pie in it`() {
        val window = Bounds(left = 0f, top = 0f, right = 200f, bottom = 2000f)
        assertEquals(100f, PieGeometry.clampCentre(Point(10f, 900f), window, metrics).x)
    }
}
