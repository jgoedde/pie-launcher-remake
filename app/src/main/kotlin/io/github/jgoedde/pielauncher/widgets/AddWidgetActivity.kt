package io.github.jgoedde.pielauncher.widgets

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import kotlinx.coroutines.launch

/** How an "Add widget" ended. */
enum class AddWidgetOutcome { Added, NoRoom, TooLarge, Cancelled }

/** Add the widget of [provider] in [profile] at the first free spot of [zones], in order. */
data class AddWidgetRequest(
    val provider: ComponentName,
    val profile: UserHandle,
    val zones: List<WidgetZoneId>,
)

/** Runs [AddWidgetActivity]. */
class AddWidgetContract : ActivityResultContract<AddWidgetRequest, AddWidgetOutcome>() {
    override fun createIntent(
        context: Context,
        input: AddWidgetRequest,
    ): Intent =
        Intent(context, AddWidgetActivity::class.java)
            .putExtra(EXTRA_PROVIDER, input.provider)
            .putExtra(EXTRA_PROFILE, input.profile)
            .putExtra(EXTRA_ZONES, input.zones.map { it.name }.toTypedArray())

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): AddWidgetOutcome =
        intent
            ?.getStringExtra(EXTRA_OUTCOME)
            ?.let { name -> AddWidgetOutcome.entries.firstOrNull { it.name == name } }
            ?: AddWidgetOutcome.Cancelled

    internal companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_ZONES = "zones"
        const val EXTRA_OUTCOME = "outcome"
    }
}

/**
 * The widget-add trampoline (PRD 7.4), invisible: the launcher binds the widget itself. Finds
 * the spot first (so nothing is bound or configured that cannot go anywhere) → allocate an id →
 * `bindAppWidgetIdIfAllowed` with the size options of the target span, else the system
 * `ACTION_APPWIDGET_BIND` dialog → a placeholder in the target cells → configure unless the
 * provider makes it optional and reconfigurable → place. Any cancel or failure deletes the id
 * and removes the placeholder.
 */
@Suppress("DEPRECATION") // startAppWidgetConfigureActivityForResult only reports to onActivityResult.
class AddWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val targets: List<WidgetZoneId> by lazy {
        intent
            .getStringArrayExtra(AddWidgetContract.EXTRA_ZONES)
            ?.mapNotNull { name -> WidgetZoneId.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isNotEmpty() }
            ?: WidgetZoneId.addOrder(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated while a system screen is up: its result is still on the way.
            widgetId = savedInstanceState.getInt(STATE_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            return
        }
        val provider = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROVIDER, ComponentName::class.java)
        val profile = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROFILE, UserHandle::class.java)
        val info = if (provider != null && profile != null) widgets.provider(provider, profile) else null
        if (info == null) return finishWith(AddWidgetOutcome.Cancelled)
        lifecycleScope.launch { bind(info) }
    }

    private suspend fun bind(info: AppWidgetProviderInfo) {
        val zones = graph.homeGeometry.zones(this)
        val spot = widgets.findSpot(info, targets, zones)
        if (spot !is LayoutResult.Changed) return finishWith(spot.outcome())
        widgetId = widgets.allocateId()
        val options = sizeOptions(WidgetCatalog.sizeList(spot.widget.cells.span, graph.homeGeometry.catalogGrid(this)))
        if (widgets.bindIfAllowed(widgetId, info.profile, info.provider, options)) return onBound()
        startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
            REQUEST_BIND,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ID, widgetId)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return finishWith(AddWidgetOutcome.Cancelled)
        when (requestCode) {
            REQUEST_BIND -> onBound()
            REQUEST_CONFIGURE -> onConfigured()
        }
    }

    private fun onBound() {
        val info = widgets.info(widgetId) ?: return finishWith(AddWidgetOutcome.Cancelled)
        if (!info.needsConfigure()) return onConfigured()
        lifecycleScope.launch {
            // The spot may have been taken while the bind dialog was up.
            val spot = widgets.findSpot(info, targets, graph.homeGeometry.zones(this@AddWidgetActivity))
            if (spot !is LayoutResult.Changed) return@launch finishWith(spot.outcome())
            widgets.showPending(widgetId, info, spot.widget)
            configure()
        }
    }

    private fun configure() {
        // The configure IntentSender carries no background-start rights of its own; the visible launcher grants them.
        val options =
            ActivityOptions
                .makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                .toBundle()
        try {
            widgets.startConfigure(this, widgetId, REQUEST_CONFIGURE, options)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Configure activity missing for widget $widgetId", e)
            finishWith(AddWidgetOutcome.Cancelled)
        } catch (e: SecurityException) {
            Log.w(TAG, "Configure activity not allowed for widget $widgetId", e)
            finishWith(AddWidgetOutcome.Cancelled)
        }
    }

    private fun onConfigured() {
        // Gone when the process died meanwhile and the startup reconcile deleted the unplaced id.
        if (widgets.info(widgetId) == null) return finishWith(AddWidgetOutcome.Cancelled)
        lifecycleScope.launch {
            val placed = widgets.place(widgetId, targets, graph.homeGeometry.zones(this@AddWidgetActivity))
            finishWith(if (placed is LayoutResult.Changed) AddWidgetOutcome.Added else placed.outcome())
        }
    }

    /** Home clears the launcher task, destroying this activity without a result: that is a cancel too. */
    override fun onDestroy() {
        if (isFinishing) discard()
        super.onDestroy()
    }

    private fun finishWith(outcome: AddWidgetOutcome) {
        if (outcome != AddWidgetOutcome.Added) discard()
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_OK, Intent().putExtra(AddWidgetContract.EXTRA_OUTCOME, outcome.name))
        finish()
    }

    /** Deletes the unplaced id and its placeholder. */
    private fun discard() {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        widgets.clearPending(widgetId)
        widgets.deleteId(widgetId)
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun LayoutResult.outcome(): AddWidgetOutcome =
        when ((this as? LayoutResult.Rejected)?.reason) {
            Rejection.TooLarge -> AddWidgetOutcome.TooLarge
            null -> AddWidgetOutcome.Cancelled
            else -> AddWidgetOutcome.NoRoom
        }

    private companion object {
        const val TAG = "AddWidgetActivity"
        const val STATE_ID = "widget_id"
        const val REQUEST_BIND = 2
        const val REQUEST_CONFIGURE = 3
    }
}
