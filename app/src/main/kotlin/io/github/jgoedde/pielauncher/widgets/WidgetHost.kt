package io.github.jgoedde.pielauncher.widgets

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.widget.RemoteViews
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.home.AddableWidget
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.HomeZones
import io.github.jgoedde.pielauncher.core.home.LaidOutWidget
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.PageCompaction
import io.github.jgoedde.pielauncher.core.home.PageLayouts
import io.github.jgoedde.pielauncher.core.home.PagePlacement
import io.github.jgoedde.pielauncher.core.home.PageZone
import io.github.jgoedde.pielauncher.core.home.ProviderSizes
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetDrop
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetReconcile
import io.github.jgoedde.pielauncher.core.home.WidgetSpans
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
    private val homeGeometry: HomeGeometry,
) {
    private val dao = database.widgets()
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(application)
    private val host =
        LauncherAppWidgetHost(application, onRemoved = { id ->
            appScope.launch {
                dao.delete(listOf(id))
                compactPages()
            }
        })

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

    /**
     * Binds [id] to [info] with [options] when the launcher may bind widgets, and returns null.
     * Otherwise returns the system `ACTION_APPWIDGET_BIND` dialog that asks the user, to start for
     * a result.
     */
    fun bindOrAsk(
        id: Int,
        info: AppWidgetProviderInfo,
        options: Bundle,
    ): Intent? {
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, options)) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
    }

    /**
     * The size options of a widget showing at [span] on the Home Page's grid: its bind options,
     * and what a Pin Request is accepted with.
     */
    fun sizeOptionsFor(
        span: CellSpan,
        context: Context,
    ): Bundle = sizeOptions(WidgetCatalog.sizeList(span, homeGeometry.catalogGrid(context)))

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

    /**
     * Shows bound widget [id] of [info] as a placeholder at [at] on [page] until [place] or
     * [clearPending], with the neighbours a drop [moved] at their new cells meanwhile.
     */
    fun showPending(
        id: Int,
        info: AppWidgetProviderInfo,
        page: Int,
        at: LaidOutWidget,
        moved: List<LaidOutWidget> = emptyList(),
    ) {
        pendingWidget.value = PendingWidget(WidgetRow(id, info.provider, page, at.zone, at.cells), info, moved)
    }

    /** Removes the placeholder of widget [id], if it is the one shown. */
    fun clearPending(id: Int) {
        pendingWidget.update { if (it?.row?.appWidgetId == id) null else it }
    }

    fun createView(
        context: Context,
        id: Int,
    ): AppWidgetHostView = host.createView(context, id, info(id))

    /**
     * Starts the provider's configure activity for widget [id], on add and from Edit Mode's
     * "Widget settings"; the result arrives in [activity]'s `onActivityResult` with [requestCode].
     * False when it cannot start.
     */
    fun startConfigure(
        activity: Activity,
        id: Int,
        requestCode: Int,
    ): Boolean {
        // The configure IntentSender carries no background-start rights of its own; the visible launcher grants them.
        val options =
            ActivityOptions
                .makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                .toBundle()
        return try {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, options)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Configure activity missing for widget $id", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Configure activity not allowed for widget $id", e)
            false
        }
    }

    /**
     * The Widget Layouts of all Pages: [rows] on [zones], with each provider's span bounds. There
     * are at least [minPages] Pages, so a Page a drag just made shows before it holds anything.
     */
    fun layouts(
        rows: List<WidgetRow>,
        zones: HomeZones,
        minPages: Int = 1,
    ): PageLayouts {
        val grid = zones.catalogGrid(pxPerDp)
        val byPage = rows.groupBy { it.page }
        return PageLayouts.of(zones, maxOf(PageCompaction.pageCount(byPage.keys), minPages)) { page ->
            byPage[page].orEmpty().map { row ->
                val bounds = info(row.appWidgetId)?.spanBounds(grid, pxPerDp) ?: SpanBounds.fixed(row.cells.span)
                LaidOutWidget(row.appWidgetId, row.zone, row.cells, bounds)
            }
        }
    }

    /** Where a widget of [info] would go at [target]: where it was dropped, else the first free spot from the tapped zone on. */
    suspend fun findSpot(
        info: AppWidgetProviderInfo,
        target: WidgetAddTarget,
        zones: HomeZones,
    ): PagePlacement =
        when (target) {
            is WidgetAddTarget.Tap -> findSpot(info, target.zone, zones)
            is WidgetAddTarget.Drop -> findSpot(info, target.drop, zones)
            is WidgetAddTarget.Pin -> findSpot(info, target.drop, zones)
        }

    /** Places the bound widget [id] at [target], as [findSpot] would; nothing is recorded on a rejection. */
    suspend fun place(
        id: Int,
        target: WidgetAddTarget,
        zones: HomeZones,
    ): PagePlacement =
        when (target) {
            is WidgetAddTarget.Tap -> place(id, target.zone, zones)
            is WidgetAddTarget.Drop -> place(id, target.drop, zones)
            is WidgetAddTarget.Pin -> place(id, target.drop, zones)
        }

    /** Where a widget of [info] would go: [first], else the first Page zone with room, else a new Page ([PageLayouts.add]). */
    suspend fun findSpot(
        info: AppWidgetProviderInfo,
        first: PageZone?,
        zones: HomeZones,
    ): PagePlacement =
        layouts(dao.all(), zones).add(AppWidgetManager.INVALID_APPWIDGET_ID, info.spanBounds(zones.catalogGrid(pxPerDp), pxPerDp), first)

    /** Whether a widget of [info] still fits where it was dropped ([drop]): the placement, or a rejection. */
    suspend fun findSpot(
        info: AppWidgetProviderInfo,
        drop: WidgetDrop,
        zones: HomeZones,
    ): PagePlacement = placeOnDrop(dao.all(), AppWidgetManager.INVALID_APPWIDGET_ID, info, drop, zones)

    /**
     * Places the bound widget [id] where it was dropped ([drop]), moves the neighbours that drop
     * moved, and records it all. Nothing is recorded on a rejection.
     */
    suspend fun place(
        id: Int,
        drop: WidgetDrop,
        zones: HomeZones,
    ): PagePlacement =
        placing.withLock {
            val info = info(id) ?: return PagePlacement(null, LayoutResult.Rejected(Rejection.NoRoom))
            val rows = dao.all()
            val placed = placeOnDrop(rows, id, info, drop, zones)
            val result = placed.result
            val zone = placed.zone
            if (result is LayoutResult.Changed && zone != null) {
                val byId = rows.associateBy { it.appWidgetId }
                val moved = result.moved.mapNotNull { widget -> byId[widget.id]?.copy(zone = widget.zone, cells = widget.cells) }
                dao.insertAll(moved + WidgetRow(id, info.provider, zone.page, result.widget.zone, result.widget.cells))
                compactPages()
            }
            clearPending(id)
            placed
        }

    /**
     * Places the bound widget [id] at [first], else the first Page zone with room, else on a new
     * Page, and records it. Nothing is recorded on a rejection.
     */
    suspend fun place(
        id: Int,
        first: PageZone?,
        zones: HomeZones,
    ): PagePlacement =
        placing.withLock {
            val info = info(id) ?: return PagePlacement(null, LayoutResult.Rejected(Rejection.NoRoom))
            val placed = layouts(dao.all(), zones).add(id, info.spanBounds(zones.catalogGrid(pxPerDp), pxPerDp), first)
            val result = placed.result
            val zone = placed.zone
            if (result is LayoutResult.Changed && zone != null) {
                dao.insert(WidgetRow(id, info.provider, zone.page, result.widget.zone, result.widget.cells))
            }
            clearPending(id)
            placed
        }

    /** [drop] of widget [id] on the layouts of [rows]; a Page past the last one is a new, empty Page. */
    private fun placeOnDrop(
        rows: List<WidgetRow>,
        id: Int,
        info: AppWidgetProviderInfo,
        drop: WidgetDrop,
        zones: HomeZones,
    ): PagePlacement {
        val layouts = layouts(rows, zones)
        val page = drop.page.coerceAtMost(layouts.pages.size)
        val layout = layouts.pages.getOrNull(page) ?: layouts.newPage
        val result = layout.place(id, info.spanBounds(zones.catalogGrid(pxPerDp), pxPerDp), drop)
        return PagePlacement(PageZone(page, drop.zone).takeIf { result is LayoutResult.Changed }, result)
    }

    /** Takes a placed widget back out: drops its row and deletes its id. */
    suspend fun remove(id: Int) {
        dao.delete(listOf(id))
        host.deleteAppWidgetId(id)
        placing.withLock { compactPages() }
    }

    /**
     * Records the widgets of [page] where [layout] has them (a committed move with its reflow; a
     * widget moved in from another Page leaves that one), then removes Pages left empty.
     * Runs in the app scope, so leaving Home right after a drop still saves it.
     */
    fun save(
        page: Int,
        layout: WidgetLayout,
    ) {
        appScope.launch {
            placing.withLock {
                val rows = dao.all().associateBy { it.appWidgetId }
                val changed =
                    layout.widgets.mapNotNull { widget ->
                        rows[widget.id]?.takeIf { it.page != page || it.zone != widget.zone || it.cells != widget.cells }?.copy(
                            page = page,
                            zone = widget.zone,
                            cells = widget.cells,
                        )
                    }
                if (changed.isNotEmpty()) dao.insertAll(changed)
                compactPages()
            }
        }
    }

    /** Removes empty non-Home Pages (PRD 3.1): the Pages right of a gap move left. */
    private suspend fun compactPages() {
        val rows = dao.all()
        val moves = PageCompaction.moves(rows.associate { it.appWidgetId to it.page })
        if (moves.isNotEmpty()) dao.insertAll(rows.mapNotNull { row -> moves[row.appWidgetId]?.let { row.copy(page = it) } })
    }

    /**
     * Deletes placed widget [id] for good, row and id, in the app scope: once its undo window is
     * over (PRD 7.5), or when its Pin Request cannot be accepted.
     */
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
        placing.withLock { compactPages() }
        if (result.orphanHostIds.isNotEmpty() || result.orphanRows.isNotEmpty()) {
            Log.i(TAG, "Reconciled widgets: deleted ids ${result.orphanHostIds}, dropped rows ${result.orphanRows}")
        }
    }

    private companion object {
        const val TAG = "WidgetHost"
    }
}

/** What the widget add flow needs to know of this provider (PRD 7.4). */
internal fun AppWidgetProviderInfo.addable(): AddableWidget =
    AddableWidget(
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
): SpanBounds = spans(grid, pxPerDp).bounds

/** A widget being added: its future [row], its provider [info], and the neighbours its drop [moved]. */
class PendingWidget(
    val row: WidgetRow,
    val info: AppWidgetProviderInfo,
    val moved: List<LaidOutWidget> = emptyList(),
)
