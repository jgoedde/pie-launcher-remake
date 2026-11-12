package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WidgetPickerStateTest {
    private val analog = widget("clock", "Clock", "Analog")
    private val digital = widget("clock", "Clock", "Digital")
    private val note = widget("notes", "Notes", "Note")
    private val all = listOf(analog, digital, note)

    @Test
    fun `the picker opens with every app collapsed`() {
        val apps = WidgetPickerState<String, String>().list(all, Locale.ENGLISH)

        assertEquals(listOf("clock" to false, "notes" to false), apps.map { it.app to it.expanded })
        assertEquals(2, apps.first().count)
    }

    @Test
    fun `tapping an app header expands it and collapses the one open before`() {
        val state = WidgetPickerState<String, String>().tapHeader("clock").tapHeader("notes")

        assertEquals(listOf("clock" to false, "notes" to true), state.list(all, Locale.ENGLISH).map { it.app to it.expanded })
    }

    @Test
    fun `tapping an expanded app header collapses it`() {
        val state = WidgetPickerState<String, String>().tapHeader("clock").tapHeader("clock")

        assertEquals(listOf(false, false), state.list(all, Locale.ENGLISH).map { it.expanded })
    }

    @Test
    fun `search results are listed with every matching app expanded`() {
        val apps = WidgetPickerState<String, String>().search("o").list(all, Locale.ENGLISH)

        assertEquals(listOf("clock" to true, "notes" to true), apps.map { it.app to it.expanded })
    }

    @Test
    fun `a picker filtered to one app lists only that app, expanded`() {
        val apps = WidgetPickerState<String, String>(onlyApp = "notes").list(all, Locale.ENGLISH)

        assertEquals(listOf(PickerApp("notes", "Notes", listOf("Note"), expanded = true)), apps)
    }

    @Test
    fun `a filtered picker still searches within its app`() {
        val apps = WidgetPickerState<String, String>(onlyApp = "clock").search("digi").list(all, Locale.ENGLISH)

        assertEquals(listOf(PickerApp("clock", "Clock", listOf("Digital"), expanded = true)), apps)
    }

    @Test
    fun `tapping a widget tile shows its Add button, and tapping it again hides it`() {
        val shown = WidgetPickerState<String, String>().tapTile("Analog", tooLarge = false)

        assertEquals("Analog", shown.selected)
        assertNull(shown.tapTile("Analog", tooLarge = false).selected)
    }

    @Test
    fun `tapping another tile moves the Add button there`() {
        val state = WidgetPickerState<String, String>().tapTile("Analog", tooLarge = false).tapTile("Digital", tooLarge = false)

        assertEquals("Digital", state.selected)
    }

    @Test
    fun `a widget too large for this home screen gets no Add button`() {
        val state = WidgetPickerState<String, String>().tapTile("Analog", tooLarge = false).tapTile("Huge", tooLarge = true)

        assertNull(state.selected)
    }

    @Test
    fun `changing the search hides the Add button`() {
        val state = WidgetPickerState<String, String>().tapTile("Analog", tooLarge = false).search("clock")

        assertNull(state.selected)
    }

    @Test
    fun `collapsing the app hides the Add button of its widget`() {
        val state = WidgetPickerState<String, String>().tapHeader("clock").tapTile("Analog", tooLarge = false).tapHeader("clock")

        assertNull(state.selected)
    }

    private fun widget(
        app: String,
        appName: String,
        label: String,
    ) = CatalogWidget(app = app, appName = appName, widget = label, label = label)
}
