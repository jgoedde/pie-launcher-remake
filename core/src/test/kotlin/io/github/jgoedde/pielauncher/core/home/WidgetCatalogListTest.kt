package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WidgetCatalogListTest {
    private val clock = widget("clock", "Clock", "Analog", "A round clock face")
    private val digital = widget("clock", "Clock", "Digital", "Time in large digits")
    private val notes = widget("notes", "notes", "Note", "Pin a note to your home screen")
    private val weather = widget("weather", "Äther Weather", "Forecast", "Five days ahead")
    private val all = listOf(notes, digital, weather, clock)

    @Test
    fun `widgets are grouped under one entry per app with its widget count`() {
        val groups = WidgetCatalog.group(all, Locale.ENGLISH)

        val clockApp = groups.single { it.app == "clock" }
        assertEquals("Clock", clockApp.appName)
        assertEquals(2, clockApp.count)
    }

    @Test
    fun `apps are listed alphabetically, ignoring case and accents, and widgets by label`() {
        val groups = WidgetCatalog.group(all, Locale.ENGLISH)

        assertEquals(listOf("weather", "clock", "notes"), groups.map { it.app })
        assertEquals(listOf("Analog", "Digital"), groups[1].widgets)
    }

    @Test
    fun `a blank search lists every widget, grouped`() {
        assertEquals(WidgetCatalog.group(all, Locale.ENGLISH), WidgetCatalog.search(all, "  ", Locale.ENGLISH))
    }

    @Test
    fun `a search matching a widget label lists only that widget under its app`() {
        val result = WidgetCatalog.search(all, "DIGITAL", Locale.ENGLISH)

        assertEquals(listOf(AppWidgets("clock", "Clock", listOf("Digital"))), result)
    }

    @Test
    fun `a search matches widget descriptions`() {
        val result = WidgetCatalog.search(all, "ahead", Locale.ENGLISH)

        assertEquals(listOf(AppWidgets("weather", "Äther Weather", listOf("Forecast"))), result)
    }

    @Test
    fun `a search matching an app name lists all of that app's widgets`() {
        val radar = widget("weather", "Äther Weather", "Radar", "Rain map")

        val result = WidgetCatalog.search(all + radar, "weather", Locale.ENGLISH)

        assertEquals(listOf(AppWidgets("weather", "Äther Weather", listOf("Forecast", "Radar"))), result)
    }

    @Test
    fun `every search word must match the widget's app name, label or description`() {
        // "clock" is in both widgets' app name, "digits" only in Digital's description.
        val result = WidgetCatalog.search(all, "clock  digits", Locale.ENGLISH)

        assertEquals(listOf(AppWidgets("clock", "Clock", listOf("Digital"))), result)
    }

    @Test
    fun `a search matching nothing lists nothing`() {
        assertEquals(emptyList<AppWidgets<String, String>>(), WidgetCatalog.search(all, "calendar", Locale.ENGLISH))
    }

    @Test
    fun `within an app a label match ranks above a description match`() {
        // Digital only mentions time in its description; Timer has it in its label.
        val timer = widget("clock", "Clock", "Timer", "Count down")

        val result = WidgetCatalog.search(all + timer, "time", Locale.ENGLISH)

        assertEquals(listOf(AppWidgets("clock", "Clock", listOf("Timer", "Digital"))), result)
    }

    @Test
    fun `search results keep apps in alphabetical order`() {
        // notes matches by app name, Clock only through Alarm's description.
        val alarm = widget("clock", "Clock", "Alarm", "Wake up to a note")

        val result = WidgetCatalog.search(all + alarm, "note", Locale.ENGLISH)

        assertEquals(listOf("clock", "notes"), result.map { it.app })
        assertEquals(listOf("Alarm"), result[0].widgets)
    }

    private fun widget(
        app: String,
        appName: String,
        label: String,
        description: String,
    ) = CatalogWidget(app = app, appName = appName, widget = label, label = label, description = description)
}
