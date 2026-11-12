package io.github.jgoedde.pielauncher.core.home

/** Which Widget Zone of a Page a widget lives in. Stored by name in Room, so never rename. */
enum class WidgetZoneId {
    /** The Home Page's zone above the Pie Zone. */
    HomeUpper,

    /** The Home Page's two-row zone below the Pie Zone. */
    HomeLower,

    /** The one zone of every other Page, over its full height. */
    Page,
    ;

    companion object {
        /** The Home Page's zones, top to bottom. */
        val HOME: List<WidgetZoneId> = listOf(HomeUpper, HomeLower)

        /** The zones of [page], top to bottom. */
        fun of(page: Int): List<WidgetZoneId> = if (page == HOME_PAGE) HOME else listOf(Page)
    }
}
