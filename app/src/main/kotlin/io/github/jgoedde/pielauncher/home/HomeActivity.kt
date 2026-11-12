package io.github.jgoedde.pielauncher.home

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
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
import io.github.jgoedde.pielauncher.widgets.HostedWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                    val drawerActions = remember(drawer) { HomeDrawerActions(drawer::close, scope, snackbars) }
                    val pie = rememberPieController()
                    LaunchedEffect(pie) { pieViewModel.pie.collect { pie.pie = it } }

                    var emptySpaceMenu by remember { mutableStateOf<EmptySpaceMenu?>(null) }
                    val addWidget =
                        rememberLauncherForActivityResult(AddWidgetContract()) { outcome ->
                            if (outcome == AddWidgetOutcome.NoSpace) {
                                scope.launch { snackbars.showSnackbar(getString(R.string.widget_no_space)) }
                            }
                        }
                    val widgets by graph.widgetHost.widgets.collectAsStateWithLifecycle()

                    LaunchedEffect(drawer) {
                        homePresses.collect {
                            emptySpaceMenu = null
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
                            .excludesBackGestureAtEdges(enabled = !drawer.isShown && emptySpaceMenu == null),
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
                                HostedWidget(
                                    host = graph.widgetHost,
                                    appWidgetId = widget.id,
                                    span = widget.cells.span,
                                    grid = graph.homeGeometry.catalogGrid(this@HomeActivity),
                                    onLightBackground = wallpaperWantsDarkIcons.value,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            onEmptyLongPress = { zone, at -> emptySpaceMenu = EmptySpaceMenu(zone, at) },
                            onZonesLaidOut = graph.homeGeometry::report,
                            zoneOverlay = { zone ->
                                emptySpaceMenu?.takeIf { it.zone == zone }?.let { menu ->
                                    EmptySpacePopup(
                                        at = menu.at,
                                        onDismiss = { emptySpaceMenu = null },
                                        onWallpaperStyle = { start(wallpaperPickerIntent()) },
                                        onWidgets = { addWidget.launch(zone) },
                                        onHomeSettings = { start(Intent(this@HomeActivity, SettingsActivity::class.java)) },
                                    )
                                }
                            },
                        )
                        AppDrawer(
                            state = drawer,
                            entries = drawerEntries,
                            iconLoader = graph.iconLoader,
                            actions = drawerActions,
                        )
                        PieOverlay(pie, graph.iconLoader)
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

private fun WallpaperColors?.wantsDarkIcons(): Boolean = this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
