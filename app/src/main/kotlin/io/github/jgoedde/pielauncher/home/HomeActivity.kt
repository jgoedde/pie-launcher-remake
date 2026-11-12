package io.github.jgoedde.pielauncher.home

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.drawer.offersWidgets
import io.github.jgoedde.pielauncher.core.home.EditMode
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.WidgetDrop
import io.github.jgoedde.pielauncher.core.home.WidgetLayout
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.core.icons.IconShapes
import io.github.jgoedde.pielauncher.drawer.AppDrawer
import io.github.jgoedde.pielauncher.drawer.DrawerEntry
import io.github.jgoedde.pielauncher.drawer.DrawerEntryActions
import io.github.jgoedde.pielauncher.drawer.DrawerViewModel
import io.github.jgoedde.pielauncher.drawer.rememberDrawerState
import io.github.jgoedde.pielauncher.icons.LocalIconShape
import io.github.jgoedde.pielauncher.icons.iconShapeOf
import io.github.jgoedde.pielauncher.pie.LaunchTarget
import io.github.jgoedde.pielauncher.pie.PieOverlay
import io.github.jgoedde.pielauncher.pie.PieViewModel
import io.github.jgoedde.pielauncher.pie.pieTouches
import io.github.jgoedde.pielauncher.pie.rememberPieController
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.PinnedShortcut
import io.github.jgoedde.pielauncher.platform.ShortcutPins
import io.github.jgoedde.pielauncher.platform.entryKey
import io.github.jgoedde.pielauncher.platform.wallpaperPickerIntent
import io.github.jgoedde.pielauncher.settings.SettingsActivity
import io.github.jgoedde.pielauncher.widgets.AddWidgetContract
import io.github.jgoedde.pielauncher.widgets.AddWidgetOutcome
import io.github.jgoedde.pielauncher.widgets.AddWidgetRequest
import io.github.jgoedde.pielauncher.widgets.HostedWidget
import io.github.jgoedde.pielauncher.widgets.PendingWidgetPreview
import io.github.jgoedde.pielauncher.widgets.WidgetDragHandOff
import io.github.jgoedde.pielauncher.widgets.WidgetDragPayload
import io.github.jgoedde.pielauncher.widgets.WidgetPickerContract
import io.github.jgoedde.pielauncher.widgets.WidgetPickerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An open empty-space menu: the long-pressed Widget Zone and the press point inside it. */
private data class EmptySpaceMenu(
    val zone: WidgetZoneId,
    val at: Offset,
)

class HomeActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }

    private val drawerViewModel: DrawerViewModel by viewModels {
        viewModelFactory {
            initializer {
                DrawerViewModel(
                    apps = graph.appsRepository.apps,
                    pinnedShortcuts = graph.appsRepository.pinnedShortcuts,
                    newBadges = graph.newBadges.active,
                    pendingRemovals = graph.shortcutPins.pendingRemovals,
                )
            }
        }
    }

    private val pieViewModel: PieViewModel by viewModels {
        viewModelFactory {
            initializer {
                PieViewModel(
                    config = graph.pieConfigStore.config,
                    apps = graph.appsRepository.apps,
                    launcherChanged = graph.appsRepository.launcherChanged,
                    resolver = graph.pieItemResolver,
                )
            }
        }
    }

    /** Home pressed while the launcher is already in front. */
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Whether the wallpaper wants dark status-bar icons (`WallpaperColors.HINT_SUPPORTS_DARK_TEXT`). */
    private val wallpaperWantsDarkIcons = mutableStateOf(false)
    private val wallpaperColorsListener =
        WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) wallpaperWantsDarkIcons.value = colors.wantsDarkIcons()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars with no scrim; icon colour is set below from the wallpaper or the Drawer.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false

        val wallpaperManager = WallpaperManager.getInstance(this)
        wallpaperWantsDarkIcons.value = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM).wantsDarkIcons()
        wallpaperManager.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))

        setContent {
            val iconSettings by graph.iconSettings.settings.collectAsStateWithLifecycle(initialValue = null)
            val iconShape = remember(iconSettings?.shapeKey) { iconShapeOf(IconShapes.byKey(iconSettings?.shapeKey)) }
            PieLauncherTheme {
                CompositionLocalProvider(LocalIconShape provides iconShape) {
                    val drawer = rememberDrawerState()
                    val drawerEntries by drawerViewModel.entries.collectAsStateWithLifecycle()
                    val snackbars = remember { SnackbarHostState() }
                    val scope = rememberCoroutineScope()
                    val pie = rememberPieController()
                    LaunchedEffect(pie) { pieViewModel.pie.collect { pie.pie = it } }

                    var emptySpaceMenu by remember { mutableStateOf<EmptySpaceMenu?>(null) }
                    val editor = remember { WidgetEditor(HomeWidgetEditActions(scope, snackbars)) }
                    val addWidget =
                        rememberLauncherForActivityResult(AddWidgetContract()) { outcome ->
                            val message =
                                when (outcome) {
                                    AddWidgetOutcome.NoRoom -> R.string.widget_no_space
                                    AddWidgetOutcome.TooLarge -> R.string.widget_too_large
                                    is AddWidgetOutcome.Added, AddWidgetOutcome.Cancelled -> null
                                }
                            if (message != null) scope.launch { snackbars.showSnackbar(getString(message)) }
                            // The new widget's Resize Frame opens at once (Launcher3).
                            if (outcome is AddWidgetOutcome.Added) editor.added(outcome.widgetId)
                        }
                    val pickWidget =
                        rememberLauncherForActivityResult(WidgetPickerContract()) { picked ->
                            if (picked != null) {
                                drawer.close()
                                addWidget.launch(AddWidgetRequest(picked.provider, picked.profile, WidgetZoneId.addOrder(picked.zone)))
                            }
                        }
                    val drawerActions =
                        remember(drawer) {
                            HomeDrawerActions(drawer::close, scope, snackbars) { app ->
                                pickWidget.launch(WidgetPickerRequest(packageName = app.packageName))
                            }
                        }
                    WidgetRemovalSnackbar(editor, snackbars)
                    BackHandler(enabled = editor.mode != EditMode.Idle, onBack = editor::leave)
                    val rows by graph.widgetHost.widgets.collectAsStateWithLifecycle()
                    val pending by graph.widgetHost.pending.collectAsStateWithLifecycle()
                    // The widget being configured shows as its preview in its target cells (PRD 7.4),
                    // with the neighbours its drop moved at their new cells.
                    val widgets =
                        remember(rows, pending) {
                            val adding = pending?.row?.takeIf { row -> rows.none { it.appWidgetId == row.appWidgetId } }
                            val moved = pending?.moved.orEmpty().associateBy { it.id }
                            val shown =
                                rows.map { row -> moved[row.appWidgetId]?.let { row.copy(zone = it.zone, cells = it.cells) } ?: row }
                            shown + listOfNotNull(adding)
                        }
                    val widgetDrops =
                        remember(editor, drawer) {
                            WidgetDropTarget(editor) { payload, drop ->
                                when (payload) {
                                    is WidgetDragPayload.Provider ->
                                        addWidget.launch(AddWidgetRequest(payload.provider, payload.profile, listOf(drop.zone), drop))
                                    is WidgetDragPayload.Pin ->
                                        WidgetDragHandOff.pins.take(payload.token)?.let { pin ->
                                            val info = pin.getAppWidgetProviderInfo(this@HomeActivity) ?: return@let
                                            addWidget.launch(AddWidgetRequest(info.provider, info.profile, listOf(drop.zone), drop, pin))
                                        }
                                }
                            }
                        }

                    LaunchedEffect(editor.mode) {
                        // A widget dragged in from the Widget Picker lands on the Home Page.
                        if ((editor.mode as? EditMode.Dragging)?.drag?.dragged?.isNew == true) {
                            emptySpaceMenu = null
                            drawer.close()
                        }
                    }

                    LaunchedEffect(drawer) {
                        homePresses.collect {
                            emptySpaceMenu = null
                            editor.leave()
                            drawer.close()
                        }
                    }

                    val drawerCoversBars by remember(drawer) { derivedStateOf { drawer.progress > 0.5f } }
                    val darkIcons = if (drawerCoversBars) !isSystemInDarkTheme() else wallpaperWantsDarkIcons.value
                    LaunchedEffect(darkIcons) {
                        WindowCompat.getInsetsController(window, window.decorView).run {
                            isAppearanceLightStatusBars = darkIcons
                            isAppearanceLightNavigationBars = darkIcons
                        }
                    }

                    // At rest the side edges belong to the launcher; with an overlay open, back closes it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { drawer.travelPx = it.height.toFloat() }
                            .closesResizeFrameOnTapOutside(editor)
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = { event ->
                                    WidgetDragHandOff.boundsOf(event.toAndroidDragEvent()) != null
                                },
                                target = widgetDrops,
                            ).excludesBackGestureAtEdges(
                                enabled = !drawer.isShown && emptySpaceMenu == null && editor.mode == EditMode.Idle,
                            ),
                    ) {
                        HomePage(
                            drawer,
                            onSwipeDown = graph.notificationShade::expand,
                            pieZone =
                                Modifier.pieTouches(pie, onLaunch = { target ->
                                    when (target) {
                                        is LaunchTarget.App -> graph.appLauncher.launch(target.app)
                                        is LaunchTarget.Shortcut -> graph.appLauncher.launch(target.shortcut)
                                    }
                                }),
                            widgets = widgets,
                            layoutOf = graph.widgetHost::layout,
                            widgetContent = { widget, _, _ ->
                                val adding = pending?.takeIf { it.row.appWidgetId == widget.id }
                                if (adding != null) {
                                    PendingWidgetPreview(graph.widgetHost, adding, Modifier.fillMaxSize())
                                } else {
                                    HostedWidget(
                                        host = graph.widgetHost,
                                        appWidgetId = widget.id,
                                        span = widget.cells.span,
                                        grid = graph.homeGeometry.catalogGrid(this@HomeActivity),
                                        onLightBackground = wallpaperWantsDarkIcons.value,
                                        touches = remember(widget.id) { editor.touchesOf(widget.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            },
                            onEmptyLongPress = { zone, at -> emptySpaceMenu = EmptySpaceMenu(zone, at) },
                            onZonesLaidOut = graph.homeGeometry::report,
                            editor = editor,
                            widgetOverlay = { widget ->
                                val selected = editor.mode as? EditMode.Selected
                                if (selected?.widgetId == widget.id && selected.popupOpen) {
                                    WidgetPopup(
                                        reconfigurable = remember(widget.id) { editor.isReconfigurable(widget.id) },
                                        onDismiss = editor::closePopup,
                                        onRemove = { editor.remove(widget.id) },
                                        onWidgetSettings = { editor.openSettings(widget.id) },
                                    )
                                }
                            },
                            zoneOverlay = { zone ->
                                emptySpaceMenu?.takeIf { it.zone == zone }?.let { menu ->
                                    EmptySpacePopup(
                                        at = menu.at,
                                        onDismiss = { emptySpaceMenu = null },
                                        onWallpaperStyle = { start(wallpaperPickerIntent()) },
                                        onWidgets = { pickWidget.launch(WidgetPickerRequest(zone = zone)) },
                                        onHomeSettings = { start(Intent(this@HomeActivity, SettingsActivity::class.java)) },
                                    )
                                }
                            },
                        )
                        ResizeFrame(editor)
                        AppDrawer(
                            state = drawer,
                            entries = drawerEntries,
                            iconLoader = graph.iconLoader,
                            actions = drawerActions,
                        )
                        PieOverlay(pie, graph.iconLoader)
                        DropTargetBar(editor, Modifier.align(Alignment.TopCenter))
                        DragPicture(editor)
                        SnackbarHost(
                            snackbars,
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                                ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Launching an entry or one of its Shortcuts closes the Drawer; system screens leave it open.
     * The first launch of an entry clears its New Badge.
     */
    private inner class HomeDrawerActions(
        private val closeDrawer: () -> Unit,
        private val scope: CoroutineScope,
        private val snackbars: SnackbarHostState,
        private val pickWidgets: (LauncherApp) -> Unit,
    ) : DrawerEntryActions {
        override fun launch(entry: DrawerEntry) {
            val launched =
                when (entry) {
                    is DrawerEntry.App -> graph.appLauncher.launch(entry.app)
                    is DrawerEntry.Shortcut -> graph.appLauncher.launch(entry.shortcut.info)
                }
            if (!launched) return
            if (entry.isNew) graph.newBadges.clear(entry.key)
            closeDrawer()
        }

        override suspend fun shortcutsOf(app: LauncherApp): List<ShortcutInfo> = graph.appShortcuts.of(app)

        override fun launch(shortcut: ShortcutInfo) {
            if (graph.appLauncher.launch(shortcut)) closeDrawer()
        }

        override fun openAppInfo(entry: DrawerEntry) {
            when (entry) {
                is DrawerEntry.App -> graph.appLauncher.openAppInfo(entry.app)
                is DrawerEntry.Shortcut -> graph.appLauncher.openAppInfo(entry.shortcut)
            }
        }

        /**
         * Hides the entry at once and offers Undo until [ShortcutPins.UNDO_WINDOW_MILLIS] runs out;
         * only then is the Shortcut unpinned. The snackbar is dismissed when the window closes.
         */
        override fun remove(shortcut: PinnedShortcut) {
            val pins = graph.shortcutPins
            pins.remove(shortcut.key)
            graph.newBadges.clear(shortcut.key.entryKey)
            scope.launch {
                val shown =
                    launch {
                        val result =
                            snackbars.showSnackbar(
                                message = getString(R.string.drawer_removed, shortcut.label),
                                actionLabel = getString(R.string.drawer_undo),
                                duration = SnackbarDuration.Indefinite,
                            )
                        if (result == SnackbarResult.ActionPerformed) pins.undoRemoval(shortcut.key)
                    }
                pins.pendingRemovals.first { shortcut.key !in it }
                shown.cancel()
            }
        }

        override fun uninstall(app: LauncherApp) {
            graph.appLauncher.uninstall(app)
        }

        override suspend fun hasWidgets(app: LauncherApp): Boolean =
            withContext(Dispatchers.IO) {
                offersWidgets(app.packageName, graph.widgetHost.pickerProviders().map { it.provider.packageName })
            }

        /** The picked widget goes to the Home Page, upper Widget Zone first (no zone in the request). */
        override fun openWidgets(app: LauncherApp) = pickWidgets(app)
    }

    /** Edit Mode's effects outside the Home Page: the stored layout, the widget ids, messages. */
    private inner class HomeWidgetEditActions(
        private val scope: CoroutineScope,
        private val snackbars: SnackbarHostState,
    ) : WidgetEditActions {
        override fun save(layout: WidgetLayout) = graph.widgetHost.save(layout)

        override fun discard(id: Int) = graph.widgetHost.discard(id)

        override fun isReconfigurable(id: Int): Boolean = graph.widgetHost.isReconfigurable(id)

        /** Reopens the configure screen (PRD 7.2); its result does not matter: a cancel keeps the widget as it was. */
        override fun reconfigure(id: Int) {
            // The configure IntentSender carries no background-start rights of its own; the visible launcher grants them.
            val options =
                ActivityOptions
                    .makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                    .toBundle()
            try {
                graph.widgetHost.startConfigure(this@HomeActivity, id, REQUEST_RECONFIGURE, options)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Configure activity missing for widget $id", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Configure activity not allowed for widget $id", e)
            }
        }

        override fun rejected(reason: Rejection) {
            scope.launch { snackbars.showSnackbar(getString(R.string.widget_no_space)) }
        }
    }

    override fun onStart() {
        super.onStart()
        graph.widgetHost.startListening()
    }

    override fun onStop() {
        graph.widgetHost.stopListening()
        super.onStop()
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing handles $intent", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperColorsListener)
        graph.widgetHost.releaseViews()
        super.onDestroy()
    }
}

private const val TAG = "HomeActivity"

/**
 * Home's end of the widget drag hand-off (#34 "Home drag target"): a widget dragged in from the
 * Widget Picker or the pin sheet becomes the Edit Mode move of a new widget, with
 * the same reflow. Dropped on the Widget Zones, it goes to [add] with where it landed.
 */
private class WidgetDropTarget(
    private val editor: WidgetEditor,
    private val add: (WidgetDragPayload, WidgetDrop) -> Unit,
) : DragAndDropTarget {
    override fun onStarted(event: DragAndDropEvent) {
        WidgetDragHandOff.boundsOf(event.toAndroidDragEvent())?.let(editor::startNewDrag)
    }

    override fun onEntered(event: DragAndDropEvent) = editor.dragNewTo(event.finger)

    override fun onMoved(event: DragAndDropEvent) = editor.dragNewTo(event.finger)

    override fun onExited(event: DragAndDropEvent) = editor.dragNewTo(null)

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val payload = WidgetDragHandOff.payloadOf(event.toAndroidDragEvent())
        editor.dragNewTo(event.finger)
        val drop = editor.dropNew()
        if (payload == null || drop == null) return false
        add(payload, drop)
        return true
    }

    override fun onEnded(event: DragAndDropEvent) = editor.endNewDrag()
}

/** Below the `ActivityResultRegistry` range, so the registry never claims this result. */
private const val REQUEST_RECONFIGURE = 1

/**
 * The "Item removed" snackbar with "Undo" (PRD 7.5) for the removal waiting for its undo window;
 * the widget is deleted for good when the window runs out. A new removal or an Undo replaces it.
 */
@Composable
private fun WidgetRemovalSnackbar(
    editor: WidgetEditor,
    snackbars: SnackbarHostState,
) {
    val message = stringResource(R.string.widget_removed)
    val undo = stringResource(R.string.widget_undo)
    LaunchedEffect(editor.removal) {
        val due = editor.removal.dueAtMs ?: return@LaunchedEffect
        launch {
            val result = snackbars.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) editor.undoRemoval()
        }
        delay((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
        editor.expireRemoval()
    }
}

private fun WallpaperColors?.wantsDarkIcons(): Boolean = this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0

/** The finger of a drag event, relative to the Compose root (the whole window here, edge to edge). */
private val DragAndDropEvent.finger: Offset get() = toAndroidDragEvent().let { Offset(it.x, it.y) }
