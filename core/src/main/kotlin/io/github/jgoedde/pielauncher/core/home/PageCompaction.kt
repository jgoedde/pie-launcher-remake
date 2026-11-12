package io.github.jgoedde.pielauncher.core.home

/**
 * Removes empty non-Home Pages (PRD 3.1): the Pages that hold widgets keep their order and close
 * up to the right of the Home Page, which always stays. No reordering.
 */
object PageCompaction {
    /** For widgets on [pages] (widget id to Page), the widgets that move and their new Page. */
    fun moves(pages: Map<Int, Int>): Map<Int, Int> {
        val kept =
            pages.values
                .filter { it != HOME_PAGE }
                .distinct()
                .sorted()
        val renumbered = kept.withIndex().associate { (index, page) -> page to index + 1 }
        return pages.mapNotNull { (id, page) -> renumbered[page]?.takeIf { it != page }?.let { id to it } }.toMap()
    }

    /** How many Pages there are with widgets on [pages]: up to the rightmost one, and at least the Home Page. */
    fun pageCount(pages: Collection<Int>): Int = (pages.maxOrNull() ?: HOME_PAGE) + 1
}
