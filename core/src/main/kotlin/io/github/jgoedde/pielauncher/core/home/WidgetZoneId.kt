package io.github.jgoedde.pielauncher.core.home

/** Which Widget Zone of a Page a widget lives in. Stored by name in Room, so never rename. */
enum class WidgetZoneId {
    /** The Home Page's zone above the Pie Zone. */
    HomeUpper,

    /** The Home Page's two-row zone below the Pie Zone. */
    HomeLower,
    ;

    companion object {
        /**
         * The zones a tap-added widget tries, in order (PRD 7.4): the long-pressed zone [first],
         * then the others top to bottom. With no long-pressed zone (the App Drawer's filtered
         * Widget Picker) the Home Page from the upper zone down.
         */
        fun addOrder(first: WidgetZoneId?): List<WidgetZoneId> = listOfNotNull(first) + entries.filter { it != first }
    }
}

/** The Home Page is the leftmost Page. */
const val HOME_PAGE = 0
