package io.github.jgoedde.pielauncher.home

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.home.CellGrid
import io.github.jgoedde.pielauncher.drawer.DrawerState

/** Home Page dimensions. Code constants by design, never configuration. */
object HomePageLayout {
    val PieZoneHeight: Dp = 240.dp

    /** The lower Widget Zone holds exactly one cell row. */
    const val LOWER_WIDGET_ZONE_ROWS = 1
}

/**
 * The Home Page, top to bottom: upper Widget Zone (remaining height), Pie Zone, lower Widget
 * Zone. Everything sits inside the mandatory system gesture insets, so touches in those strips
 * never reach the launcher. A swipe up in either Widget Zone drags the App Drawer open.
 */
@Composable
fun HomePage(
    drawer: DrawerState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Vertical)),
    ) {
        val cell = CellGrid.cellSizeFor(maxWidth.value).dp
        Column(Modifier.fillMaxSize()) {
            WidgetZone(Modifier.weight(1f).opensDrawer(drawer))
            PieZone()
            WidgetZone(Modifier.height(cell * HomePageLayout.LOWER_WIDGET_ZONE_ROWS).opensDrawer(drawer))
        }
    }
}

/** Invisible at rest; the Pie will live here. Swipes here never open the Drawer. */
@Composable
private fun PieZone() {
    Spacer(Modifier.fillMaxWidth().height(HomePageLayout.PieZoneHeight))
}

/** An area on the 4-column [CellGrid]; empty until widgets arrive. */
@Composable
private fun WidgetZone(
    modifier: Modifier,
    content: @Composable (CellGrid) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        content(CellGrid.of(maxWidth.value, maxHeight.value))
    }
}

@Composable
private fun Modifier.opensDrawer(drawer: DrawerState): Modifier =
    draggable(
        state = rememberDraggableState { delta -> drawer.dragBy(delta) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> drawer.settle(velocity) },
    )
