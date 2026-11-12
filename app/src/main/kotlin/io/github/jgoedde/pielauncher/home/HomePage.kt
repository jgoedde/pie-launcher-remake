package io.github.jgoedde.pielauncher.home

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.home.CellGrid
import io.github.jgoedde.pielauncher.core.home.DotGrid
import io.github.jgoedde.pielauncher.core.home.EditMode
import io.github.jgoedde.pielauncher.core.home.HOME_PAGE
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.PageLayouts
import io.github.jgoedde.pielauncher.core.home.PageZone
import io.github.jgoedde.pielauncher.core.home.WidgetDrag
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.drawer.DrawerState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Home Page dimensions. Code constants by design, never configuration. */
object HomePageLayout {
    val PieZoneHeight: Dp = 240.dp

    /** How close to a side a dragged widget's finger must dwell to change the Page. */
    val PageEdge: Dp = 32.dp
}

/** Draws one placed widget at the given size; its Page positions it on its cells. */
typealias WidgetContent = @Composable (widget: LaidOutWidget, width: Dp, height: Dp) -> Unit

/**
 * The Pages in a stock [HorizontalPager] (PRD 3.1): the Home Page leftmost, top to bottom upper
 * Widget Zone (remaining height), Pie Zone, lower Widget Zone (two cell rows); every other Page
 * one Widget Zone over its full height, laid out by [HomeZones]. Everything sits inside the
 * mandatory system gesture insets, so touches in those strips never reach the launcher. In any
 * Widget Zone a swipe up drags the App Drawer open, a swipe down calls [onSwipeDown] (the
 * notification shade), and a long-press on empty space calls [onEmptyLongPress] with the zone and
 * the press point in that zone. [pieZone] carries the Pie's touch handling; [pieOpen] says whether
 * the Pie shows. [widgets] are laid
 * out on the Pages by [layoutsOf] (with at least the given number of Pages). [onZonesLaidOut]
 * receives the zone grids, so widgets can be placed from outside the home screen.
 *
 * Pages are swiped only at rest; in Edit Mode the [editor] drives the Page shown. Every Page
 * stays composed, so a dragged widget's view keeps its touch while other Pages show. The Page
 * indicator shows while Pages are swiped and in Edit Mode.
 */
