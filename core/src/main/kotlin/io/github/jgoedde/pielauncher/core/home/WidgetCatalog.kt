package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.drawer.sortedForDrawer
import java.util.Locale
import kotlin.math.ceil

/**
 * The grid widgets are sized against: the largest Widget Zone's columns and rows, its cell size
 * and the gap between cells. All lengths share one unit (dp); the app maps provider px to dp.
 */
data class CatalogGrid(
    val columns: Int,
    val rows: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    val spacing: Float,
)

/**
 * A widget provider's size facts, mapped from `AppWidgetProviderInfo` in the app. Lengths in the
 * [CatalogGrid]'s unit (dp); 0 means "not declared".
 */
data class ProviderSizes(
    val minWidth: Float,
    val minHeight: Float,
    val minResizeWidth: Float = 0f,
    val minResizeHeight: Float = 0f,
    val maxResizeWidth: Float = 0f,
    val maxResizeHeight: Float = 0f,
    /** `targetCellWidth`/`targetCellHeight`, when declared. */
    val targetCells: CellSpan? = null,
    /** Whether `resizeMode` has the horizontal bit. */
    val resizesHorizontally: Boolean = false,
    /** Whether `resizeMode` has the vertical bit. */
    val resizesVertically: Boolean = false,
)

/** A widget's spans on a [CatalogGrid]. */
data class WidgetSpans(
    /** The size it is added at. */
    val default: CellSpan,
    /** The smallest size it can be resized to; equals [default] on an axis that does not resize. */
    val min: CellSpan,
    /** The largest size it can be resized to; equals [default] on an axis that does not resize. */
    val max: CellSpan,
    /** Whether a Resize Frame handle exists on the left and right edges. */
    val resizesHorizontally: Boolean,
    /** Whether a Resize Frame handle exists on the top and bottom edges. */
    val resizesVertically: Boolean,
    /** Whether even the min span exceeds the grid: shown greyed out, "Too large for this home screen". */
    val tooLarge: Boolean,
) {
    /** The span bounds a widget with these spans is added, dragged and resized within. */
    val bounds: SpanBounds get() = SpanBounds(default, min, max, resizesHorizontally, resizesVertically)
}

/** One entry of a widget's `OPTION_APPWIDGET_SIZES`, in the [CatalogGrid]'s unit (dp). */
data class ReportedSize(
    val width: Float,
    val height: Float,
)

/** A widget as the Widget Picker lists it: its [app], the texts it is found by, and the [widget] itself. */
data class CatalogWidget<A, W>(
    val app: A,
    val appName: String,
    val widget: W,
    val label: String,
    val description: String = "",
)

/** One app's header in the Widget Picker, with its [widgets] in display order. */
data class AppWidgets<A, W>(
    val app: A,
    val appName: String,
    val widgets: List<W>,
) {
    val count: Int get() = widgets.size
}

/** The Widget Catalog: pure functions over widget provider facts (Launcher3 behaviour). */
object WidgetCatalog {
    /** The spans of a provider of [sizes] on [grid], following Launcher3 `initSpans`. */
    fun spans(
        sizes: ProviderSizes,
        grid: CatalogGrid,
    ): WidgetSpans {
        val x =
            AxisSizes(sizes.minWidth, sizes.minResizeWidth, sizes.maxResizeWidth, sizes.resizesHorizontally)
                .ranges(grid.cellWidth, grid.spacing, grid.columns)
        val y =
            AxisSizes(sizes.minHeight, sizes.minResizeHeight, sizes.maxResizeHeight, sizes.resizesVertically)
                .ranges(grid.cellHeight, grid.spacing, grid.rows)
        val target = sizes.targetCells
        val targetFits = target != null && target.width in x.min..x.max && target.height in y.min..y.max
        val spanX = x.resolve(if (targetFits) target!!.width else x.fromMinSize, grid.columns)
        val spanY = y.resolve(if (targetFits) target!!.height else y.fromMinSize, grid.rows)
        return WidgetSpans(
            default = CellSpan(spanX.default, spanY.default),
            min = CellSpan(spanX.min, spanY.min),
            max = CellSpan(spanX.max, spanY.max),
            resizesHorizontally = spanX.resizes,
            resizesVertically = spanY.resizes,
            tooLarge = spanX.unclampedMin > grid.columns || spanY.unclampedMin > grid.rows,
        )
    }

    private class AxisSizes(
        val min: Float,
        val minResize: Float,
        val maxResize: Float,
        val resizes: Boolean,
    ) {
        fun ranges(
            cell: Float,
            spacing: Float,
            gridCells: Int,
        ): AxisRanges {
            val fromMinSize = span(min, cell, spacing)
            val minResizeSpan = span(minResize, cell, spacing)
            val maxResizeSpan = if (maxResize > 0f) span(maxResize, cell, spacing) else gridCells
            return AxisRanges(fromMinSize, minResizeSpan, maxOf(maxResizeSpan, minResizeSpan), resizes)
        }
    }

