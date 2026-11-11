package io.github.jgoedde.pielauncher.core.pie

import io.github.jgoedde.pielauncher.core.config.Layer
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PieGestureTest {
    private val dialer = PieItem.App("com.android.dialer")
    private val camera = PieItem.App("app.grapheneos.camera")
    private val gone = PieItem.App("com.example.gone")
    private val toOther = PieItem.LayerSwitch("other")

    // Four items: 12, 3, 6 and 9 o'clock.
    private val home = Layer("home", "Home", items = listOf(dialer, camera, gone, toOther))
    private val config =
        PieConfig(
            version = 1,
            baseLayer = "home",
            layers = listOf(Layer("other", "Other", items = listOf(camera)), home),
        )
    private val window = Bounds(0f, 0f, 1000f, 2000f)
    private val gesture = PieGesture(PieMetrics.scaled(density = 1f), isAvailable = { it != gone })

    private fun open(at: Point = Point(500f, 1000f)) = gesture.down(at, window, config)

    private val open get() = gesture.state as PieState.Open

    @Test
    fun `touch-down opens the Pie at the finger on the Base Layer with nothing hovered`() {
        open()

        assertEquals(home, open.layer)
        assertEquals(Point(500f, 1000f), open.centre)
        assertNull(open.hovered)
    }

    @Test
    fun `the Pie opens clamped inside the window`() {
        open(at = Point(5f, 1000f))

        assertEquals(154f, open.centre.x)
    }

    @Test
    fun `entering an item reports a hover enter once`() {
        open()

        assertEquals(PieEvent.HoverEntered(1), gesture.move(Point(700f, 1000f)))
        assertNull(gesture.move(Point(720f, 1010f)))
        assertEquals(1, open.hovered)
        assertEquals(PieEvent.HoverEntered(0), gesture.move(Point(500f, 700f)))
    }

    @Test
    fun `moving back inside the Inner Circle clears the hover`() {
        open()
        gesture.move(Point(700f, 1000f))

        assertNull(gesture.move(Point(540f, 1000f)))
        assertNull(open.hovered)
    }

    @Test
    fun `release on an app item launches it and closes the Pie`() {
        open()
        gesture.move(Point(500f, 700f))

        assertEquals(PieRelease.Launch(dialer), gesture.up())
        assertEquals(PieState.Closed, gesture.state)
    }

    @Test
    fun `release with nothing selected cancels`() {
        open()
        gesture.move(Point(520f, 1000f))

        assertEquals(PieRelease.Cancel, gesture.up())
        assertEquals(PieState.Closed, gesture.state)
    }

    @Test
    fun `release on an Unavailable Item does nothing`() {
        open()
        gesture.move(Point(500f, 1300f))

        assertEquals(2, open.hovered)
        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `release on a Layer Switch cancels`() {
        open()
        gesture.move(Point(200f, 1000f))

        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `a cancelled gesture closes the Pie`() {
        open()
        gesture.move(Point(500f, 700f))

        gesture.cancel()

        assertEquals(PieState.Closed, gesture.state)
        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `moves while closed do nothing`() {
        assertNull(gesture.move(Point(500f, 700f)))
        assertEquals(PieState.Closed, gesture.state)
    }
}
