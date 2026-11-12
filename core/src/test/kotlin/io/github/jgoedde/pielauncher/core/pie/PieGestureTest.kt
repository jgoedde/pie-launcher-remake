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
    private val toHome = PieItem.LayerSwitch("home")
    private val mail = PieItem.Shortcut("com.example.mail", "compose")

    // Four items: 12, 3, 6 and 9 o'clock.
    private val home = Layer("home", "Home", items = listOf(dialer, camera, gone, toOther))
    private val config =
        PieConfig(
            version = 1,
            baseLayer = "home",
            layers = listOf(Layer("other", "Other", items = listOf(toHome, mail)), home),
        )
    private val window = Bounds(0f, 0f, 1000f, 2000f)
    private var now = 0L

    // The dialer has two Shortcuts; so does the Unavailable Item, which must not open them.
    private val gesture =
        PieGesture(
            testMetrics,
            isAvailable = { it != gone },
            shortcutCount = { if (it == dialer || it == gone) 2 else 0 },
        )

    private fun open(at: Point = Point(500f, 1000f)) = gesture.down(at, window, config)

    /** Lets [millis] pass with the finger still. */
    private fun dwell(millis: Long): PieEvent? {
        now += millis
        return gesture.tick(now)
    }

    private fun move(to: Point) = gesture.move(to, now)

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

        assertEquals(PieEvent.HoverEntered(1), move(Point(700f, 1000f)))
        assertNull(move(Point(720f, 1010f)))
        assertEquals(1, open.hovered)
        assertEquals(PieEvent.HoverEntered(0), move(Point(500f, 700f)))
    }

    @Test
    fun `moving back inside the Inner Circle clears the hover`() {
        open()
        move(Point(700f, 1000f))

        assertNull(move(Point(540f, 1000f)))
        assertNull(open.hovered)
    }

    @Test
    fun `release on an app item launches it and closes the Pie`() {
        open()
        move(Point(500f, 700f))

        assertEquals(PieRelease.Launch(dialer), gesture.up())
        assertEquals(PieState.Closed, gesture.state)
    }

    @Test
    fun `release with nothing selected cancels`() {
        open()
        move(Point(520f, 1000f))

        assertEquals(PieRelease.Cancel, gesture.up())
        assertEquals(PieState.Closed, gesture.state)
    }

    @Test
    fun `release on an Unavailable Item does nothing`() {
        open()
        move(Point(500f, 1300f))

        assertEquals(2, open.hovered)
        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `release on a Layer Switch cancels`() {
        open()
        move(Point(200f, 1000f))

        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `a cancelled gesture closes the Pie`() {
        open()
        move(Point(500f, 700f))

        gesture.cancel()

        assertEquals(PieState.Closed, gesture.state)
        assertEquals(PieRelease.Cancel, gesture.up())
    }

    @Test
    fun `moves while closed do nothing`() {
        assertNull(move(Point(500f, 700f)))
        assertEquals(PieState.Closed, gesture.state)
    }

    @Test
    fun `dwelling on a Layer Switch for the Hover Delay switches Layer and re-centres at the finger`() {
        open()
        move(Point(200f, 1000f))

        assertNull(dwell(PieGesture.HOVER_DELAY_MILLIS - 1))
        assertEquals(home, open.layer)
        assertEquals(PieEvent.LayerSwitched, dwell(1))

        assertEquals(config.layer("other"), open.layer)
        assertEquals(Point(200f, 1000f), open.centre)
        assertNull(open.hovered)
    }

    @Test
    fun `the Progress Disc fills while dwelling on a Layer Switch or an app with Shortcuts`() {
        open()
        move(Point(200f, 1000f))
        dwell(300)

        assertEquals(300f / 675f, gesture.dwellProgress(now)!!, 0.001f)

        move(Point(500f, 700f))
        assertEquals(0f, gesture.dwellProgress(now))
        dwell(600)
        assertEquals(600f / 675f, gesture.dwellProgress(now)!!, 0.001f)
    }

    @Test
    fun `apps without Shortcuts, Unavailable Items and Shortcut items show no Progress Disc and fire nothing`() {
        open()
        move(Point(700f, 1000f))
        assertNull(gesture.dwellProgress(now))
        assertNull(dwell(1000))

        move(Point(500f, 1300f))
        assertNull(gesture.dwellProgress(now))
        assertNull(dwell(1000))

        move(Point(200f, 1000f))
        dwell(675)
        move(Point(200f, 1150f))
        assertEquals(mail, open.layer.items[open.hovered!!])
        assertNull(gesture.dwellProgress(now))
        assertNull(dwell(1000))
    }

    @Test
    fun `a Layer Switch may lead back to the Base Layer`() {
        open()
        move(Point(200f, 1000f))
        dwell(675)

        move(Point(200f, 700f))
        assertEquals(PieEvent.LayerSwitched, dwell(675))

        assertEquals(home, open.layer)
        assertEquals(Point(200f, 700f), open.centre)
    }

    @Test
    fun `the re-centred Pie is clamped inside the window`() {
        open(at = Point(200f, 1000f))
        move(Point(20f, 1000f))

        dwell(675)

        assertEquals(Point(154f, 1000f), open.centre)
    }

    /** Opens the dialer's Shortcut list: the dialer sits at 12 o'clock, its card above and to the right. */
    private fun openShortcuts(): PieEvent? {
        open()
        move(Point(500f, 700f))
        return dwell(675)
    }

    @Test
    fun `dwelling on an app with Shortcuts opens its Shortcut list beside the icon`() {
        assertEquals(PieEvent.ShortcutsOpened(0), openShortcuts())

        assertEquals(ShortcutList(item = 0, rows = 2, card = Bounds(536f, 806f, 720f, 906f)), open.shortcuts)
        assertNull(gesture.dwellProgress(now))
        assertNull(dwell(1000))
    }

    @Test
    fun `release on a Shortcut row launches that Shortcut`() {
        openShortcuts()

        assertEquals(PieEvent.ShortcutHovered(1), move(Point(600f, 860f)))
        assertEquals(PieRelease.LaunchShortcut(dialer, row = 1), gesture.up())
    }

    @Test
    fun `the card is hit-tested before the ring`() {
        openShortcuts()

        // Without the card, this point would hover the camera at 3 o'clock.
        move(Point(715f, 900f))

        assertEquals(1, open.hoveredRow)
        assertEquals(0, open.hovered)
    }

    @Test
    fun `the list stays open while the thumb travels to it`() {
        openShortcuts()

        move(Point(530f, 870f))
        move(Point(560f, 1000f))

        assertEquals(0, open.shortcuts?.item)
        assertNull(open.hoveredRow)
    }

    @Test
    fun `moving over another Pie Item closes the list`() {
        openShortcuts()

        move(Point(700f, 1000f))

        assertNull(open.shortcuts)
        assertEquals(PieRelease.Launch(camera), gesture.up())
    }

    @Test
    fun `moving into the centre closes the list`() {
        openShortcuts()

        move(Point(505f, 1000f))

        assertNull(open.shortcuts)
    }

    @Test
    fun `release on the app itself with its list open launches the app`() {
        openShortcuts()
        move(Point(600f, 860f))
        move(Point(500f, 700f))

        assertNull(open.hoveredRow)
        assertEquals(PieRelease.Launch(dialer), gesture.up())
    }
}
