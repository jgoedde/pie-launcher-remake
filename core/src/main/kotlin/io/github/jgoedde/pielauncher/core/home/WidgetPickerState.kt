package io.github.jgoedde.pielauncher.core.home

import java.util.Locale

/** One app's header in the Widget Picker list as shown: its [widgets], listed below it only when [expanded]. */
data class PickerApp<A, W>(
    val app: A,
    val appName: String,
    val widgets: List<W>,
    val expanded: Boolean,
) {
    val count: Int get() = widgets.size
}

/**
 * What the Widget Picker shows (PRD 7.4): the search [query], the one expanded app header, and
 * the [selected] tile that shows the "Add" button. [onlyApp] filters the picker to one app (the
 * App Drawer's "Widgets" entry). Immutable: every tap returns a new state.
 */
data class WidgetPickerState<A, W>(
    val onlyApp: A? = null,
    val query: String = "",
    val expandedApp: A? = null,
    /** The tile showing the "Add" button, if any. */
    val selected: W? = null,
) {
    /**
     * The app headers to show for [widgets]: alphabetical, searched by [query] (Widget Catalog).
     * While searching, or when filtered to one app, every listed app is expanded; otherwise only
     * [expandedApp].
     */
    fun list(
        widgets: List<CatalogWidget<A, W>>,
        locale: Locale,
    ): List<PickerApp<A, W>> {
        val shown = if (onlyApp != null) widgets.filter { it.app == onlyApp } else widgets
        val allExpanded = onlyApp != null || query.isNotBlank()
        return WidgetCatalog.search(shown, query, locale).map { group ->
            PickerApp(group.app, group.appName, group.widgets, expanded = allExpanded || group.app == expandedApp)
        }
    }

    /** A new search; the "Add" button goes away. */
    fun search(query: String): WidgetPickerState<A, W> = copy(query = query, selected = null)

    /** Expands [app], collapsing the one open before, or collapses it when already open; the "Add" button goes away. */
    fun tapHeader(app: A): WidgetPickerState<A, W> = copy(expandedApp = if (expandedApp == app) null else app, selected = null)

    /** Shows the "Add" button on [widget], or hides it when it is already shown there. A [tooLarge] widget has none. */
    fun tapTile(
        widget: W,
        tooLarge: Boolean,
    ): WidgetPickerState<A, W> = copy(selected = if (tooLarge || selected == widget) null else widget)
}
