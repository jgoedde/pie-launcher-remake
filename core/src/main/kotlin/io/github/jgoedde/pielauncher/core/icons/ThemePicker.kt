package io.github.jgoedde.pielauncher.core.icons

/** The stored Themed Icons switch and Icon Shape key (PRD 8.4). */
data class IconSettings(
    val themedIcons: Boolean,
    val shapeKey: String?,
)

/** A setting change the system picker asks for. */
sealed interface IconSettingChange {
    data class ThemedIcons(
        val on: Boolean,
    ) : IconSettingChange

    data class Shape(
        val key: String,
    ) : IconSettingChange
}

/** A cursor's content: [columns] and [rows] of values in column order. */
data class PickerRows(
    val columns: List<String>,
    val rows: List<List<Any>>,
)

/**
 * The ThemePicker provider contract of "Wallpaper & style", as Launcher3's GridCustomizationsProxy
 * answers it: the provider passes each URI path here and turns the answer into a cursor or a
 * stored setting. Methods called on the provider (`get_preview`) answer null there.
 */
object ThemePicker {
    private const val BOOLEAN_VALUE = "boolean_value"
    private const val SHAPE_KEY = "shape_key"

    /** The rows for a query on [path]; null for every path the launcher does not offer. */
    fun query(
        path: String?,
        settings: IconSettings,
    ): PickerRows? =
        when (path) {
            "/icon_themed", "/get_icon_themed" -> PickerRows(listOf(BOOLEAN_VALUE), listOf(listOf(if (settings.themedIcons) 1 else 0)))
            "/shape_options" -> shapeOptions(IconShapes.byKey(settings.shapeKey))
            else -> null
        }

    /**
     * The setting an update on [path] with [values] asks for; null when it asks for nothing the
     * launcher offers or carries no valid value, and the provider then reports no row changed.
     */
    fun update(
        path: String?,
        values: Map<String, Any?>,
    ): IconSettingChange? =
        when (path) {
            "/icon_themed", "/set_icon_themed" -> values[BOOLEAN_VALUE].asBoolean()?.let(IconSettingChange::ThemedIcons)
            "/shape" ->
                IconShapes.all
                    .firstOrNull { it.key == values[SHAPE_KEY] }
                    ?.let { IconSettingChange.Shape(it.key) }
            else -> null
        }

    /** What ContentValues.getAsBoolean accepts: a Boolean, a number (non-zero is true) or "true"/"false"/"1"/"0". */
    private fun Any?.asBoolean(): Boolean? =
        when (this) {
            is Boolean -> this
            is Number -> toLong() != 0L
            is String -> toBooleanStrictOrNull() ?: toLongOrNull()?.let { it != 0L }
            else -> null
        }

    /** `is_default` is the string "true"/"false", as the picker compares it. */
    private fun shapeOptions(current: IconShape) =
        PickerRows(
            listOf(SHAPE_KEY, "shape_title", "path", "is_default"),
            IconShapes.all.map { listOf(it.key, it.title, it.path, (it == current).toString()) },
        )
}