@Composable
internal fun <W> HomePages(
    drawer: DrawerState,
    onSwipeDown: () -> Unit,
    pieZone: Modifier,
    pieOpen: Boolean,
    widgets: List<W>,
    layoutsOf: (List<W>, HomeZones, Int) -> PageLayouts,
    widgetContent: WidgetContent,
    onEmptyLongPress: (PageZone, Offset) -> Unit,
    onZonesLaidOut: (HomeZones) -> Unit,
    editor: WidgetEditor,
    modifier: Modifier = Modifier,
    zoneOverlay: @Composable (PageZone) -> Unit = {},
    widgetOverlay: @Composable (LaidOutWidget) -> Unit = {},
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Vertical)),
    ) {
        val density = LocalDensity.current
        val pieZonePx = with(density) { HomePageLayout.PieZoneHeight.toPx() }
        val cellGapPx = with(density) { CellGrid.CELL_GAP_DP.dp.toPx() }
        val edgePx = with(density) { HomePageLayout.PageEdge.toPx() }
        val zones =
            remember(constraints.maxWidth, constraints.maxHeight, pieZonePx, cellGapPx) {
                HomeZones.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pieZonePx, cellGapPx)
            }
        LaunchedEffect(zones) { onZonesLaidOut(zones) }
        val minPages = editor.minPages
        val layouts = remember(widgets, zones, minPages) { layoutsOf(widgets, zones, minPages) }
        val pageCount = layouts.pages.size
        val origin = remember { arrayOf(Offset.Zero) }
        val widthPx = constraints.maxWidth.toFloat()
        SideEffect { editor.laidOut(layouts, zones, origin[0], widthPx, edgePx) }
        val drag = (editor.mode as? EditMode.Dragging)?.drag
        LaunchedEffect(editor.dueAtMs) {
            val due = editor.dueAtMs ?: return@LaunchedEffect
            delay((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
            editor.tick()
        }
        val pager = rememberPagerState { pageCount }
        LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(editor::pageSettled) }
        LaunchedEffect(editor.homePresses) { if (editor.homePresses > 0) pager.animateScrollToPage(HOME_PAGE) }
        LaunchedEffect(editor.page, pageCount) {
            val target = editor.page
            if (target < pageCount && pager.currentPage != target) pager.animateScrollToPage(target)
        }
        // A resize redraws the widget at its new span, and its pushed neighbours, while the handle moves.
        val resize = editor.resize
        Box(
            Modifier.fillMaxSize().onGloballyPositioned {
                origin[0] = it.positionInWindow()
                editor.laidOut(layouts, zones, origin[0], widthPx, edgePx)
            },
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = pageCount,
                userScrollEnabled = editor.mode == EditMode.Idle,
                key = { it },
            ) { page ->
                val stored = layouts.pages.getOrNull(page) ?: layouts.newPage
                val shown = editor.shown(page, stored)
                val dragHere = drag?.takeIf { editor.page == page }
                val resizeHere = resize?.takeIf { shown.widget(it.widgetId) != null }
                val layout = dragHere?.preview ?: resizeHere?.preview ?: shown
                Column(Modifier.fillMaxSize()) {
                    WidgetZoneId.of(page).forEach { zoneId ->
                        if (zoneId == WidgetZoneId.HomeLower) PieZone(pieZone, blocked = !editor.mode.pieEnabled, pieOpen = pieOpen)
                        WidgetZone(
                            layout = layout,
                            zone = zoneId,
                            drag = dragHere,
                            draggedId = drag?.dragged?.id,
                            pickedFrom = dragHere?.base ?: layout,
                            reordering = drag != null || resize != null,
                            widgetContent = widgetContent,
                            widgetOverlay = widgetOverlay,
                            modifier =
                                Modifier
                                    .height(with(density) { zones.heightPx(zoneId).toDp() })
                                    .homeSwipes(drawer, onSwipeDown)
                                    .emptySpaceLongPress(
                                        isEmpty = { at -> layout.widgetAt(zoneId, at.x, at.y) == null },
                                        onLongPress = { at -> onEmptyLongPress(PageZone(page, zoneId), at) },
                                    ),
                            overlay = { zoneOverlay(PageZone(page, zoneId)) },
                        )
                    }
                }
            }
            PageIndicator(
                pageCount = pageCount,
                pager = pager,
                visible = pager.isScrollInProgress || editor.mode != EditMode.Idle,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * The Page indicator (Launcher3 `PageIndicatorDots`): a dot per Page, the shown one opaque, just
 * above the gesture strip; it fades in while [visible].
 */
@Composable
private fun PageIndicator(
    pageCount: Int,
    pager: PagerState,
    visible: Boolean,
    modifier: Modifier,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(PAGE_INDICATOR_FADE_MS), label = "pageIndicator")
    Row(
        modifier.graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(PageIndicatorGap),
    ) {
        repeat(pageCount) { page ->
            val selected = page == pager.currentPage
            Box(
                Modifier
                    .size(PageIndicatorDot)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (selected) 1f else 0.4f)),
            )
        }
    }
}

private const val PAGE_INDICATOR_FADE_MS = 150
private val PageIndicatorDot = 6.dp
private val PageIndicatorGap = 8.dp

/**
 * Marked at rest by a faint dot grid, which fades out while the Pie is [pieOpen]; a touch-down
 * here opens the Pie. Swipes here never open the Drawer or the shade. In Edit Mode it is drawn
 * [blocked] instead, since it never holds widgets, and the Pie is off: the blocked area takes
 * every touch.
 */
@Composable
private fun PieZone(
    modifier: Modifier,
    blocked: Boolean,
    pieOpen: Boolean,
) {
    Box(modifier.fillMaxWidth().height(HomePageLayout.PieZoneHeight)) {
        if (!blocked) {
            val alpha by animateFloatAsState(if (pieOpen) 0f else 1f, tween(PIE_ZONE_DOTS_FADE_MS), label = "pieZoneDots")
            Canvas(Modifier.fillMaxSize().padding(PieZoneInset).graphicsLayer { this.alpha = alpha }) {
                val radius = PieZoneDotRadius.toPx()
                DotGrid.centres(size.width, size.height, PieZoneDotStep.toPx()).forEach { (x, y) ->
                    drawCircle(PieZoneDotColor, radius, Offset(x, y))
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }.padding(PieZoneInset)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Black.copy(alpha = 0.24f))
                    .border(1.dp, Color.White.copy(alpha = 0.32f), MaterialTheme.shapes.extraLarge),
            )
        }
    }
}

private val PieZoneInset = 8.dp
private val PieZoneDotRadius = 1.2.dp
private val PieZoneDotStep = 20.dp
private val PieZoneDotColor = Color.White.copy(alpha = 0.22f)
private const val PIE_ZONE_DOTS_FADE_MS = 150

