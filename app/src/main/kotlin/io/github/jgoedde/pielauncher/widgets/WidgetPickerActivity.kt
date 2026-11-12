package io.github.jgoedde.pielauncher.widgets

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CatalogWidget
import io.github.jgoedde.pielauncher.core.home.PickerApp
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetPickerState
import io.github.jgoedde.pielauncher.core.home.WidgetSpans
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import io.github.jgoedde.pielauncher.home.PieLauncherTheme
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.icons.LauncherIcon
import io.github.jgoedde.pielauncher.icons.LauncherIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * What opens the Widget Picker: the long-pressed Widget Zone (null from the App Drawer) and, to
 * filter it to one app, that app's [packageName].
 */
data class WidgetPickerRequest(
    val zone: WidgetZoneId? = null,
    val packageName: String? = null,
)

/** The widget tapped to add, and the zone the picker was opened for. */
data class PickedWidget(
    val provider: ComponentName,
    val profile: UserHandle,
    val zone: WidgetZoneId?,
)

/** Runs [WidgetPickerActivity]; null when it closed without adding anything. */
class WidgetPickerContract : ActivityResultContract<WidgetPickerRequest, PickedWidget?>() {
    override fun createIntent(
        context: Context,
        input: WidgetPickerRequest,
    ): Intent =
        Intent(context, WidgetPickerActivity::class.java)
            .putExtra(EXTRA_ZONE, input.zone?.name)
            .putExtra(EXTRA_PACKAGE_NAME, input.packageName)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): PickedWidget? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        val provider = intent.getParcelableExtra(EXTRA_PROVIDER, ComponentName::class.java) ?: return null
        val profile = intent.getParcelableExtra(EXTRA_PROFILE, UserHandle::class.java) ?: return null
        return PickedWidget(provider, profile, zoneOf(intent))
    }

    companion object {
        /** Filters the picker to one app (Launcher3 `EXTRA_PACKAGE_NAME`). */
        const val EXTRA_PACKAGE_NAME = "package_name"
        internal const val EXTRA_ZONE = "zone"
        internal const val EXTRA_PROVIDER = "provider"
        internal const val EXTRA_PROFILE = "profile"

        internal fun zoneOf(intent: Intent): WidgetZoneId? =
            intent.getStringExtra(EXTRA_ZONE)?.let { name -> WidgetZoneId.entries.firstOrNull { it.name == name } }
    }
}

/**
 * The Widget Picker (ADR 0006, PRD 7.4): its own Activity with a bottom sheet titled "Widgets",
 * searchable, one expandable header per app, preview tiles, greyed-out too-large widgets and
 * tap-to-add. "Add" returns the provider to Home, which binds, configures and places it. Closes
 * on swipe down, back or screen lock.
 */
class WidgetPickerActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }

    private val viewModel: WidgetPickerViewModel by viewModels {
        viewModelFactory {
            initializer {
                WidgetPickerViewModel(
                    host = graph.widgetHost,
                    context = application,
                    grid = graph.homeGeometry.catalogGrid(this@WidgetPickerActivity),
                    onlyPackage = intent.getStringExtra(WidgetPickerContract.EXTRA_PACKAGE_NAME),
                )
            }
        }
    }

    private val screenOff =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        setContent {
            PieLauncherTheme {
                val catalog by viewModel.catalog.collectAsStateWithLifecycle()
                val state by viewModel.state.collectAsStateWithLifecycle()
                WidgetPickerSheet(
                    catalog = catalog,
                    state = state,
                    grid = viewModel.grid,
                    iconLoader = graph.iconLoader,
                    preview = viewModel::preview,
                    onState = viewModel::update,
                    onAdd = ::add,
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun add(widget: PickerWidget) {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(WidgetPickerContract.EXTRA_PROVIDER, widget.info.provider)
                .putExtra(WidgetPickerContract.EXTRA_PROFILE, widget.info.profile)
                .putExtra(WidgetPickerContract.EXTRA_ZONE, WidgetPickerContract.zoneOf(intent)?.name),
        )
        finish()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }
}

/** One provider as the picker lists it, with its spans on the home grid. */
class PickerWidget(
    val info: AppWidgetProviderInfo,
    val label: String,
    val description: String,
    val spans: WidgetSpans,
)

class WidgetPickerViewModel(
    private val host: WidgetHost,
    private val context: Application,
    val grid: CatalogGrid,
    onlyPackage: String?,
) : ViewModel() {
    private val catalogFlow = MutableStateFlow<List<CatalogWidget<String, PickerWidget>>?>(null)

    /** Every listed provider; null while loading. */
    val catalog: StateFlow<List<CatalogWidget<String, PickerWidget>>?> = catalogFlow.asStateFlow()

    private val stateFlow = MutableStateFlow(WidgetPickerState<String, PickerWidget>(onlyApp = onlyPackage))
    val state: StateFlow<WidgetPickerState<String, PickerWidget>> = stateFlow.asStateFlow()

    private val previews = ConcurrentHashMap<ComponentName, PreviewContent>()

    init {
        viewModelScope.launch { catalogFlow.value = withContext(Dispatchers.IO) { load() } }
    }

    fun update(change: (WidgetPickerState<String, PickerWidget>) -> WidgetPickerState<String, PickerWidget>) = stateFlow.update(change)

    /** The preview of [widget], loaded once per picker. */
    suspend fun preview(
        widget: PickerWidget,
        widthPx: Int,
    ): PreviewContent =
        previews[widget.info.provider]
            ?: loadWidgetPreview(context, host, widget.info, widthPx).also { previews[widget.info.provider] = it }

    private fun load(): List<CatalogWidget<String, PickerWidget>> {
        val pm = context.packageManager
        val appNames = mutableMapOf<String, String>()
        return host.pickerProviders().map { info ->
            val pkg = info.provider.packageName
            val appName =
                appNames.getOrPut(pkg) {
                    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                }
            val label = info.loadLabel(pm)
            val description = info.loadDescription(context)?.toString().orEmpty()
            CatalogWidget(pkg, appName, PickerWidget(info, label, description, host.spans(info, grid)), label, description)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPickerSheet(
    catalog: List<CatalogWidget<String, PickerWidget>>?,
    state: WidgetPickerState<String, PickerWidget>,
    grid: CatalogGrid,
    iconLoader: IconLoader,
    preview: suspend (PickerWidget, Int) -> PreviewContent,
    onState: ((WidgetPickerState<String, PickerWidget>) -> WidgetPickerState<String, PickerWidget>) -> Unit,
    onAdd: (PickerWidget) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.widget_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = { query -> onState { it.search(query) } },
                placeholder = { Text(stringResource(R.string.widget_picker_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val locale = LocalLocale.current.platformLocale
            val apps = catalog?.let { state.list(it, locale) }.orEmpty()
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                apps.forEach { app ->
                    item(key = app.app) {
                        AppHeader(app, iconLoader, enabled = state.onlyApp == null && state.query.isBlank()) {
                            onState { it.tapHeader(app.app) }
                        }
                    }
                    if (app.expanded) {
                        items(app.widgets, key = { it.info.provider.flattenToString() }) { widget ->
                            PickerTile(widget, grid, showAdd = state.selected == widget, preview, onState, onAdd)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    app: PickerApp<String, PickerWidget>,
    iconLoader: IconLoader,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    val iconPx = with(LocalDensity.current) { APP_ICON_SIZE.roundToPx() }
    val icon by produceState<LauncherIcon?>(null, app.app) {
        value = iconLoader.ownerIcon(app.app, Process.myUserHandle(), iconPx)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LauncherIconImage(icon, APP_ICON_SIZE)
        Column {
            Text(app.appName, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.widget_count, app.count, app.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickerTile(
    widget: PickerWidget,
    grid: CatalogGrid,
    showAdd: Boolean,
    preview: suspend (PickerWidget, Int) -> PreviewContent,
    onState: ((WidgetPickerState<String, PickerWidget>) -> WidgetPickerState<String, PickerWidget>) -> Unit,
    onAdd: (PickerWidget) -> Unit,
) {
    val span = widget.spans.default
    val density = LocalDensity.current
    val widthPx =
        with(density) {
            WidgetCatalog
                .sizeList(span, grid)
                .first()
                .width.dp
                .roundToPx()
        }
    val content by produceState<PreviewContent?>(null, widget.info.provider) { value = preview(widget, widthPx) }
    WidgetTile(
        label = widget.label,
        span = span,
        description = widget.description,
        tooLarge = widget.spans.tooLarge,
        showAdd = showAdd,
        onTap = { onState { it.tapTile(widget, widget.spans.tooLarge) } },
        onAdd = { onAdd(widget) },
        preview = { WidgetPreview(content, span, grid) },
    )
}

private val APP_ICON_SIZE = 40.dp
