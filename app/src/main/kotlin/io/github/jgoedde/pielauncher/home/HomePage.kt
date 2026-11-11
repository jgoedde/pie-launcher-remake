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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
 * never reach the launcher. In either Widget Zone a swipe up drags the App Drawer open and a swipe
 * down calls [onSwipeDown] (the notification shade).
 */
@Composable
fun HomePage(
    drawer: DrawerState,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Vertical)),
    ) {
        val cell = with(LocalDensity.current) { CellGrid.cellSizeFor(constraints.maxWidth.toFloat()).toDp() }
        Column(Modifier.fillMaxSize()) {
            WidgetZone(Modifier.weight(1f).homeSwipes(drawer, onSwipeDown))
            PieZone()
            WidgetZone(Modifier.height(cell * HomePageLayout.LOWER_WIDGET_ZONE_ROWS).homeSwipes(drawer, onSwipeDown))
        }
    }
}

/** Invisible at rest; the Pie will live here. Swipes here never open the Drawer or the shade. */
@Composable
private fun PieZone() {
    Spacer(Modifier.fillMaxWidth().height(HomePageLayout.PieZoneHeight))
}

/** An area laid out on the 4-column [CellGrid]; empty until widgets arrive. */
@Composable
private fun WidgetZone(modifier: Modifier) {
    Box(modifier.fillMaxWidth())
}
