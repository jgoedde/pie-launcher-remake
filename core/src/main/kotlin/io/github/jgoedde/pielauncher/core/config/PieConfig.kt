package io.github.jgoedde.pielauncher.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Pie Config (`spec/pie-config.schema.json`): all Layers, their Pie Items, and which Layer is
 * the Base Layer. Optional fields stay null when the file leaves them out, so the normalized file
 * keeps the same content as the imported one.
 */
@Serializable
data class PieConfig(
    @SerialName("\$schema") val schema: String? = null,
    val version: Int,
    val baseLayer: String,
    val layers: List<Layer>,
) {
    val base: Layer get() = layers.first { it.id == baseLayer }

    fun layer(id: String): Layer? = layers.firstOrNull { it.id == id }
}

@Serializable
data class Layer(
    val id: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val items: List<PieItem>,
) {
    /** [color] or the schema default `primary`. */
    val effectiveColor: PieColor get() = color?.let(PieColor::parse) ?: PieColor.Role.Primary

    /** [icon] or the schema default `layers`. */
    val effectiveIcon: String get() = icon ?: DEFAULT_ICON

    companion object {
        const val DEFAULT_ICON = "layers"
    }
}

/** One selectable entry in a Layer. */
@Serializable
sealed interface PieItem {
    /** Without [activity] the launcher starts what `getLaunchIntentForPackage` returns. */
    @Serializable
    @SerialName("app")
    data class App(
        @SerialName("package") val packageName: String,
        val activity: String? = null,
    ) : PieItem

    /** A Layer Switch to the Layer [layer]. */
    @Serializable
    @SerialName("layer")
    data class LayerSwitch(
        val layer: String,
    ) : PieItem

    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        @SerialName("package") val packageName: String,
        val shortcutId: String,
    ) : PieItem
}

/** A Layer colour: a fixed `#RRGGBB` or a Monet role that follows the wallpaper. */
sealed interface PieColor {
    /** [rgb] is `0xRRGGBB`. */
    data class Hex(
        val rgb: Int,
    ) : PieColor

    enum class Role(
        val key: String,
    ) : PieColor {
        Primary("primary"),
        Secondary("secondary"),
        Tertiary("tertiary"),
    }

    companion object {
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

        /** Null when [value] is neither `#RRGGBB` nor a role name. */
        fun parse(value: String): PieColor? =
            Role.entries.firstOrNull { it.key == value }
                ?: if (HEX.matches(value)) Hex(value.substring(1).toInt(16)) else null
    }
}
