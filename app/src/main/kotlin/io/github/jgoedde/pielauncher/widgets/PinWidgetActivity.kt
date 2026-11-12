package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.CellRect
import io.github.jgoedde.pielauncher.home.PieLauncherTheme
import kotlinx.coroutines.launch

/**
 * App-initiated widget pinning (`requestPinAppWidget`, PRD 7.6). A sheet shows the widget's name
 * and size; Add binds it, accepts the request and places it at the first free spot on the Home
 * Page, upper Widget Zone first. With no free spot the sheet says "No space" and offers no Add.
 */
class PinWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }

    private var request: LauncherApps.PinItemRequest? = null
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** The sheet's content once the free spot is known; null cells means "No space". */
    private var sheet by mutableStateOf<PinSheet?>(null)

    private val bind =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) accept() else cancel()
        }

    private val targets: List<ZoneTarget>
        get() =
            listOf(WidgetZoneId.HomeUpper, WidgetZoneId.HomeLower).map { zone ->
                ZoneTarget(HOME_PAGE, zone, graph.homeGeometry.grid(this, zone))
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (pin == null || pin.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET || !pin.isValid) return finish()
        val info = pin.getAppWidgetProviderInfo(this) ?: return finish()
        request = pin
        pendingId = savedInstanceState?.getInt(STATE_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        lifecycleScope.launch {
            sheet = PinSheet(info.loadLabel(packageManager), widgets.findSpot(targets, info)?.cells)
        }
        setContent {
            PieLauncherTheme {
                sheet?.let { PinWidgetSheet(it, onAdd = ::add, onDismiss = ::cancel) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ID, pendingId)
    }

    private fun add() {
        val info = request?.getAppWidgetProviderInfo(this) ?: return cancel()
        pendingId = widgets.allocateId()
        if (widgets.bindIfAllowed(pendingId, info.profile, info.provider)) return accept()
        bind.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
        )
    }

    /**
     * Places the bound widget first and accepts the request only once it has a spot, so the
     * requesting app never hears "added" for a widget that found no room.
     */
    private fun accept() {
        val pin = request ?: return cancel()
        val id = pendingId
        val label = sheet?.label
        lifecycleScope.launch {
            if (!widgets.place(id, targets)) {
                Toast.makeText(application, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
                return@launch cancel()
            }
            val accepted =
                try {
                    pin.isValid && pin.accept(bundleOf(AppWidgetManager.EXTRA_APPWIDGET_ID to id))
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Cannot accept widget Pin Request", e)
                    false
                }
            pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (accepted) {
                Toast.makeText(application, getString(R.string.pin_widget_added, label), Toast.LENGTH_SHORT).show()
            } else {
                widgets.remove(id)
            }
            finish()
        }
    }

    /** Destroyed mid-flow (e.g. by Home) without Add finishing: the bound id must not leak. */
    override fun onDestroy() {
        if (isFinishing && pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) widgets.deleteId(pendingId)
        super.onDestroy()
    }

    private fun cancel() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) widgets.deleteId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        finish()
    }

    private companion object {
        const val TAG = "PinWidgetActivity"
        const val STATE_ID = "widget_id"
    }
}

private class PinSheet(
    val label: CharSequence,
    val cells: CellRect?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinWidgetSheet(
    sheet: PinSheet,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.pin_widget_title), style = MaterialTheme.typography.titleLarge)
            Text(sheet.label.toString(), style = MaterialTheme.typography.bodyLarge)
            val cells = sheet.cells
            Text(
                if (cells != null) {
                    stringResource(R.string.pin_widget_size, cells.width, cells.height)
                } else {
                    stringResource(R.string.widget_no_space)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (cells != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_widget_cancel)) }
                Button(onClick = onAdd, enabled = cells != null) { Text(stringResource(R.string.pin_widget_add)) }
            }
        }
    }
}
