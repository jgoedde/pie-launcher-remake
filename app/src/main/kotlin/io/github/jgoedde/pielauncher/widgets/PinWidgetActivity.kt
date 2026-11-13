package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import io.github.jgoedde.pielauncher.core.home.PinAdd
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow
import io.github.jgoedde.pielauncher.core.home.WidgetAddOutcome
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget
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
 * "Add to home screen" runs the widget add flow ([WidgetAddTarget.PinSheet], via a
 * [WidgetAddRunner]): the spot it finds before the sheet shows enables Add, or disables it with
 * the reason; Add binds (a cancelled bind dialog keeps the sheet), places at the first free spot
 * of the Home Page, upper Widget Zone first, then of the other Pages, else on a new Page, and
 * accepts the request. Touch and hold on the preview drags the widget onto Home instead, whose
 * drop runs the same flow. The launcher never configures a pinned widget.
 */
class PinWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }

    private var request: LauncherApps.PinItemRequest? = null
    private var runner: WidgetAddRunner? = null

    /** The sheet's content once the flow has offered "Add to home screen". */
    private var sheet by mutableStateOf<PinSheetContent?>(null)
    private var preview by mutableStateOf<PreviewContent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (pin == null || pin.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET || !pin.isValid) return finish()
        val info = pin.getAppWidgetProviderInfo(this) ?: return finish()
        request = pin

        val grid = graph.homeGeometry.catalogGrid(this)
        runner =
            WidgetAddRunner(this, WidgetAddTarget.PinSheet, savedInstanceState, info, pin, onOffer = {
                sheet = load(info, grid, it)
            }, onFinish = { finishWith(info, it) })
                .also { it.start() }
        lifecycleScope.launch {
            val spans = widgets.spans(info, grid)
            val widthPx = (WidgetCatalog.sizeList(spans.default, grid).first().width * resources.displayMetrics.density).roundToInt()
            val appSupplied = pin.extras?.getParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews::class.java)
            preview = loadWidgetPreview(this@PinWidgetActivity, widgets, info, widthPx, appSupplied)
        }
        setContent {
            PieLauncherTheme {
                sheet?.let { PinWidgetSheet(it, preview, onAdd = ::add, onPickUp = ::pickUp, onDismiss = ::finish) }
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
        runner?.save(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        runner?.onActivityResult(requestCode, resultCode)
    }

    private fun add() {
        runner?.send(WidgetAddFlow.Event.AddChosen)
    }

    /** The add ended: say how, unless it was cancelled (the requesting app hears nothing then). */
    private fun finishWith(
        info: AppWidgetProviderInfo,
        outcome: WidgetAddOutcome,
    ) {
        when (outcome) {
            is WidgetAddOutcome.Added ->
                Toast.makeText(application, getString(R.string.pin_widget_added, info.loadLabel(packageManager)), Toast.LENGTH_SHORT).show()
            WidgetAddOutcome.NoRoom, WidgetAddOutcome.TooLarge ->
                Toast
                    .makeText(
                        application,
                        R.string.widget_no_space,
                        Toast.LENGTH_SHORT,
                    ).show()
            WidgetAddOutcome.Cancelled -> Unit
        }
        finish()
    }

    /**
     * Touch and hold on the preview: a system drag of the widget starts from [view], drawn as
     * [shadow], Home comes to the front and the sheet closes. The request waits in
     * [WidgetDragHandOff.pins] for Home's drop, which runs the widget add flow on it.
     */
    private fun pickUp(
        view: View,
        shadow: Bitmap,
    ) {
        val pin = request ?: return
        val spans = sheet?.spans ?: return
        val token = WidgetDragHandOff.pins.hold(pin)
        if (!WidgetDragHandOff.start(view, WidgetDragPayload.Pin(token), spans.bounds, shadow)) return WidgetDragHandOff.pins.release(token)
        // The sheet sits over the requesting app: Home must come up to take the drop (Launcher3 does the same).
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    /** Destroyed mid-flow (declined, or by Home) without the add finishing: the bound id must not leak. */
    override fun onDestroy() {
        runner?.onDestroy()
        super.onDestroy()
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

/** Launcher3's pin sheet: title, hint, the preview card in a 24 dp container, then "Cancel" and "Add to home screen", end-aligned. */
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
