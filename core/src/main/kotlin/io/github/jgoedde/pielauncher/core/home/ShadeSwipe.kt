package io.github.jgoedde.pielauncher.core.home

import kotlin.math.abs

/**
 * Whether a vertical drag on the home surface (outside the Pie Zone) pulls down the notification
 * shade, decided once when the drag crosses touch slop (Launcher3 `StatusBarTouchController`).
 */
object ShadeSwipe {
    /**
     * [dx] and [dy] are the total travel in px since touch-down, y positive downwards. The drag
     * must be past [touchSlop] downwards and more downward than sideways.
     */
    fun opensShade(
        dx: Float,
        dy: Float,
        touchSlop: Float,
    ): Boolean = dy > touchSlop && dy > abs(dx)
}
