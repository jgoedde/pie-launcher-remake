package io.github.jgoedde.pielauncher.core.pie

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** A position in window px, y downwards. */
data class Point(
    val x: Float,
    val y: Float,
)

/** A rectangle in window px. */
data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Pie sizes in px. Code constants by design (PRD 4), not Pie Config fields or settings; the
 * prototype's CSS px are taken as dp.
 */
data class PieMetrics(
    /** Ring radius: from the Pie centre to the middle of each Pie Item. */
    val radius: Float,
    /** How far inside the ring a Pie Item starts to catch the finger. */
    val captureRadius: Float,
    /** The innermost part of the Inner Circle; moving there closes an open Shortcut list. */
    val centreRadius: Float,
    /** Half a Pie Item's drawn size. */
    val itemRadius: Float,
    /** Space kept between the ring (or a Shortcut list card) and the window edges. */
    val margin: Float,
    /** Shortcut list card width. */
    val cardWidth: Float,
    /** One Shortcut row in the card. */
    val cardRowHeight: Float,
    /** Space above the first and below the last row of the card. */
    val cardPadding: Float,
    /** Space between a Pie Item's icon and its Shortcut list card. */
    val cardGap: Float,
) {
    /** The Inner Circle: once the finger is outside it, only its direction counts. */
    val innerRadius: Float get() = radius - captureRadius

    /** How far the ring reaches from the centre, margin included. */
    val extent: Float get() = radius + itemRadius + margin

    companion object {
        /** The default sizes for a display of [density] px per dp. */
        fun scaled(density: Float) =
            PieMetrics(
                radius = 120f * density,
                captureRadius = 30f * density,
                centreRadius = 26f * density,
                itemRadius = 28f * density,
                margin = 8f * density,
                cardWidth = 184f * density,
                cardRowHeight = 44f * density,
                cardPadding = 6f * density,
                cardGap = 10f * density,
            )
    }
}

/** What the finger is over. */
sealed interface PieHit {
    /** Inside the Inner Circle, outside the centre. */
    data object None : PieHit

    /** The innermost part of the Inner Circle. */
    data object Centre : PieHit

    data class Item(
        val index: Int,
    ) : PieHit
}

/** Ring layout, hit model and centre clamping (PRD 4.2, 4.3). */
object PieGeometry {
    /** Share of a sector the angle may drift from the hovered item before another one takes over. */
    const val HYSTERESIS = 0.65f

    /** Degrees clockwise from 12 o'clock. */
    fun itemAngle(
        index: Int,
        count: Int,
    ): Float = index * 360f / count

    fun itemPosition(
        centre: Point,
        index: Int,
        count: Int,
        metrics: PieMetrics,
    ): Point {
        val rad = Math.toRadians(itemAngle(index, count).toDouble())
        return Point(centre.x + metrics.radius * sin(rad).toFloat(), centre.y - metrics.radius * cos(rad).toFloat())
    }

    /**
     * Band model: outside the Inner Circle the line from [centre] to [finger] picks the item whose
     * sector it crosses, however far out the finger is. The [hovered] item holds until the angle
     * is more than [HYSTERESIS] of a sector away from it.
     */
    fun hitTest(
        centre: Point,
        finger: Point,
        count: Int,
        hovered: Int?,
        metrics: PieMetrics,
    ): PieHit {
        val dx = finger.x - centre.x
        val dy = finger.y - centre.y
        val distance = hypot(dx, dy)
        if (distance < metrics.centreRadius) return PieHit.Centre
        if (distance < metrics.innerRadius || count == 0) return PieHit.None
        val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        val sector = 360f / count
        if (hovered != null && hovered in 0 until count && abs(angleBetween(angle, itemAngle(hovered, count))) <= sector * HYSTERESIS) {
            return PieHit.Item(hovered)
        }
        return PieHit.Item(Math.floorMod((angle / sector).roundToInt(), count))
    }

    /**
     * The Pie centre for a touch-down at [finger]: moved just far enough that the ring stays
     * inside [window] (the window minus system-bar insets) with the margin to spare.
     */
    fun clampCentre(
        finger: Point,
        window: Bounds,
        metrics: PieMetrics,
    ): Point =
        Point(
            clamp(finger.x, window.left, window.right, metrics.extent),
            clamp(finger.y, window.top, window.bottom, metrics.extent),
        )

    private fun clamp(
        value: Float,
        min: Float,
        max: Float,
        extent: Float,
    ): Float = if (max - min < 2 * extent) (min + max) / 2 else value.coerceIn(min + extent, max - extent)

    /** Signed difference in degrees, in -180..180. */
    private fun angleBetween(
        a: Float,
        b: Float,
    ): Float = ((a - b) % 360f + 540f) % 360f - 180f
}
