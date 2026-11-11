package io.github.jgoedde.pielauncher.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.platform.LauncherApp

private val IconSize = 48.dp

/**
 * The App Drawer overlay. Slides up with [state]; while it is closed nothing is composed.
 *
 * Closes via back, a downward swipe while the list is at the top, or launching an entry. Each
 * open starts scrolled to the top.
 */
@Composable
fun AppDrawer(
    state: DrawerState,
    entries: List<LauncherApp>,
    iconLoader: IconLoader,
    onLaunch: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) listState.scrollToItem(0)
    }
    BackHandler(enabled = !state.isClosed) { state.close() }
    if (state.progress == 0f) return

    val pullToClose = remember(state) { PullToCloseConnection(state) }
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - state.progress) * size.height }
                .nestedScroll(pullToClose),
        color = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = { it.componentName.flattenToString() }) { app ->
                DrawerEntryRow(app, iconLoader, onClick = { onLaunch(app) })
            }
        }
    }
}

@Composable
private fun DrawerEntryRow(
    app: LauncherApp,
    iconLoader: IconLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(app, iconLoader)
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppIcon(
    app: LauncherApp,
    iconLoader: IconLoader,
) {
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    val icon: ImageBitmap? by produceState(iconLoader.cached(app, sizePx), app, sizePx) {
        if (value == null) value = iconLoader.originalIcon(app, sizePx)
    }
    Box(Modifier.size(IconSize)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Turns a downward drag that the list can no longer scroll (it is at the top) into closing the
 * Drawer, and an upward drag into reopening it before the list scrolls again.
 */
private class PullToCloseConnection(
    private val state: DrawerState,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || state.progress >= 1f) return Offset.Zero
        val before = state.progress
        state.dragBy(available.y)
        return Offset(0f, -(state.progress - before) * state.travelPx)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        state.dragBy(available.y)
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.progress >= 1f) return Velocity.Zero
        state.settle(available.y)
        return available
    }
}