    /** One axis before the default is chosen: the span from the min size, and the resize range. */
    private class AxisRanges(
        val fromMinSize: Int,
        val min: Int,
        val max: Int,
        val resizeBit: Boolean,
    ) {
        /** The final spans for a chosen [default], fitted to a grid of [gridCells]. */
        fun resolve(
            default: Int,
            gridCells: Int,
        ): AxisSpans {
            val clamped = minOf(default, gridCells)
            if (!resizeBit) return AxisSpans(clamped, clamped, clamped, unclampedMin = default)
            val unclampedMin = minOf(default, min)
            val axisMin = minOf(unclampedMin, clamped)
            val axisMax = maxOf(minOf(max, gridCells), clamped)
            return AxisSpans(clamped, axisMin, axisMax, unclampedMin)
        }
    }

    private class AxisSpans(
        val default: Int,
        val min: Int,
        val max: Int,
        /** The min span before fitting to the grid; above the grid means "too large". */
        val unclampedMin: Int,
    ) {
        val resizes: Boolean get() = min < max
    }

    /**
     * [widgets] grouped by app: apps alphabetical by name, each app's widgets alphabetical by
     * label (case- and accent-insensitive collation for [locale]).
     */
    fun <A, W> group(
        widgets: List<CatalogWidget<A, W>>,
        locale: Locale,
    ): List<AppWidgets<A, W>> = grouped(widgets.map { Scored(it, 0) }, locale)

    /**
     * The Widget Picker search (Launcher3 `InMemoryWidgetsSearchAlgorithm`): [query] is lowercased
     * and split on whitespace. An app whose name holds every word is listed whole; otherwise a
     * widget is listed when each word is in its app name, label or description. Apps stay
     * alphabetical; within an app, widgets matching in their label rank above those matching only
     * in their description. A blank [query] lists everything, as [group].
     */
    fun <A, W> search(
        widgets: List<CatalogWidget<A, W>>,
        query: String,
        locale: Locale,
    ): List<AppWidgets<A, W>> {
        val words = query.lowercase(locale).split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return group(widgets, locale)
        val matches =
            widgets.groupBy { it.app }.values.flatMap { appWidgets ->
                val appName = appWidgets.first().appName.lowercase(locale)
                val appMatches = words.all { it in appName }
                appWidgets.mapNotNull { widget ->
                    val score = score(widget, words, locale)
                    when {
                        score != null -> Scored(widget, score)
                        appMatches -> Scored(widget, 0)
                        else -> null
                    }
                }
            }
        return grouped(matches, locale)
    }

    /** How well [widget] matches [words]: higher is better; null when some word is in none of its texts. */
    private fun score(
        widget: CatalogWidget<*, *>,
        words: List<String>,
        locale: Locale,
    ): Int? {
        val appName = widget.appName.lowercase(locale)
        val label = widget.label.lowercase(locale)
        val description = widget.description.lowercase(locale)
        var score = 0
        for (word in words) {
            val inLabel = word in label
            val inDescription = word in description
            if (!inLabel && !inDescription && word !in appName) return null
            if (inLabel) score += LABEL_SCORE
            if (inDescription) score += DESCRIPTION_SCORE
        }
        return score
    }

    private class Scored<A, W>(
        val widget: CatalogWidget<A, W>,
        val score: Int,
    )

    private fun <A, W> grouped(
        widgets: List<Scored<A, W>>,
        locale: Locale,
    ): List<AppWidgets<A, W>> =
        widgets
            .groupBy { it.widget.app }
            .values
            .map { appWidgets ->
                val first = appWidgets.first().widget
                AppWidgets(
                    app = first.app,
                    appName = first.appName,
                    widgets =
                        appWidgets
                            .sortedForDrawer(locale) { it.widget.label }
                            .sortedByDescending { it.score }
                            .map { it.widget.widget },
                )
            }.sortedForDrawer(locale) { it.appName }

    /**
     * The sizes to report to a widget at [span] on [grid] (Launcher3 `WidgetSizes`): the cells
     * plus the gaps between them, nothing subtracted. One entry, as only portrait is supported.
     */
    fun sizeList(
        span: CellSpan,
        grid: CatalogGrid,
    ): List<ReportedSize> =
        listOf(
            ReportedSize(
                width = span.width * grid.cellWidth + (span.width - 1) * grid.spacing,
                height = span.height * grid.cellHeight + (span.height - 1) * grid.spacing,
            ),
        )

    /**
     * Cells needed for [size] on a grid of [cell] with [spacing] between cells, as Launcher3
     * `getSpan`: n cells span `n * cell + (n - 1) * spacing`. At least one.
     */
    fun span(
        size: Float,
        cell: Float,
        spacing: Float,
    ): Int = maxOf(1, ceil((size + spacing) / (cell + spacing) - ROUNDING_TOLERANCE).toInt())

    private val WHITESPACE = Regex("\\s+")
    private const val LABEL_SCORE = 2
    private const val DESCRIPTION_SCORE = 1

    /** Cells; absorbs float error so a size of exactly n cells needs n, not n + 1. */
    private const val ROUNDING_TOLERANCE = 1e-4f
}
