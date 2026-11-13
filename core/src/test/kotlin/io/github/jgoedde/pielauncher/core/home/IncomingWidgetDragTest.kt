package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingWidgetDragTest {
    private val drop = WidgetDrop(page = 1, zone = WidgetZoneId.Page, cells = CellRect(0, 0, 2, 2))

    @Test
    fun `a pin drop with target cells and provider info starts the add and hands over the Pin Request once`() {
        val drag = IncomingWidgetDrag(held = 7)

        assertEquals(DropStart.Add(WidgetAddTarget.Pin(7, drop)), drag.drop(WidgetDragCarry.Pin(7), drop, infoFound = true))
        assertNull(drag.end())
    }

    @Test
    fun `a Widget Picker drop with target cells starts the add at the drop`() {
        val drag = IncomingWidgetDrag(held = null)

        assertEquals(DropStart.Add(WidgetAddTarget.Drop(drop)), drag.drop(WidgetDragCarry.Provider, drop, infoFound = true))
    }

    @Test
    fun `a drop without target cells releases the held Pin Request`() {
        val drag = IncomingWidgetDrag(held = 7)

        assertEquals(DropStart.NoAdd(release = 7), drag.drop(WidgetDragCarry.Pin(7), drop = null, infoFound = true))
        assertNull(drag.end())
    }

    @Test
    fun `an unreadable drop releases the Pin Request held since the drag started`() {
        val drag = IncomingWidgetDrag(held = 7)

        assertEquals(DropStart.NoAdd(release = 7), drag.drop(carry = null, drop, infoFound = false))
    }

    @Test
    fun `a pin drop whose provider info is gone releases the Pin Request and starts no add`() {
        val drag = IncomingWidgetDrag(held = 7)

        assertEquals(DropStart.NoAdd(release = 7), drag.drop(WidgetDragCarry.Pin(7), drop, infoFound = false))
    }

    @Test
    fun `a Widget Picker drop whose provider is gone starts no add and releases nothing`() {
        val drag = IncomingWidgetDrag(held = null)

        assertEquals(DropStart.NoAdd(release = null), drag.drop(WidgetDragCarry.Provider, drop, infoFound = false))
    }

    @Test
    fun `a drag that ends without a drop releases its held Pin Request once`() {
        val drag = IncomingWidgetDrag(held = 7)

        assertEquals(DropStart.NoAdd(release = 7), drag.end())
        assertNull(drag.end())
    }

    @Test
    fun `a second drop starts nothing more`() {
        val drag = IncomingWidgetDrag(held = 7)
        drag.drop(WidgetDragCarry.Pin(7), drop, infoFound = true)

        assertEquals(DropStart.NoAdd(release = null), drag.drop(WidgetDragCarry.Pin(7), drop, infoFound = true))
    }
}
