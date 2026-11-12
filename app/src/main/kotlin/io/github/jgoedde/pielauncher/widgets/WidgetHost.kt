package io.github.jgoedde.pielauncher.widgets

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.widget.RemoteViews
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
import io.github.jgoedde.pielauncher.core.home.WidgetSpans
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.platform.LauncherDatabase
import io.github.jgoedde.pielauncher.platform.WidgetRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val pendingWidget = MutableStateFlow<PendingWidget?>(null)

    /**
     * The widget being added while its configure screen is open (PRD 7.4): bound, not yet
     * placed. Home draws its preview in the target cells. In memory only.
     */
    val pending: StateFlow<PendingWidget?> = pendingWidget.asStateFlow()

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

    /** Every widget provider of the main profile the Widget Picker may list (not hidden from pickers). */
    fun pickerProviders(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle()).filter {
            it.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
        }

    /** The installed provider [provider] of [profile], if it still exists. */
    fun provider(
        provider: ComponentName,
        profile: UserHandle,
    ): AppWidgetProviderInfo? = manager.getInstalledProvidersForProfile(profile).firstOrNull { it.provider == provider }

    /**
     * The framework's generated home-screen preview of [info] (API 35), or null: none was set,
     * or the launcher is not bind-allow-listed yet. A blocking binder call.
     */
    fun generatedPreview(info: AppWidgetProviderInfo): RemoteViews? {
        if (info.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN == 0) return null
        return try {
            manager.getWidgetPreview(info.provider, info.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No generated preview for ${info.provider}", e)
            null
        }
    }

    /** The spans of [info] on [grid] (Widget Catalog). */
    fun spans(
        info: AppWidgetProviderInfo,
        grid: CatalogGrid,
    ): WidgetSpans = info.spans(grid, pxPerDp)

    /** Shows bound widget [id] of [info] as a placeholder at [at] until [place] or [clearPending]. */
    fun showPending(
        id: Int,
        info: AppWidgetProviderInfo,
        at: LaidOutWidget,
    ) {
        pendingWidget.value = PendingWidget(WidgetRow(id, info.provider, HOME_PAGE, at.zone, at.cells), info)
    }

    /** Removes the placeholder of widget [id], if it is the one shown. */
    fun clearPending(id: Int) {
        pendingWidget.update { if (it?.row?.appWidgetId == id) null else it }
    }

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
            clearPending(id)
            result
        }

    /** Takes a placed widget back out: drops its row and deletes its id. */
    suspend fun remove(id: Int) {
        dao.delete(listOf(id))
        host.deleteAppWidgetId(id)
    }

    /**
     * Records the Home Page's widgets where [layout] has them (a committed move with its reflow).
     * Runs in the app scope, so leaving Home right after a drop still saves it.
     */
    fun save(layout: WidgetLayout) {
        appScope.launch {
            placing.withLock {
                val rows = dao.all().associateBy { it.appWidgetId }
                val changed =
                    layout.widgets.mapNotNull { widget ->
                        rows[widget.id]?.takeIf { it.zone != widget.zone || it.cells != widget.cells }?.copy(
                            zone = widget.zone,
                            cells = widget.cells,
                        )
                    }
                if (changed.isNotEmpty()) dao.insertAll(changed)
            }
        }
    }

    /** Deletes the removed widget [id] for good once its undo window is over (PRD 7.5): row and id. */
    fun discard(id: Int) {
        appScope.launch { remove(id) }
    }

    /** Whether widget [id] offers "Widget settings": it has a configure activity and declares itself reconfigurable. */
    fun isReconfigurable(id: Int): Boolean {
        val info = info(id) ?: return false
        return info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
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

/** The spans of this provider on [grid] (Widget Catalog, Launcher3 `initSpans`); provider px mapped to dp. */
private fun AppWidgetProviderInfo.spans(
    grid: CatalogGrid,
    pxPerDp: Float,
): WidgetSpans =
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

private fun AppWidgetProviderInfo.spanBounds(
    grid: CatalogGrid,
    pxPerDp: Float,
): SpanBounds {
    val spans = spans(grid, pxPerDp)
    return SpanBounds(spans.default, spans.min, spans.max, spans.resizesHorizontally, spans.resizesVertically)
}

/** A widget being added: its future [row] and its provider [info]. */
class PendingWidget(
    val row: WidgetRow,
    val info: AppWidgetProviderInfo,
)
