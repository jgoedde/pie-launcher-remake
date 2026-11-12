package io.github.jgoedde.pielauncher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import java.text.DateFormat
import java.util.Date

/** What the Launcher Settings screen shows. [status] is null until the Pie Config is decoded. */
data class SettingsState(
    val isDefaultHome: Boolean,
    val status: ConfigStatus?,
    val versionName: String,
    val versionCode: Long,
)

interface SettingsActions {
    fun requestDefaultHome()

    fun showStatus()

    fun importConfig()

    fun exportConfig()

    fun exportInventory()

    fun resetConfig()

    fun openWallpaperStyle()

    fun openGitHub()

    fun openLicenses()
}

/**
 * The one Launcher Settings screen (PRD 10.2): a `LargeTopAppBar` that collapses on scroll over
 * categories whose rows sit in one grouped container each, Android 16/17 Settings style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    SettingsScaffold(stringResource(R.string.settings_title), scrollBehavior, onBack = null) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom =
                        padding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.isDefaultHome) item { DefaultHomeBanner(actions::requestDefaultHome) }
            item {
                SettingsGroup(
                    stringResource(R.string.settings_category_pie_config),
                    listOf(
                        SettingsRow(statusText(state.status), null, actions::showStatus),
                        SettingsRow(
                            stringResource(R.string.settings_import),
                            stringResource(R.string.settings_import_supporting),
                            actions::importConfig,
                        ),
                        SettingsRow(
                            stringResource(R.string.settings_export_config),
                            stringResource(R.string.settings_export_config_supporting),
                            actions::exportConfig,
                        ),
                        SettingsRow(
                            stringResource(R.string.settings_export_inventory),
                            stringResource(R.string.settings_export_inventory_supporting),
                            actions::exportInventory,
                        ),
                        SettingsRow(stringResource(R.string.settings_reset), null, actions::resetConfig, destructive = true),
                    ),
                )
            }
            item {
                SettingsGroup(
                    stringResource(R.string.settings_category_appearance),
                    listOf(
                        SettingsRow(
                            stringResource(R.string.settings_wallpaper_style),
                            stringResource(R.string.settings_wallpaper_style_supporting),
                            actions::openWallpaperStyle,
                        ),
                    ),
                )
            }
            item { Footer(state, actions) }
        }
    }
}

/** Shared by the main screen and the licenses page; [onBack] adds the back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)?,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) BackButton(onBack) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        content = content,
    )
}

@Composable
private fun statusText(status: ConfigStatus?): String {
    val record = status?.record
    val layerCount = status?.layerCount
    return when {
        status == null -> stringResource(R.string.settings_status_loading)
        layerCount == null -> stringResource(R.string.settings_status_broken)
        record == null -> stringResource(R.string.settings_status_default)
        else ->
            stringResource(
                R.string.settings_status_imported,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.importedAtMillis)),
                pluralStringResource(R.plurals.layers, layerCount, layerCount),
                pluralStringResource(R.plurals.warnings, record.warnings.size, record.warnings.size),
            )
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        shape = RoundedCornerShape(OUTER_CORNER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_default_home_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_default_home_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
            ) { Text(stringResource(R.string.settings_default_home_action)) }
        }
    }
}

private data class SettingsRow(
    val headline: String,
    val supporting: String?,
    val onClick: () -> Unit,
    /** Shown in `error` colour. */
    val destructive: Boolean = false,
)

/** A category heading over its rows; first and last row more rounded, small gaps between rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(
    title: String,
    rows: List<SettingsRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        rows.forEachIndexed { i, row ->
            SegmentedListItem(
                onClick = row.onClick,
                shapes = ListItemDefaults.segmentedShapes(index = i, count = rows.size),
                supportingContent = row.supporting?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(row.headline, color = if (row.destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        }
    }
}

/** One small line: version · GitHub · Open-source licenses. */
@Composable
private fun Footer(
    state: SettingsState,
    actions: SettingsActions,
) {
    val style = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val link = MaterialTheme.colorScheme.primary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        Text(stringResource(R.string.settings_version, state.versionName, state.versionCode), style = style, color = muted)
        Text("·", style = style, color = muted)
        Text(
            stringResource(R.string.settings_github),
            style = style,
            color = link,
            modifier = Modifier.clickable(onClick = actions::openGitHub),
        )
        Text("·", style = style, color = muted)
        Text(
            stringResource(R.string.settings_licenses),
            style = style,
            color = link,
            modifier = Modifier.clickable(onClick = actions::openLicenses),
        )
    }
}

/** The licenses sub-page: the bundled `NOTICE`, as is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    notice: String,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    SettingsScaffold(stringResource(R.string.settings_licenses), scrollBehavior, onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val OUTER_CORNER = 20.dp
