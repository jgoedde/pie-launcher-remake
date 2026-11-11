package io.github.jgoedde.pielauncher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePickerTest {
    @Test
    fun `icon_themed answers one row with boolean_value 1 or 0`() {
        assertEquals(
            PickerRows(listOf("boolean_value"), listOf(listOf(1))),
            ThemePicker.query("/icon_themed", IconSettings(themedIcons = true, shapeKey = null)),
        )
        assertEquals(
            PickerRows(listOf("boolean_value"), listOf(listOf(0))),
            ThemePicker.query("/icon_themed", IconSettings(themedIcons = false, shapeKey = null)),
        )
        assertEquals(
            PickerRows(listOf("boolean_value"), listOf(listOf(1))),
            ThemePicker.query("/get_icon_themed", IconSettings(themedIcons = true, shapeKey = null)),
        )
    }

    @Test
    fun `shape_options lists every Icon Shape with the stored one as the only default`() {
        val rows = ThemePicker.query("/shape_options", IconSettings(themedIcons = true, shapeKey = "arch"))!!

        assertEquals(listOf("shape_key", "shape_title", "path", "is_default"), rows.columns)
        assertEquals(
            listOf(
                listOf("circle", "Circle", "false"),
                listOf("square", "Square", "false"),
                listOf("four_sided_cookie", "4-sided cookie", "false"),
                listOf("seven_sided_cookie", "7-sided cookie", "false"),
                listOf("arch", "Arch", "true"),
            ),
            rows.rows.map { listOf(it[0], it[1], it[3]) },
        )
        assertEquals("M50 0A50 50,0,1,1,50 100A50 50,0,1,1,50 0", rows.rows[0][2])
    }

    @Test
    fun `shape_options marks the circle as default when no known shape is stored`() {
        val rows = ThemePicker.query("/shape_options", IconSettings(themedIcons = true, shapeKey = "sunny"))!!

        assertEquals(listOf("true", "false", "false", "false", "false"), rows.rows.map { it[3] })
    }

    @Test
    fun `grid queries and unknown paths answer null, so the picker hides those sections`() {
        val settings = IconSettings(themedIcons = true, shapeKey = null)

        assertNull(ThemePicker.query("/list_options", settings))
        assertNull(ThemePicker.query("/default_grid", settings))
        assertNull(ThemePicker.query(null, settings))
    }

    @Test
    fun `updating icon_themed switches Themed Icons, whatever type the picker sends`() {
        assertEquals(IconSettingChange.ThemedIcons(true), ThemePicker.update("/icon_themed", mapOf("boolean_value" to true)))
        assertEquals(IconSettingChange.ThemedIcons(false), ThemePicker.update("/icon_themed", mapOf("boolean_value" to false)))
        assertEquals(IconSettingChange.ThemedIcons(true), ThemePicker.update("/icon_themed", mapOf("boolean_value" to 1)))
        assertEquals(IconSettingChange.ThemedIcons(false), ThemePicker.update("/icon_themed", mapOf("boolean_value" to 0)))
        assertEquals(IconSettingChange.ThemedIcons(true), ThemePicker.update("/icon_themed", mapOf("boolean_value" to "true")))
        assertEquals(IconSettingChange.ThemedIcons(true), ThemePicker.update("/icon_themed", mapOf("boolean_value" to "1")))
    }

    @Test
    fun `updating icon_themed without a boolean_value changes nothing`() {
        assertNull(ThemePicker.update("/icon_themed", emptyMap()))
        assertNull(ThemePicker.update("/icon_themed", mapOf("boolean_value" to "maybe")))
    }

    @Test
    fun `updating shape with a known shape_key picks that Icon Shape`() {
        assertEquals(
            IconSettingChange.Shape("four_sided_cookie"),
            ThemePicker.update("/shape", mapOf("shape_key" to "four_sided_cookie")),
        )
    }

    @Test
    fun `updating shape with an unknown or missing shape_key changes nothing`() {
        assertNull(ThemePicker.update("/shape", mapOf("shape_key" to "sunny")))
        assertNull(ThemePicker.update("/shape", emptyMap()))
    }

    @Test
    fun `updating a grid or unknown path changes nothing`() {
        assertNull(ThemePicker.update("/default_grid", mapOf("name" to "4x4", "shape_key" to "arch")))
        assertNull(ThemePicker.update(null, mapOf("boolean_value" to true)))
    }
}
