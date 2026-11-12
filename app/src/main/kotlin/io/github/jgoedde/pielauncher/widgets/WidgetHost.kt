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
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.HOME_PAGE
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetConfigure
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetReconcile
import io.github.jgoedde.pielauncher.core.home.WidgetSize
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

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) = host.deleteAppWidgetId(id)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun bindIfAllowed(
        id: Int,
        profile: UserHandle,
        provider: ComponentName,
    ): Boolean = manager.bindAppWidgetIdIfAllowed(id, profile, provider, null)

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
    ): WidgetLayout =
        WidgetLayout(
            zones.grids,
            rows.filter { it.page == HOME_PAGE }.map { row ->
                val bounds = info(row.appWidgetId)?.spanBounds(zones) ?: SpanBounds.fixed(row.cells.span)
                LaidOutWidget(row.appWidgetId, row.zone, row.cells, bounds)
            },
        )

    /** Where a widget of [info] would go: the first of [targets] on the Home Page with room, or a rejection. */
    suspend fun findSpot(
        info: AppWidgetProviderInfo,
        targets: List<WidgetZoneId>,
        zones: HomeZones,
    ): LayoutResult = layout(dao.all(), zones).add(AppWidgetManager.INVALID_APPWIDGET_ID, info.spanBounds(zones), targets)

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
            val result = layout(dao.all(), zones).add(id, info.spanBounds(zones), targets)
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

/**
 * Interim span bounds until the Widget Catalog (#54): the default span from `minWidth`/`minHeight`
 * and the target cells, the min span from `minResize*` on the axes the provider resizes on (else
 * the default), the max span the largest Widget Zone.
 */
private fun AppWidgetProviderInfo.spanBounds(zones: HomeZones): SpanBounds {
    val target = if (targetCellWidth > 0 && targetCellHeight > 0) CellSpan(targetCellWidth, targetCellHeight) else null
    val default = WidgetSize(minWidth, minHeight, target).defaultSpan(zones.cellPx)
    val minResize = WidgetSize(minResizeWidth, minResizeHeight).minSpan(zones.cellPx)
    val horizontal = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val vertical = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    return SpanBounds(
        default = default,
        min =
            CellSpan(
                if (horizontal) minOf(minResize.width, default.width) else default.width,
                if (vertical) minOf(minResize.height, default.height) else default.height,
            ),
        max = CellSpan(zones.upper.columns, maxOf(zones.upper.rows, zones.lower.rows)),
        resizesHorizontally = horizontal,
        resizesVertically = vertical,
    )
}