/** Launcher3 `REORDER_ANIMATION_DURATION`: neighbours move to their temporary spots. */
private const val REORDER_ANIMATION_MS = 150

/** Launcher3 `REORDER_PREVIEW_MAGNITUDE`: how far a moved neighbour wiggles, as a share of a cell. */
private const val REORDER_PREVIEW_MAGNITUDE = 0.12f

/** Launcher3 `ReorderPreviewAnimation.PREVIEW_DURATION`: one wiggle swing. */
private const val REORDER_PREVIEW_MS = 300

/**
 * One Widget Zone of [layout] holding its widgets on their cells. During a drag the dragged
 * widget [draggedId] keeps its cells in [pickedFrom] but is not drawn (its picture follows the finger, and its view keeps
 * the touch); its target cells are outlined; neighbours slide to their temporary spots and wiggle.
 * While [reordering] (a drag or a resize), neighbours slide rather than jump.
 */
@Composable
private fun WidgetZone(
    layout: WidgetLayout,
    zone: WidgetZoneId,
    drag: WidgetDrag?,
    draggedId: Int?,
    pickedFrom: WidgetLayout,
    reordering: Boolean,
    widgetContent: WidgetContent,
    widgetOverlay: @Composable (LaidOutWidget) -> Unit,
    modifier: Modifier,
    overlay: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // The dragged widget stays where it was picked up, so its view and touch stay put, even while another Page shows.
    val picked = draggedId?.let(pickedFrom::widget)?.takeIf { it.zone == zone }
    val widgets = layout.inZone(zone).filter { it.id != draggedId } + listOfNotNull(picked)
    val moved =
        drag
            ?.moved
            .orEmpty()
            .map { it.id }
            .toSet()
    Box(modifier.fillMaxWidth()) {
        drag?.outline?.takeIf { it.first == zone }?.let { (_, cells) ->
            val bounds =
                drag.preview.grids
                    .getValue(zone)
                    .bounds(cells)
            with(density) {
                Box(
                    Modifier
                        .offset(bounds.left.toDp(), bounds.top.toDp())
                        .size(bounds.width.toDp(), bounds.height.toDp())
                        .clip(RoundedCornerShape(dimensionResource(android.R.dimen.system_app_widget_background_radius)))
                        .background(Color.White.copy(alpha = 0.3f)),
                )
            }
        }
        widgets.forEach { widget ->
            key(widget.id) {
                val bounds = layout.grids.getValue(zone).bounds(widget.cells)
                val target = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                val position by animateIntOffsetAsState(
                    target,
                    animationSpec = if (reordering) tween(REORDER_ANIMATION_MS) else snap(),
                    label = "reorder",
                )
                val wiggle = if (widget.id in moved) wiggleTowards(drag!!.base.widget(widget.id), widget, layout) else Offset.Zero
                with(density) {
                    val width = bounds.width.toDp()
                    val height = bounds.height.toDp()
                    Box(
                        Modifier
                            .offset { position + IntOffset(wiggle.x.roundToInt(), wiggle.y.roundToInt()) }
                            .size(width, height)
                            .graphicsLayer { alpha = if (widget.id == draggedId) 0f else 1f },
                    ) {
                        widgetContent(widget, width, height)
                        widgetOverlay(widget)
                    }
                }
            }
        }
        overlay()
    }
}

/**
 * The wiggle of a neighbour the reorder moved to [now] from [was] (Launcher3
 * `ReorderPreviewAnimation`, preview mode): it swings back and forth by
 * [REORDER_PREVIEW_MAGNITUDE] of a cell toward where it was.
 */
@Composable
private fun wiggleTowards(
    was: LaidOutWidget?,
    now: LaidOutWidget,
    layout: WidgetLayout,
): Offset {
    val grid = layout.grids.getValue(now.zone)
    val from = was?.takeIf { it.zone == now.zone }?.let { grid.bounds(it.cells) } ?: return Offset.Zero
    val to = grid.bounds(now.cells)
    val direction = Offset(from.left - to.left, from.top - to.top)
    val length = direction.getDistance()
    if (length == 0f) return Offset.Zero
    val swing by rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(REORDER_PREVIEW_MS), RepeatMode.Reverse),
        label = "wiggle",
    )
    return direction / length * (REORDER_PREVIEW_MAGNITUDE * grid.cellWidth * swing)
}
