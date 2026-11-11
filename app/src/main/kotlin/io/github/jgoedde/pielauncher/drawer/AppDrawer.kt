package io.github.jgoedde.pielauncher.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.platform.LauncherApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The App Drawer overlay. Slides up with [state]; while it is closed nothing is composed, so every
 * open starts with an empty query and the list scrolled to the top.
 *
 * Closes via back, a downward swipe while the list is at the top, or launching an entry. Once
 * fully open it focuses the search field and shows the keyboard; scrolling the list hides it.
 */
@Composable
fun AppDrawer(
    state: DrawerState,
    entries: DrawerEntries,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isShown) { state.close() }
    if (!state.isShown) return

    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(entries, query) { entries.search(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        snapshotFlow { state.progress }.first { it >= 1f }
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) keyboard?.hide() }
    }

    val pullToClose = remember(state) { PullToCloseConnection(state) }
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - state.progress) * size.height }
                .nestedScroll(pullToClose),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            SearchField(
                query = query,
                onQueryChange = {
                    // Results change identity wholesale; keep the best hit at the top instead of the
                    // previously first visible entry.
                    listState.requestScrollToItem(0)
                    query = it
                },
                onGo = { if (searching) results.firstOrNull()?.let(actions::launch) },
                modifier = Modifier.dragsDrawer(state).focusRequester(focus),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (searching && results.isEmpty()) {
                    // Not scrollable, so it drags the Drawer itself; centred in the room above the keyboard.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .dragsDrawer(state)
                            .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()),
                        contentAlignment = Alignment.Center,
                    ) { NoResults(query) }
                } else {
                    EntryList(results, listState, highlightFirst = searching, iconLoader, actions)
                }
                if (!searching) {
                    FastScroller(
                        letters = entries.letters,
                        onDragStart = { keyboard?.hide() },
                        onLetter = { letter -> scope.launch { listState.scrollToItem(letter.firstIndex) } },
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun EntryList(
    entries: List<LauncherApp>,
    listState: LazyListState,
    highlightFirst: Boolean,
    iconLoader: IconLoader,
    actions: DrawerEntryActions,
) {
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
    LazyColumn(
        state = listState,
        // Room on the right for the fast-scroller.
        contentPadding = PaddingValues(top = 4.dp, end = FastScrollerWidth, bottom = bottom.calculateBottomPadding()),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(entries, key = { _, app -> app.componentName.flattenToString() }) { i, app ->
            DrawerEntryRow(app, highlighted = highlightFirst && i == 0, iconLoader, actions)
        }
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
