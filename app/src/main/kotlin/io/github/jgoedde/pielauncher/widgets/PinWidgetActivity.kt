package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.PinAdd
import io.github.jgoedde.pielauncher.core.home.PinSheet
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetSpans
import io.github.jgoedde.pielauncher.home.PieLauncherTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * App-initiated widget pinning (`requestPinAppWidget`, PRD 7.6, Launcher3 `AddItemActivity`): a
 * sheet titled with the app's name and the hint "Touch and hold to move a widget.", the widget's
 * preview card (an app-supplied preview wins), "Cancel" and "Add to home screen".
 *
 * "Add to home screen" binds the widget (the system bind dialog when not allow-listed; a
 * cancelled bind keeps the sheet), places it at the first free spot of the Home Page, upper
 * Widget Zone first, and accepts the request with the size options and the widget id. It is
 * disabled with the reason when the widget has no room or is too large, both known before the
 * sheet shows. Touch and hold on the preview drags the widget onto Home instead, which binds,
 * places and accepts it on drop. The launcher never configures a pinned widget.
 */
class PinWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }

    private var request: LauncherApps.PinItemRequest? = null
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** The sheet's content once spans and the spot are known. */
    private var sheet by mutableStateOf<PinSheetContent?>(null)
    private var preview by mutableStateOf<PreviewContent?>(null)

    private val bind =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                accept()
            } else {
                // Launcher3: the id goes, the sheet stays.
                deletePending()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (pin == null || pin.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET || !pin.isValid) return finish()
        val info = pin.getAppWidgetProviderInfo(this) ?: return finish()
        request = pin
        pendingId = savedInstanceState?.getInt(STATE_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val grid = graph.homeGeometry.catalogGrid(this)
        lifecycleScope.launch {
            val spot = widgets.findSpot(info, PinSheet.TARGETS, graph.homeGeometry.zones(this@PinWidgetActivity))
            sheet = load(info, grid, PinSheet.add(spot))
        }
        lifecycleScope.launch {
            val spans = widgets.spans(info, grid)
            val widthPx = (WidgetCatalog.sizeList(spans.default, grid).first().width * resources.displayMetrics.density).roundToInt()
            val appSupplied = pin.extras?.getParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews::class.java)
            preview = loadWidgetPreview(this@PinWidgetActivity, widgets, info, widthPx, appSupplied)
        }
        setContent {
            PieLauncherTheme {
                sheet?.let { PinWidgetSheet(it, preview, onAdd = ::add, onPickUp = ::pickUp, onDismiss = ::cancel) }
            }
        }
    }

    private fun load(
        info: AppWidgetProviderInfo,
        grid: CatalogGrid,
        add: PinAdd,
    ): PinSheetContent {
        val pkg = info.provider.packageName
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
        return PinSheetContent(
            appName = appName.getOrDefault(pkg),
            label = info.loadLabel(packageManager),
            description = info.loadDescription(this)?.toString().orEmpty(),
            spans = widgets.spans(info, grid),
            grid = grid,
            add = add,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ID, pendingId)
    }

    private fun add() {
        val info = request?.getAppWidgetProviderInfo(this) ?: return cancel()
        // The widget's size at the spot the sheet found, so it lays out right from its first update.
        val ready = sheet?.add as? PinAdd.Ready ?: return
        pendingId = widgets.allocateId()
        val ask = widgets.bindOrAsk(pendingId, info, widgets.sizeOptionsFor(ready.span, this)) ?: return accept()
        bind.launch(ask)
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
            val placed = widgets.place(id, PinSheet.TARGETS, graph.homeGeometry.zones(this@PinWidgetActivity))
            if (placed !is LayoutResult.Changed) {
                Toast.makeText(application, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
                return@launch cancel()
            }
            val options = widgets.sizeOptionsFor(placed.widget.cells.span, this@PinWidgetActivity)
            options.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val accepted =
                try {
                    pin.isValid && pin.accept(options)
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

    /**
     * Touch and hold on the preview: a system drag of the widget starts from [view], drawn as
     * [shadow], Home comes to the front and the sheet closes. The request waits in
     * [WidgetDragHandOff.pins] for Home's drop, which binds, places and accepts it.
     */
    private fun pickUp(
        view: View,
        shadow: Bitmap,
    ) {
        val pin = request ?: return
        val spans = sheet?.spans ?: return
        val bounds = SpanBounds(spans.default, spans.min, spans.max, spans.resizesHorizontally, spans.resizesVertically)
        val token = WidgetDragHandOff.pins.hold(pin)
        if (!WidgetDragHandOff.start(view, WidgetDragPayload.Pin(token), bounds, shadow)) return WidgetDragHandOff.pins.release(token)
        // The sheet sits over the requesting app: Home must come up to take the drop (Launcher3 does the same).
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    /** Destroyed mid-flow (e.g. by Home) without Add finishing: the bound id must not leak. */
    override fun onDestroy() {
        if (isFinishing) deletePending()
        super.onDestroy()
    }

    private fun deletePending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) widgets.deleteId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    /** Declining leaves nothing behind (the requesting app hears nothing). */
    private fun cancel() {
        deletePending()
        finish()
    }

    private companion object {
        const val TAG = "PinWidgetActivity"
        const val STATE_ID = "widget_id"
    }
}

private class PinSheetContent(
    val appName: String,
    val label: String,
    val description: String,
    val spans: WidgetSpans,
    val grid: CatalogGrid,
    val add: PinAdd,
)

/** Launcher3's pin sheet: title, hint, the preview card in a 24 dp container, then Cancel and Add, end-aligned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinWidgetSheet(
    sheet: PinSheetContent,
    preview: PreviewContent?,
    onAdd: () -> Unit,
    onPickUp: (View, Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val disabled = (sheet.add as? PinAdd.Disabled)?.reason
    val tooLarge = disabled == Rejection.TooLarge
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                sheet.appName,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.pin_widget_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                val pickUp = rememberPreviewPickUp(sheet, enabled = !tooLarge, onPickUp)
                WidgetTile(
                    label = sheet.label,
                    span = sheet.spans.default,
                    description = sheet.description,
                    tooLarge = tooLarge,
                    showAdd = false,
                    onTap = {},
                    onAdd = {},
                    preview = { Box(pickUp.recording) { WidgetPreview(preview, sheet.spans.default, sheet.grid) } },
                    previewModifier = pickUp.touch,
                )
            }
            if (disabled == Rejection.NoRoom) {
                Text(
                    stringResource(R.string.widget_no_space),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_widget_cancel)) }
                Button(onClick = onAdd, enabled = disabled == null) { Text(stringResource(R.string.pin_widget_add)) }
            }
        }
    }
}
