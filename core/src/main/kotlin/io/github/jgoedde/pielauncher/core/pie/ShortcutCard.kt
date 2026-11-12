package io.github.jgoedde.pielauncher.core.pie

/**
 * Where an app item's Shortcut list card opens (PRD 4.6): beside the icon, on each axis away from
 * the Pie centre, unless only the other side has room. A card too big for both sides is pushed
 * inside the window margin.
 */
object ShortcutCard {
    fun place(
        item: Point,
        centre: Point,
        rows: Int,
        window: Bounds,
        metrics: PieMetrics,
    ): Bounds {
        val width = metrics.cardWidth
        val height = rows * metrics.cardRowHeight + 2 * metrics.cardPadding
        val reach = metrics.itemRadius + metrics.cardGap
        val left =
            side(
                after = item.x + reach,
                before = item.x - reach - width,
                preferAfter = item.x >= centre.x,
                size = width,
                min = window.left + metrics.margin,
                max = window.right - metrics.margin,
            )
        val top =
            side(
                after = item.y - metrics.itemRadius,
                before = item.y + metrics.itemRadius - height,
                preferAfter = item.y >= centre.y,
                size = height,
                min = window.top + metrics.margin,
                max = window.bottom - metrics.margin,
            )
        return Bounds(left, top, left + width, top + height)
    }

    /** The row under [finger], or null when it is not on [card]. */
    fun rowAt(
        card: Bounds,
        rows: Int,
        finger: Point,
        metrics: PieMetrics,
    ): Int? {
        if (rows == 0 || finger.x !in card.left..card.right || finger.y !in card.top..card.bottom) return null
        return ((finger.y - card.top - metrics.cardPadding) / metrics.cardRowHeight).toInt().coerceIn(0, rows - 1)
    }

    /** The start of a span of [size]: [after] or [before] the icon, whichever fits, preferring one. */
    private fun side(
        after: Float,
        before: Float,
        preferAfter: Boolean,
        size: Float,
        min: Float,
        max: Float,
    ): Float {
        val fitsAfter = after + size <= max
        val fitsBefore = before >= min
        val start =
            when {
                preferAfter && fitsAfter -> after
                !preferAfter && fitsBefore -> before
                fitsAfter -> after
                fitsBefore -> before
                preferAfter -> after
                else -> before
            }
        return start.coerceAtMost(max - size).coerceAtLeast(min)
    }
}
