package io.github.jgoedde.pielauncher.widgets

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.HOME_PAGE
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.ProviderSizes
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetConfigure
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetReconcile
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.platform.LauncherDatabase
import io.github.jgoedde.pielauncher.platform.WidgetRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one process-wide `AppWidgetHost` (PRD 7.1) and the widget layout in Room (ADR 0002).
 * Owns the widget ids: allocates and deletes them, places widgets, drops rows the system removes
 * and reconciles both sides at startup.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetHost(
    private val application: Application,
    database: LauncherDatabase,
    private val appScope: CoroutineScope,
) {
    private val dao = database.widgets()
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(application)
    private val host = LauncherAppWidgetHost(application, onRemoved = { id -> appScope.launch { dao.delete(listOf(id)) } })

    private val pxPerDp: Float get() = application.resources.displayMetrics.density

    /** Serialises placements, so two adds can never claim the same free cells. */
    private val placing = Mutex()

    /** Every placed widget's row, as stored. */
    val widgets: StateFlow<List<WidgetRow>> = dao.observeAll().stateIn(appScope, SharingStarted.Eagerly, emptyList())

    /** Updates flow only while listening; the system queues them meanwhile. Called from the Home Activity's onStart. */
    fun startListening() {
        try {
            host.startListening()
        } catch (e: RuntimeException) {
            // Launcher3 tolerates binder size errors here: the listening relationship is set up anyway.
            Log.w(TAG, "startListening failed", e)
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopListening failed", e)
        }
    }

    /** Called when the Home Activity is destroyed: its widget views must not outlive it. */
    fun releaseViews() = host.releaseViews()

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) = host.deleteAppWidgetId(id)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun bindIfAllowed(
        id: Int,
        profile: UserHandle,
        provider: ComponentName,
        options: Bundle? = null,
    ): Boolean = manager.bindAppWidgetIdIfAllowed(id, profile, provider, options)

    fun createView(
        context: Context,
        id: Int,
    ): AppWidgetHostView = host.createView(context, id, info(id))

    /** Starts the provider's configure activity for [id]; the result arrives in [activity]'s `onActivityResult`. */
    fun startConfigure(
        activity: Activity,
        id: Int,
        requestCode: Int,
        options: Bundle?,
    ) = host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, options)

    /** The Home Page's Widget Layout: [rows] on [zones], with each provider's span bounds. */
    fun layout(
        rows: List<WidgetRow>,
        zones: HomeZones,
    ): WidgetLayout {
        val grid = zones.catalogGrid(pxPerDp)
        return WidgetLayout(
            zones.grids,
            rows.filter { it.page == HOME_PAGE }.map { row ->
                val bounds = info(row.appWidgetId)?.spanBounds(grid, pxPerDp) ?: SpanBounds.fixed(row.cells.span)
                LaidOutWidget(row.appWidgetId, row.zone, row.cells, bounds)
            },
        )
    }

    /** Where a widget of [info] would go: the first of [targets] on the Home Page with room, or a rejection. */
    suspend fun findSpot(
        info: AppWidgetProviderInfo,
        targets: List<WidgetZoneId>,
        zones: HomeZones,
    ): LayoutResult =
        layout(dao.all(), zones).add(AppWidgetManager.INVALID_APPWIDGET_ID, info.spanBounds(zones.catalogGrid(pxPerDp), pxPerDp), targets)

    /**
     * Places the bound widget [id] at the first free spot of [targets] and records it. Nothing is
     * recorded on a rejection.
     */
    suspend fun place(
        id: Int,
        targets: List<WidgetZoneId>,
        zones: HomeZones,
    ): LayoutResult =
        placing.withLock {
            val info = info(id) ?: return LayoutResult.Rejected(Rejection.NoRoom)
            val result = layout(dao.all(), zones).add(id, info.spanBounds(zones.catalogGrid(pxPerDp), pxPerDp), targets)
            if (result is LayoutResult.Changed) {
                dao.insert(WidgetRow(id, info.provider, HOME_PAGE, result.widget.zone, result.widget.cells))
            }
            result
        }

    /** Takes a placed widget back out: drops its row and deletes its id. */
    suspend fun remove(id: Int) {
        dao.delete(listOf(id))
        host.deleteAppWidgetId(id)
    }

    /** Startup reconcile (PRD 7.1): deletes host ids with no row and rows with no host id. */
    suspend fun reconcile() {
        val result = WidgetReconcile.of(host.appWidgetIds.toSet(), dao.all().map { it.appWidgetId }.toSet())
        result.orphanHostIds.forEach(host::deleteAppWidgetId)
        if (result.orphanRows.isNotEmpty()) dao.delete(result.orphanRows)
        if (result.orphanHostIds.isNotEmpty() || result.orphanRows.isNotEmpty()) {
            Log.i(TAG, "Reconciled widgets: deleted ids ${result.orphanHostIds}, dropped rows ${result.orphanRows}")
        }
    }

    private companion object {
        const val TAG = "WidgetHost"
    }
}

/** Whether adding this widget runs its configure activity (PRD 7.4). */
internal fun AppWidgetProviderInfo.needsConfigure(): Boolean =
    WidgetConfigure.isNeeded(
        hasConfigureActivity = configure != null,
        configurationOptional = widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0,
        reconfigurable = widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0,
    )

/** The span bounds of this provider on [grid] (Widget Catalog, Launcher3 `initSpans`); provider px mapped to dp. */
private fun AppWidgetProviderInfo.spanBounds(
    grid: CatalogGrid,
    pxPerDp: Float,
): SpanBounds {
    val spans =
        WidgetCatalog.spans(
            ProviderSizes(
                minWidth = minWidth / pxPerDp,
                minHeight = minHeight / pxPerDp,
                minResizeWidth = minResizeWidth / pxPerDp,
                minResizeHeight = minResizeHeight / pxPerDp,
                maxResizeWidth = maxResizeWidth / pxPerDp,
                maxResizeHeight = maxResizeHeight / pxPerDp,
                targetCells = if (targetCellWidth > 0 && targetCellHeight > 0) CellSpan(targetCellWidth, targetCellHeight) else null,
                resizesHorizontally = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                resizesVertically = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            ),
            grid,
        )
    return SpanBounds(spans.default, spans.min, spans.max, spans.resizesHorizontally, spans.resizesVertically)
}
