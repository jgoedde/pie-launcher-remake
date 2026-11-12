package io.github.jgoedde.pielauncher.widgets

/** Which Widget Zone of a Page a widget lives in. Stored by name in Room, so never rename. */
enum class WidgetZoneId {
    /** The Home Page's zone above the Pie Zone. */
    HomeUpper,

    /** The Home Page's one-row zone below the Pie Zone. */
    HomeLower,
}

/** The Home Page is the leftmost Page. */
const val HOME_PAGE = 0
