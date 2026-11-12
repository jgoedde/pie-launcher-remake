package io.github.jgoedde.pielauncher.core.home

/** The dot grid that marks the Pie Zone at rest: dots one step apart, centred in the area. */
object DotGrid {
    /** Dot centres, column by column, as (x, y) in the area's px. */
    fun centres(
        width: Float,
        height: Float,
        step: Float,
    ): List<Pair<Float, Float>> {
        val xs = axis(width, step)
        val ys = axis(height, step)
        return xs.flatMap { x -> ys.map { y -> x to y } }
    }

    private fun axis(
        length: Float,
        step: Float,
    ): List<Float> {
        val count = (length / step).toInt()
        val start = (length - (count - 1) * step) / 2
        return List(count) { start + it * step }
    }
}
