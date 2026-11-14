package io.github.jgoedde.pielauncher.widgets

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CellGrid
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.home.HomePageLayout

/**
 * The Home Page's Widget Zone grids, for placing widgets from outside the home screen (the add
 * trampoline, Pin Requests). The Home Page reports what it laid out; before it has, the grids are
 * computed from the window the same way the Home Page lays them out.
 */
@SingleIn(AppScope::class)
@Inject
class HomeGeometry {
    @Volatile private var laidOut: HomeZones? = null

    /** Called by the Home Page with the zones it measured. */
    fun report(zones: HomeZones) {
        laidOut = zones
    }

    fun zones(context: Context): HomeZones = laidOut ?: computed(context)

    /** The grid widgets are sized against: the largest Widget Zone, in dp. */
    fun catalogGrid(context: Context): CatalogGrid = zones(context).catalogGrid(context.resources.displayMetrics.density)

    /** Full window minus the vertical mandatory gesture insets, as in `HomePage`. */
    private fun computed(context: Context): HomeZones {
        val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.mandatorySystemGestures())
        val density = context.resources.displayMetrics.density
        return HomeZones.of(
            widthPx = metrics.bounds.width().toFloat(),
            heightPx = (metrics.bounds.height() - insets.top - insets.bottom).toFloat(),
            pieZoneHeightPx = HomePageLayout.PieZoneHeight.value * density,
            cellGapPx = CellGrid.CELL_GAP_DP * density,
            zonePaddingPx = CellGrid.ZONE_PADDING_DP * density,
        )
    }
}
