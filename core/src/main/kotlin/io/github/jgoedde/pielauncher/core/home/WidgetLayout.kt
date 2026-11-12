package io.github.jgoedde.pielauncher.core.home

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

/** A widget's size in cells. */
data class CellSpan(
    val width: Int,
    val height: Int,
)

/** A block of cells in one Widget Zone: top-left cell plus size in cells. */
data class CellRect(
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
) {
    val span: CellSpan get() = CellSpan(width, height)

    fun overlaps(other: CellRect): Boolean =
        column < other.column + other.width &&
            other.column < column + width &&
            row < other.row + other.height &&
            other.row < row + height
}

/**
 * A widget's span bounds (from its provider, via the Widget Catalog): the span it is added at,
 * the span range it may be resized or shrunk within, and the axes it resizes on.
 */
data class SpanBounds(
    val default: CellSpan,
    val min: CellSpan,
    val max: CellSpan,
    val resizesHorizontally: Boolean,
    val resizesVertically: Boolean,
) {
    companion object {
        /** Bounds that pin a widget to [span]: no resizing, no shrinking. */
        fun fixed(span: CellSpan) = SpanBounds(span, span, span, resizesHorizontally = false, resizesVertically = false)
    }
}

/** One widget of a Page: its id, Widget Zone, cells and span bounds. */
data class LaidOutWidget(
    val id: Int,
    val zone: WidgetZoneId,
    val cells: CellRect,
    val bounds: SpanBounds,
)

/** A widget being dragged: a placed one picked up ([WidgetLayout.pickUp]) or a new one from the Widget Picker. */
data class DraggedWidget(
    val id: Int,
    val span: CellSpan,
    val bounds: SpanBounds,
)

/** Where a dragged widget is over. */
sealed interface DragTarget {
    /** The cell under the dragged widget's top-left corner. */
    data class Cell(
        val zone: WidgetZoneId,
        val column: Int,
        val row: Int,
    ) : DragTarget

    data object PieZone : DragTarget
}

/** The Resize Frame edge whose handle is dragged. */
enum class ResizeEdge { Left, Top, Right, Bottom }

/** Why a layout operation found no solution. */
enum class Rejection {
    /** Nothing fits where asked ("No room on this home screen"). */
    NoRoom,

    /** Even the min span fits no Widget Zone of the Page ("Too large for this home screen"). */
    TooLarge,

    /** The target is the Pie Zone, which never holds widgets. */
    PieZone,
}

/** The outcome of a [WidgetLayout] operation. */
sealed interface LayoutResult {
    /** The new [layout]; [widget] is the widget operated on, [moved] the neighbours reflow moved. */
    data class Changed(
        val layout: WidgetLayout,
        val widget: LaidOutWidget,
        val moved: List<LaidOutWidget> = emptyList(),
    ) : LayoutResult

    data class Rejected(
        val reason: Rejection,
    ) : LayoutResult

    /** The operation leaves the layout as it is (a resize that is bounded or blocked, an unknown id). */
    data object Unchanged : LayoutResult
}

/**
 * The widgets of one Page on the cell grids of its Widget Zones (PRD 7, ADR 0007), with
 * Launcher3's reflow: moving or resizing pushes neighbours aside, only ever within one zone.
 * Immutable: every operation returns a new layout, a rejection, or no change.
 */
