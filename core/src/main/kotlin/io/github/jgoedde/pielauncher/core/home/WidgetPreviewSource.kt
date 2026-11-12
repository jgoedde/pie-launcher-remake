package io.github.jgoedde.pielauncher.core.home

/** Which previews a widget has, mapped from its provider (and, for a Pin Request, the request) in the app. */
data class PreviewFacts(
    /** A Pin Request carried its own preview (`EXTRA_APPWIDGET_PREVIEW`). */
    val appSupplied: Boolean = false,
    /** The framework returned a generated preview (only when the launcher is bind-allow-listed). */
    val generated: Boolean = false,
    /** The provider declares a `previewLayout`. */
    val layout: Boolean = false,
    /** The provider declares a `previewImage`. */
    val image: Boolean = false,
)

/** Where a widget's preview in the Widget Picker or the pin sheet comes from (Launcher3 `generatePreviewInfoBg`). */
enum class WidgetPreviewSource {
    AppSupplied,
    Generated,
    Layout,
    Image,

    /** A rounded rect with cell divider lines at the widget's default span. */
    Placeholder,
    ;

    companion object {
        /** The first preview [facts] has, in order: app-supplied, generated, layout, image, else a placeholder. */
        fun of(facts: PreviewFacts): WidgetPreviewSource =
            when {
                facts.appSupplied -> AppSupplied
                facts.generated -> Generated
                facts.layout -> Layout
                facts.image -> Image
                else -> Placeholder
            }
    }
}
