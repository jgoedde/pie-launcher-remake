package io.github.jgoedde.pielauncher.home

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.drawer.AppDrawer
import io.github.jgoedde.pielauncher.drawer.DrawerViewModel
import io.github.jgoedde.pielauncher.drawer.rememberDrawerState
import kotlinx.coroutines.flow.MutableSharedFlow

class HomeActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }

    private val drawerViewModel: DrawerViewModel by viewModels {
        viewModelFactory { initializer { DrawerViewModel(graph.appsRepository.apps) } }
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
            PieLauncherTheme {
                val drawer = rememberDrawerState()
                val entries by drawerViewModel.entries.collectAsStateWithLifecycle()

                LaunchedEffect(drawer) { homePresses.collect { drawer.close() } }

                val drawerCoversBars by remember(drawer) { derivedStateOf { drawer.progress > 0.5f } }
                val darkIcons = if (drawerCoversBars) !isSystemInDarkTheme() else wallpaperWantsDarkIcons.value
                LaunchedEffect(darkIcons) {
                    WindowCompat.getInsetsController(window, window.decorView).run {
                        isAppearanceLightStatusBars = darkIcons
                        isAppearanceLightNavigationBars = darkIcons
                    }
                }

                Box(Modifier.fillMaxSize().onSizeChanged { drawer.travelPx = it.height.toFloat() }) {
                    HomePage(drawer)
                    AppDrawer(
                        state = drawer,
                        entries = entries,
                        iconLoader = graph.iconLoader,
                        onLaunch = { app -> if (graph.appLauncher.launch(app)) drawer.close() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.tryEmit(Unit)
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperColorsListener)
        super.onDestroy()
    }
}

private fun WallpaperColors?.wantsDarkIcons(): Boolean = this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