class WidgetLayout(
    val grids: Map<WidgetZoneId, CellGrid>,
    val widgets: List<LaidOutWidget>,
) {
    fun widget(id: Int): LaidOutWidget? = widgets.firstOrNull { it.id == id }

    fun inZone(zone: WidgetZoneId): List<LaidOutWidget> = widgets.filter { it.zone == zone }

    /** Where [widget] lies inside its zone, in px: its cells plus the gaps between them. */
    fun boundsOf(widget: LaidOutWidget): CellBounds = grid(widget.zone).bounds(widget.cells)

    /** The widget under the zone-relative point ([x], [y]) in [zone]; null on empty space and in gaps between widgets. */
    fun widgetAt(
        zone: WidgetZoneId,
        x: Float,
        y: Float,
    ): LaidOutWidget? =
        inZone(zone).firstOrNull { widget ->
            val bounds = boundsOf(widget)
            x >= bounds.left && x < bounds.left + bounds.width && y >= bounds.top && y < bounds.top + bounds.height
        }

    /**
     * The cell of [zone] under the zone-relative point ([x], [y]), a gap counting to the cell
     * before it; null outside the zone's rows and columns.
     */
    fun cellAt(
        zone: WidgetZoneId,
        x: Float,
        y: Float,
    ): DragTarget.Cell? {
        val grid = grids[zone] ?: return null
        if (grid.cellWidth <= 0f || grid.cellHeight <= 0f || x < 0f || y < grid.verticalPadding) return null
        val column = floor(x / (grid.cellWidth + grid.gap)).toInt()
        val row = floor((y - grid.verticalPadding) / (grid.cellHeight + grid.gap)).toInt()
        val insideLastCell =
            x < grid.columns * (grid.cellWidth + grid.gap) - grid.gap &&
                y < grid.verticalPadding + grid.rows * (grid.cellHeight + grid.gap) - grid.gap
        if (column !in 0 until grid.columns || row !in 0 until grid.rows || !insideLastCell) return null
        return DragTarget.Cell(zone, column, row)
    }

    /**
     * Tap-add or Pin Request: places widget [id] at its default span in the first of [zones] (in
     * order) with room, at the first free spot row by row, left to right. Like Launcher3, the
     * default span is cut to the zone's grid where the min span allows; it never shrinks further
     * to squeeze into a gap. [Rejection.TooLarge] when the min span fits no zone of the Page.
     */
    fun add(
        id: Int,
        bounds: SpanBounds,
        zones: List<WidgetZoneId>,
    ): LayoutResult {
        if (grids.values.none { bounds.min.fitsIn(it) }) return LayoutResult.Rejected(Rejection.TooLarge)
        for (zone in zones) {
            val grid = grids[zone] ?: continue
            if (!bounds.min.fitsIn(grid)) continue
            val cells = firstFreeSpot(zone, grid, bounds.default.cutTo(grid, bounds.min)) ?: continue
            val placed = LaidOutWidget(id, zone, cells, bounds)
            return LayoutResult.Changed(WidgetLayout(grids, widgets + placed), placed)
        }
        return LayoutResult.Rejected(Rejection.NoRoom)
    }

    /** Picks up the placed widget [id] for a move, at its current span. */
    fun pickUp(id: Int): DraggedWidget? = widget(id)?.let { DraggedWidget(it.id, it.cells.span, it.bounds) }

    /**
     * The reorder preview for [dragged] over [target] (Launcher3 `calculateReorder`): the push
     * solution if it keeps at least as large a span as the closest empty space, else that closest
     * empty space (nothing moves), each shrinking the dragged widget one cell at a time, X then
     * Y, down to its min span. A widget dropped back on its own cells is the closest empty space.
     * The [LayoutResult.Changed.layout] shows the dragged widget at its spot and the moved
     * neighbours at theirs. Rejected on the Pie Zone, and when nothing fits even at the min span.
     */
    fun dragOver(
        dragged: DraggedWidget,
        target: DragTarget,
    ): LayoutResult {
        if (target !is DragTarget.Cell) return LayoutResult.Rejected(Rejection.PieZone)
        val grid = grids[target.zone] ?: return LayoutResult.Rejected(Rejection.NoRoom)
        val zone = ZoneCells(grid, inZone(target.zone).filter { it.id != dragged.id }.associate { it.id to it.cells })
        val min = CellSpan(minOf(dragged.bounds.min.width, dragged.span.width), minOf(dragged.bounds.min.height, dragged.span.height))
        val span = dragged.span.cutTo(grid, min)
        val origin = widget(dragged.id)?.takeIf { it.zone == target.zone }?.cells
        val push = zone.pushSolution(target.column, target.row, span, min, origin)
        val empty = zone.closestEmptySpace(target.column, target.row, span, min)
        val solution =
            when {
                push != null && (empty == null || push.cells.area >= empty.cells.area) -> push
                else -> empty
            } ?: return LayoutResult.Rejected(Rejection.NoRoom)
        return commit(LaidOutWidget(dragged.id, target.zone, solution.cells, dragged.bounds), solution.others)
    }

    /** Commits the drop of [dragged] on [target]: the same solution [dragOver] shows for that target. */
    fun drop(
        dragged: DraggedWidget,
        target: DragTarget,
    ): LayoutResult = dragOver(dragged, target)

    /**
     * Resizes widget [id] by dragging its [edge] handle [deltaCells] cells (positive is right or
     * down), measured from this layout. The span changes by one cell once the delta passes
     * [RESIZE_THRESHOLD] of a cell, then by one more per further cell. It stays within the span
     * bounds and the zone edges, on the axes the widget resizes on, and pushes neighbours in the
     * resize direction; where they can't move, it grows only as far as they can. A widget below
     * its min span can always grow back. [LayoutResult.Unchanged] when nothing changes.
     */
    fun resize(
        id: Int,
        edge: ResizeEdge,
        deltaCells: Float,
    ): LayoutResult {
        val widget = widget(id) ?: return LayoutResult.Unchanged
        val grid = grid(widget.zone)
        val horizontal = edge == ResizeEdge.Left || edge == ResizeEdge.Right
        val cells = widget.cells
        val current = if (horizontal) cells.width else cells.height
        val min = if (horizontal) widget.bounds.min.width else widget.bounds.min.height
        val max = if (horizontal) widget.bounds.max.width else widget.bounds.max.height
        val gridSize = if (horizontal) grid.columns else grid.rows
        val resizes = if (horizontal) widget.bounds.resizesHorizontally else widget.bounds.resizesVertically
        if (!(resizes && min < max && min < gridSize) && current >= min) return LayoutResult.Unchanged

        val growsForward = edge == ResizeEdge.Right || edge == ResizeEdge.Bottom
        val growth = spanIncrement(deltaCells) * if (growsForward) 1 else -1
        val start = if (horizontal) cells.column else cells.row
        val end = start + current
        val roomToEdge = if (growsForward) gridSize - start else end
        val wanted = (current + growth).coerceAtMost(maxOf(max, min, current)).coerceAtLeast(minOf(min, current)).coerceAtMost(roomToEdge)
        if (wanted == current) return LayoutResult.Unchanged

        val zone = ZoneCells(grid, inZone(widget.zone).filter { it.id != id }.associate { it.id to it.cells })
        val direction =
            when (edge) {
                ResizeEdge.Left -> Direction(-1, 0)
                ResizeEdge.Right -> Direction(1, 0)
                ResizeEdge.Top -> Direction(0, -1)
                ResizeEdge.Bottom -> Direction(0, 1)
            }
        val step = if (wanted > current) -1 else 1
        var size = wanted
        while (size != current) {
            val newStart = if (growsForward) start else end - size
            val resized =
                if (horizontal) cells.copy(column = newStart, width = size) else cells.copy(row = newStart, height = size)
            zone.makeRoom(resized, direction)?.let { others -> return commit(widget.copy(cells = resized), others) }
            size += step
        }
        return LayoutResult.Unchanged
    }

    /** Takes widget [id] out; [LayoutResult.Changed.widget] is what [undo] puts back. */
    fun remove(id: Int): LayoutResult {
        val widget = widget(id) ?: return LayoutResult.Unchanged
        return LayoutResult.Changed(WidgetLayout(grids, widgets - widget), widget)
    }

    /** Puts a [removed] widget back on its exact cells; [Rejection.NoRoom] if they were taken meanwhile. */
    fun undo(removed: LaidOutWidget): LayoutResult {
        if (widget(removed.id) != null) return LayoutResult.Unchanged
        val grid = grids[removed.zone] ?: return LayoutResult.Rejected(Rejection.NoRoom)
        val free = ZoneCells(grid, inZone(removed.zone).associate { it.id to it.cells }).isVacant(removed.cells)
        if (!free) return LayoutResult.Rejected(Rejection.NoRoom)
        return LayoutResult.Changed(WidgetLayout(grids, widgets + removed), removed)
    }

    private fun grid(zone: WidgetZoneId): CellGrid = grids[zone] ?: error("No Widget Zone $zone on this Page")

    /** This layout with [placed] in its zone and that zone's other widgets at [others]. */
    private fun commit(
        placed: LaidOutWidget,
        others: Map<Int, CellRect>,
    ): LayoutResult.Changed {
        val moved = mutableListOf<LaidOutWidget>()
        val rest =
            widgets.filter { it.id != placed.id }.map { widget ->
                val cells = others[widget.id]
                if (widget.zone != placed.zone || cells == null || cells == widget.cells) {
                    widget
                } else {
                    widget.copy(cells = cells).also(moved::add)
                }
            }
        return LayoutResult.Changed(WidgetLayout(grids, rest + placed), placed, moved)
    }

    private fun firstFreeSpot(
        zone: WidgetZoneId,
        grid: CellGrid,
        span: CellSpan,
    ): CellRect? {
        val cells = ZoneCells(grid, inZone(zone).associate { it.id to it.cells })
        for (row in 0..grid.rows - span.height) {
            for (column in 0..grid.columns - span.width) {
                val candidate = CellRect(column, row, span.width, span.height)
                if (cells.isVacant(candidate)) return candidate
            }
        }
        return null
    }

    companion object {
        /** Launcher3 `AppWidgetResizeFrame.RESIZE_THRESHOLD`: the share of a cell a handle must travel. */
        const val RESIZE_THRESHOLD = 0.66f

        /** Whole cells of span change for a handle delta of [deltaCells]. */
        private fun spanIncrement(deltaCells: Float): Int =
            if (abs(deltaCells) <= RESIZE_THRESHOLD) 0 else (sign(deltaCells) * ceil(abs(deltaCells) - RESIZE_THRESHOLD)).toInt()
    }
}

