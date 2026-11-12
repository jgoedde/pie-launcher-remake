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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.home.CellGrid
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.drawer.DrawerState

/** Home Page dimensions. Code constants by design, never configuration. */
object HomePageLayout {
    val PieZoneHeight: Dp = 240.dp
}

/** Draws one placed widget at the given size; the Home Page positions it on its cells. */
typealias WidgetContent = @Composable (widget: LaidOutWidget, width: Dp, height: Dp) -> Unit

/**
 * The Home Page, top to bottom: upper Widget Zone (remaining height), Pie Zone, lower Widget
 * Zone (one cell row), laid out by [HomeZones]. Everything sits inside the mandatory system
 * gesture insets, so touches in those strips never reach the launcher. In either Widget Zone a
 * swipe up drags the App Drawer open, a swipe down calls [onSwipeDown] (the notification shade),
 * and a long-press on empty space calls [onEmptyLongPress] with the zone and the press point in
 * that zone. [pieZone] carries the Pie's touch handling. [widgets] are laid out on the zones
 * by [layoutOf]. [onZonesLaidOut] receives the zone grids, so widgets can be placed from
 * outside the Home Page.
 */
@Composable
fun <W> HomePage(
    drawer: DrawerState,
    onSwipeDown: () -> Unit,
    pieZone: Modifier,
    widgets: List<W>,
    layoutOf: (List<W>, HomeZones) -> WidgetLayout,
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
        val cellGapPx = with(density) { CellGrid.CELL_GAP_DP.dp.toPx() }
        val zones =
            remember(constraints.maxWidth, constraints.maxHeight, pieZonePx, cellGapPx) {
                HomeZones.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pieZonePx, cellGapPx)
            }
        LaunchedEffect(zones) { onZonesLaidOut(zones) }
        val layout = remember(widgets, zones) { layoutOf(widgets, zones) }
        Column(Modifier.fillMaxSize()) {
            WidgetZoneId.entries.forEach { zoneId ->
                if (zoneId == WidgetZoneId.HomeLower) PieZone(pieZone)
                WidgetZone(
                    layout = layout,
                    zone = zoneId,
                    widgetContent = widgetContent,
                    modifier =
                        Modifier
                            .height(with(density) { zones.heightPx(zoneId).toDp() })
                            .homeSwipes(drawer, onSwipeDown)
                            .emptySpaceLongPress(
                                isEmpty = { at -> layout.widgetAt(zoneId, at.x, at.y) == null },
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

/** One Widget Zone of [layout] holding its widgets on their cells. */
@Composable
private fun WidgetZone(
    layout: WidgetLayout,
    zone: WidgetZoneId,
    widgetContent: WidgetContent,
    modifier: Modifier,
    overlay: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxWidth()) {
        layout.inZone(zone).forEach { widget ->
            val bounds = layout.boundsOf(widget)
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
