package io.github.jgoedde.pielauncher.core.home

/** The area a popup may cover, in window px: the window minus system bars and margins. */
data class PopupBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** A popup's top-left corner in window px. */
data class PopupPosition(
    val x: Int,
    val y: Int,
)

/**
 * Places the empty-space popup at the touch point the way Launcher3 does (`Launcher.showDefaultOptions`
 * and `ArrowPopup.orientAboutObject`, no arrow). The anchor is a rect of ±[ANCHOR_RADIUS_PX] around
 * the touch. The popup starts at the anchor's left edge, or ends at its right edge when that runs
 * past [PopupBounds.right]. It sits above the anchor, or below it when there is no room above.
 * A popup that fits neither way is clamped into the bounds.
 */
object TouchPopup {
    /** Half the side of Launcher3's anchor rect around the touch, in raw px as in Launcher3. */
    const val ANCHOR_RADIUS_PX = 20

    fun place(
        touchX: Int,
        touchY: Int,
        width: Int,
        height: Int,
        bounds: PopupBounds,
    ): PopupPosition {
        val startAligned = touchX - ANCHOR_RADIUS_PX
        val x = if (startAligned + width <= bounds.right) startAligned else touchX + ANCHOR_RADIUS_PX - width
        val above = touchY - ANCHOR_RADIUS_PX - height
        val y = if (above >= bounds.top) above else touchY + ANCHOR_RADIUS_PX
        return PopupPosition(
            x.coerceIn(bounds.left, maxOf(bounds.left, bounds.right - width)),
            y.coerceIn(bounds.top, maxOf(bounds.top, bounds.bottom - height)),
        )
    }
}