/** One step on the grid; at most one cell per axis. */
private data class Direction(
    val dx: Int,
    val dy: Int,
) {
    operator fun unaryMinus() = Direction(-dx, -dy)

    /**
     * Launcher3 `attemptPushInDirection`: a diagonal splits into its axes, then those reversed;
     * a straight push tries itself, its reverse, then the two perpendicular directions.
     */
    fun pushOrder(): List<Direction> =
        if (dx != 0 && dy != 0) {
            listOf(Direction(dx, 0), Direction(0, dy), Direction(-dx, 0), Direction(0, -dy))
        } else {
            val perpendicular = Direction(dy, dx)
            listOf(this, -this, perpendicular, -perpendicular)
        }
}

/** A dragged or resized widget's spot, and where the zone's other widgets go to make room for it. */
private class Solution(
    val cells: CellRect,
    val others: Map<Int, CellRect>,
)

/** The other widgets of one Widget Zone by id, on its [grid]: the reorder algorithm's ground. */
private class ZoneCells(
    val grid: CellGrid,
    val items: Map<Int, CellRect>,
) {
    fun inBounds(rect: CellRect) =
        rect.column >= 0 && rect.row >= 0 && rect.column + rect.width <= grid.columns && rect.row + rect.height <= grid.rows

    fun isVacant(rect: CellRect) = inBounds(rect) && items.values.none { it.overlaps(rect) }

    /**
     * Launcher3 `findReorderSolution`: makes room for the dragged widget at ([column], [row]),
     * shrinking it one cell at a time, X then Y, down to [min] while no rearrangement exists.
     */
    fun pushSolution(
        column: Int,
        row: Int,
        span: CellSpan,
        min: CellSpan,
        origin: CellRect?,
    ): Solution? {
        var size = span
        var shrinkX = true
        while (true) {
            val rect = CellRect(clamp(column, size.width, grid.columns), clamp(row, size.height, grid.rows), size.width, size.height)
            makeRoom(rect, pushDirection(rect, origin))?.let { return Solution(rect, it) }
            size =
                when {
                    size.width > min.width && (min.height == size.height || shrinkX) ->
                        size.copy(width = size.width - 1).also {
                            shrinkX =
                                false
                        }
                    size.height > min.height -> size.copy(height = size.height - 1).also { shrinkX = true }
                    else -> return null
                }
        }
    }

    /**
     * Launcher3 `closestEmptySpaceReorder`: the vacant spot nearest ([column], [row]) for the
     * largest span between [min] and [span] there; nothing moves. Ties go to the larger span,
     * then row by row.
     */
    fun closestEmptySpace(
        column: Int,
        row: Int,
        span: CellSpan,
        min: CellSpan,
    ): Solution? {
        val spans =
            (min.width..span.width)
                .flatMap { w -> (min.height..span.height).map { h -> CellSpan(w, h) } }
                .sortedByDescending { it.width * it.height }
        var best: CellRect? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.columns) {
                val rect = spans.firstNotNullOfOrNull { s -> CellRect(c, r, s.width, s.height).takeIf(::isVacant) } ?: continue
                val distance = (c - column) * (c - column) + (r - row) * (r - row)
                if (distance < bestDistance || (distance == bestDistance && rect.area > best!!.area)) {
                    best = rect
                    bestDistance = distance
                }
            }
        }
        return best?.let { Solution(it, items) }
    }

    /**
     * Launcher3 `rearrangementExists`: where the others go so that [target] is free, or null.
     * Tries pushing the intersecting widgets in each direction of [preferred]'s push order, then
     * moving them as one block, then moving each to its own nearest free spot.
     */
    fun makeRoom(
        target: CellRect,
        preferred: Direction,
    ): Map<Int, CellRect>? {
        if (!inBounds(target)) return null
        val intersecting = items.filterValues { it.overlaps(target) }.keys
        if (intersecting.isEmpty()) return items
        for (direction in preferred.pushOrder()) push(intersecting, target, direction)?.let { return it }
        return moveAsBlock(intersecting, target) ?: moveEach(intersecting, target)
    }

    /** Pushes [intersecting] in [direction] until clear of [target], sweeping up what they hit; null at the zone edge. */
    private fun push(
        intersecting: Set<Int>,
        target: CellRect,
        direction: Direction,
    ): Map<Int, CellRect>? {
        val positions = items.toMutableMap()
        val cluster = intersecting.toMutableSet()
        while (cluster.any { positions.getValue(it).overlaps(target) }) {
            do {
                val hit =
                    positions.keys.filter { id ->
                        id !in cluster && cluster.any { positions.getValue(it).shifted(direction).overlaps(positions.getValue(id)) }
                    }
                cluster += hit
            } while (hit.isNotEmpty())
            for (id in cluster) {
                val moved = positions.getValue(id).shifted(direction)
                if (!inBounds(moved)) return null
                positions[id] = moved
            }
        }
        return positions
    }

    /** Moves [cluster] as one block to the nearest spot where its bounding box is free. */
    private fun moveAsBlock(
        cluster: Set<Int>,
        target: CellRect,
    ): Map<Int, CellRect>? {
        val rects = cluster.map(items::getValue)
        val left = rects.minOf { it.column }
        val top = rects.minOf { it.row }
        val box = CellRect(left, top, rects.maxOf { it.column + it.width } - left, rects.maxOf { it.row + it.height } - top)
        val blocked = items.filterKeys { it !in cluster }.values + target
        val spot = nearestVacant(box, blocked) ?: return null
        return items +
            cluster.associateWith { id ->
                items.getValue(id).let { it.copy(column = it.column + spot.column - left, row = it.row + spot.row - top) }
            }
    }

    /** Moves each of [cluster], top to bottom and left to right, to its own nearest free spot. */
    private fun moveEach(
        cluster: Set<Int>,
        target: CellRect,
    ): Map<Int, CellRect>? {
        val positions = items.toMutableMap()
        val placed =
            items
                .filterKeys { it !in cluster }
                .values
                .toMutableList<CellRect>()
                .apply { add(target) }
        for (id in cluster.sortedWith(compareBy({ items.getValue(it).row }, { items.getValue(it).column }))) {
            val spot = nearestVacant(items.getValue(id), placed) ?: return null
            positions[id] = spot
            placed += spot
        }
        return positions
    }

    /** The in-bounds spot for [rect]'s span nearest [rect] that overlaps none of [blocked]. */
    private fun nearestVacant(
        rect: CellRect,
        blocked: List<CellRect>,
    ): CellRect? {
        var best: CellRect? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0..grid.rows - rect.height) {
            for (c in 0..grid.columns - rect.width) {
                val candidate = rect.copy(column = c, row = r)
                if (blocked.any { it.overlaps(candidate) }) continue
                val distance = (c - rect.column) * (c - rect.column) + (r - rect.row) * (r - rect.row)
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * Push the widgets under [target] away from it: from its centre toward theirs. When they
     * line up exactly, toward the dragged widget's [origin] (the cells it vacated), else down.
     */
    private fun pushDirection(
        target: CellRect,
        origin: CellRect?,
    ): Direction {
        val hit = items.values.filter { it.overlaps(target) }
        if (hit.isEmpty()) return Direction(0, 1)
        val dx = hit.sumOf { it.column * 2 + it.width } * 1.0 / hit.size - (target.column * 2 + target.width)
        val dy = hit.sumOf { it.row * 2 + it.height } * 1.0 / hit.size - (target.row * 2 + target.height)
        if (dx != 0.0 || dy != 0.0) return Direction(sign(dx).toInt(), sign(dy).toInt())
        if (origin != null && origin != target) {
            return Direction((origin.column - target.column).sign, (origin.row - target.row).sign)
        }
        return Direction(0, 1)
    }

    private fun clamp(
        start: Int,
        size: Int,
        gridSize: Int,
    ) = start.coerceAtMost(gridSize - size).coerceAtLeast(0)
}

private val CellRect.area: Int get() = width * height

private fun CellRect.shifted(direction: Direction) = copy(column = column + direction.dx, row = row + direction.dy)

private fun CellSpan.fitsIn(grid: CellGrid) = width <= grid.columns && height <= grid.rows

/** This span cut to [grid] on each axis, but never below [min]. */
private fun CellSpan.cutTo(
    grid: CellGrid,
    min: CellSpan,
) = CellSpan(minOf(width, grid.columns).coerceAtLeast(min.width), minOf(height, grid.rows).coerceAtLeast(min.height))
