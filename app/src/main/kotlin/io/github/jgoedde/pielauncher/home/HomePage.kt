package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.home.CellGrid
import io.github.jgoedde.pielauncher.core.home.CellRect
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.drawer.DrawerState
import io.github.jgoedde.pielauncher.widgets.HomeWidget
import io.github.jgoedde.pielauncher.widgets.WidgetZoneId

/** Home Page dimensions. Code constants by design, never configuration. */
object HomePageLayout {
    val PieZoneHeight: Dp = 240.dp
}

/** Draws one placed widget at the given size; the Home Page positions it on its cells. */
typealias WidgetContent = @Composable (widget: HomeWidget, width: Dp, height: Dp) -> Unit

/**
 * The Home Page, top to bottom: upper Widget Zone (remaining height), Pie Zone, lower Widget
 * Zone (one cell row), laid out by [HomeZones]. Everything sits inside the mandatory system
 * gesture insets, so touches in those strips never reach the launcher. In either Widget Zone a
 * swipe up drags the App Drawer open, a swipe down calls [onSwipeDown] (the notification shade),
 * and a long-press on empty space calls [onEmptyLongPress] with the zone and the press point in
 * that zone. [pieZone] carries the Pie's touch handling. [onZonesLaidOut] receives the zone
 * grids, so widgets can be placed from outside the Home Page.
 */
@Composable
fun HomePage(
    drawer: DrawerState,
    onSwipeDown: () -> Unit,
    pieZone: Modifier,
    widgets: List<HomeWidget>,
    widgetContent: WidgetContent,
    onEmptyLongPress: (WidgetZoneId, Offset) -> Unit,
    onZonesLaidOut: (HomeZones) -> Unit,
    modifier: Modifier = Modifier,
    zoneOverlay: @Composable (WidgetZoneId) -> Unit = {},
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Vertical)),
    ) {
        val density = LocalDensity.current
        val pieZonePx = with(density) { HomePageLayout.PieZoneHeight.toPx() }
        val zones =
            remember(constraints.maxWidth, constraints.maxHeight, pieZonePx) {
                HomeZones.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pieZonePx)
            }
        LaunchedEffect(zones) { onZonesLaidOut(zones) }
        Column(Modifier.fillMaxSize()) {
            listOf(WidgetZoneId.HomeUpper, WidgetZoneId.HomeLower).forEach { zoneId ->
                if (zoneId == WidgetZoneId.HomeLower) PieZone(pieZone)
                val grid = if (zoneId == WidgetZoneId.HomeUpper) zones.upper else zones.lower
                val height = if (zoneId == WidgetZoneId.HomeUpper) zones.upperHeightPx else zones.lowerHeightPx
                val inZone = widgets.filter { it.zone == zoneId }
                WidgetZone(
                    grid = grid,
                    widgets = inZone,
                    widgetContent = widgetContent,
                    modifier =
                        Modifier
                            .height(with(density) { height.toDp() })
                            .homeSwipes(drawer, onSwipeDown)
                            .emptySpaceLongPress(
                                isEmpty = { at -> inZone.none { grid.boundsOf(it.cells).contains(at) } },
                                onLongPress = { at -> onEmptyLongPress(zoneId, at) },
                            ),
                    overlay = { zoneOverlay(zoneId) },
                )
            }
        }
    }
}

/** Invisible at rest; a touch-down here opens the Pie. Swipes here never open the Drawer or the shade. */
@Composable
private fun PieZone(modifier: Modifier) {
    Spacer(modifier.fillMaxWidth().height(HomePageLayout.PieZoneHeight))
}

/** An area laid out on the 4-column [CellGrid] holding its [widgets] on their cells. */
@Composable
private fun WidgetZone(
    grid: CellGrid,
    widgets: List<HomeWidget>,
    widgetContent: WidgetContent,
    modifier: Modifier,
    overlay: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxWidth()) {
        widgets.forEach { widget ->
            val bounds = grid.boundsOf(widget.cells)
            with(density) {
                val width = bounds.width.toDp()
                val height = bounds.height.toDp()
                Box(Modifier.offset(bounds.left.toDp(), bounds.top.toDp()).size(width, height)) {
                    widgetContent(widget, width, height)
                }
            }
        }
        overlay()
    }
}

/** The px bounds of [cells] inside the zone, rows starting below the zone's top padding. */
private fun CellGrid.boundsOf(cells: CellRect): Rect {
    val left = cells.column * cellWidth
    val top = verticalPadding + cells.row * cellHeight
    return Rect(left, top, left + cells.width * cellWidth, top + cells.height * cellHeight)
}
