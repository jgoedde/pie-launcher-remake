package io.github.jgoedde.pielauncher.core.home

/** One Widget Zone of one Page. */
data class PageZone(
    val page: Int,
    val zone: WidgetZoneId,
)

/** Where [PageLayouts.add] put a widget: its [zone] (null when rejected) and the [result] on that Page's layout. */
data class PagePlacement(
    val zone: PageZone?,
    val result: LayoutResult,
)

/**
 * The Widget Layouts of all Pages (PRD 3.1), indexed by Page: the Home Page first, then the
 * others left to right. [newPage] is the empty layout of a Page appended on the right.
 */
class PageLayouts(
    val pages: List<WidgetLayout>,
    val newPage: WidgetLayout,
) {
    /**
     * Tap-add or widget Pin Request (PRD 7.4, 7.6): places widget [id] at the first free spot of
     * the long-pressed zone [first], else of the Home Page (upper zone first), else of the other
     * Pages left to right. With no room on any Page, a Page is appended for it.
     * [Rejection.TooLarge] when it fits not even an empty Page.
     */
    fun add(
        id: Int,
        bounds: SpanBounds,
        first: PageZone?,
    ): PagePlacement {
        val order =
            listOfNotNull(first) +
                pages.indices.flatMap { page -> WidgetZoneId.of(page).map { PageZone(page, it) } }.filter { it != first }
        for (slot in order) {
            val layout = pages.getOrNull(slot.page) ?: continue
            val result = layout.add(id, bounds, listOf(slot.zone))
            if (result is LayoutResult.Changed) return PagePlacement(slot, result)
        }
        val appended = newPage.add(id, bounds, WidgetZoneId.of(pages.size))
        return PagePlacement((appended as? LayoutResult.Changed)?.let { PageZone(pages.size, it.widget.zone) }, appended)
    }

    companion object {
        /** The layouts of [count] Pages (at least the Home Page) on [zones], each with [widgetsOf] that Page. */
        fun of(
            zones: HomeZones,
            count: Int,
            widgetsOf: (page: Int) -> List<LaidOutWidget>,
        ): PageLayouts =
            PageLayouts(
                (0 until maxOf(count, 1)).map { page -> WidgetLayout(zones.grids(page), widgetsOf(page)) },
                WidgetLayout(zones.grids(maxOf(count, 1)), emptyList()),
            )
    }
}
