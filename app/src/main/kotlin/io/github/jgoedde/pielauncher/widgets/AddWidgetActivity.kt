package io.github.jgoedde.pielauncher.widgets

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.PagePlacement
import io.github.jgoedde.pielauncher.core.home.PageZone
import io.github.jgoedde.pielauncher.core.home.Rejection
import io.github.jgoedde.pielauncher.core.home.WidgetAddCodec
import io.github.jgoedde.pielauncher.core.home.WidgetAddOutcome
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget
import io.github.jgoedde.pielauncher.core.home.WidgetDrop
import kotlinx.coroutines.launch

/**
 * Add a widget at [target]. A tap or drop from the Widget Picker names the widget's [provider] in
 * [profile]; a widget Pin Request ([WidgetAddTarget.Pin], PRD 7.6) brings its own, is never
 * configured, and is accepted once the widget is placed.
 */
data class AddWidgetRequest(
    val target: WidgetAddTarget,
    val provider: ComponentName? = null,
    val profile: UserHandle? = null,
)

/** Runs [AddWidgetActivity]. */
class AddWidgetContract : ActivityResultContract<AddWidgetRequest, WidgetAddOutcome>() {
    override fun createIntent(
        context: Context,
        input: AddWidgetRequest,
    ): Intent =
        Intent(context, AddWidgetActivity::class.java)
            .putExtra(EXTRA_TARGET, WidgetAddCodec.encode(input.target))
            .putExtra(EXTRA_PROVIDER, input.provider)
            .putExtra(EXTRA_PROFILE, input.profile)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): WidgetAddOutcome = WidgetAddCodec.outcome(intent?.getStringExtra(EXTRA_OUTCOME)) ?: WidgetAddOutcome.Cancelled

    internal companion object {
        const val EXTRA_TARGET = "target"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_OUTCOME = "outcome"
    }
}

/**
 * The widget-add trampoline (PRD 7.4), invisible: the launcher binds the widget itself. Finds
 * the spot first (so nothing is bound or configured that cannot go anywhere) → allocate an id →
 * `bindAppWidgetIdIfAllowed` with the size options of the target span, else the system
 * `ACTION_APPWIDGET_BIND` dialog → a placeholder in the target cells → configure unless the
 * provider makes it optional and reconfigurable → place. Any cancel or failure deletes the id
 * and removes the placeholder. A pinned widget is never configured by the launcher; its Pin
 * Request is accepted with the size options and the widget id once it is placed.
 */
@Suppress("DEPRECATION") // startAppWidgetConfigureActivityForResult only reports to onActivityResult.
class AddWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val target: WidgetAddTarget? by lazy { WidgetAddCodec.target(intent.getStringExtra(AddWidgetContract.EXTRA_TARGET)) }

    /** The long-pressed zone a tap-add tries first; null: the Home Page, upper zone first. */
    private val first: PageZone? get() = (target as? WidgetAddTarget.Tap)?.zone

    /** Where the widget was dropped, when it was dragged onto Home; null for tap-add. */
    private val drop: WidgetDrop?
        get() =
            when (val target = target) {
                is WidgetAddTarget.Drop -> target.drop
                is WidgetAddTarget.Pin -> target.drop
                is WidgetAddTarget.Tap, null -> null
            }

    /**
     * The widget Pin Request this add answers (dragged in from the pin sheet); null for the Widget
     * Picker. It stays in [WidgetDragHandOff.pins] until this trampoline finishes, so a recreated
     * trampoline still finds it.
     */
    private val pin: LauncherApps.PinItemRequest?
        get() = (target as? WidgetAddTarget.Pin)?.let { WidgetDragHandOff.pins.get(it.handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated while a system screen is up: its result is still on the way.
            widgetId = savedInstanceState.getInt(STATE_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            return
        }
        val provider = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROVIDER, ComponentName::class.java)
        val profile = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROFILE, UserHandle::class.java)
        val info =
            when {
                pin != null -> pin?.takeIf { it.isValid }?.getAppWidgetProviderInfo(this)
                provider != null && profile != null -> widgets.provider(provider, profile)
                else -> null
            }
        if (target == null || info == null) return finishWith(WidgetAddOutcome.Cancelled)
        lifecycleScope.launch { bind(info) }
    }

    private suspend fun bind(info: AppWidgetProviderInfo) {
        val placement = spotFor(info)
        val spot = placement.result
        if (spot !is LayoutResult.Changed) return finishWith(spot.outcome())
        widgetId = widgets.allocateId()
        val ask = widgets.bindOrAsk(widgetId, info, widgets.sizeOptionsFor(spot.widget.cells.span, this)) ?: return onBound()
        startActivityForResult(ask, REQUEST_BIND)
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
        if (resultCode != RESULT_OK) return finishWith(WidgetAddOutcome.Cancelled)
        when (requestCode) {
            REQUEST_BIND -> onBound()
            REQUEST_CONFIGURE -> onConfigured()
        }
    }

    private fun onBound() {
        val info = widgets.info(widgetId) ?: return finishWith(WidgetAddOutcome.Cancelled)
        // The platform contract: the launcher never configures a pinned widget (the app does).
        if (pin != null || !info.needsConfigure()) return onConfigured()
        lifecycleScope.launch {
            // The spot may have been taken while the bind dialog was up.
            val placement = spotFor(info)
            val spot = placement.result
            val page = placement.zone?.page
            if (spot !is LayoutResult.Changed || page == null) return@launch finishWith(spot.outcome())
            widgets.showPending(widgetId, info, page, spot.widget, spot.moved)
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
            finishWith(WidgetAddOutcome.Cancelled)
        } catch (e: SecurityException) {
            Log.w(TAG, "Configure activity not allowed for widget $widgetId", e)
            finishWith(WidgetAddOutcome.Cancelled)
        }
    }

    private fun onConfigured() {
        // Gone when the process died meanwhile and the startup reconcile deleted the unplaced id.
        if (widgets.info(widgetId) == null) return finishWith(WidgetAddOutcome.Cancelled)
        lifecycleScope.launch {
            val zones = graph.homeGeometry.zones(this@AddWidgetActivity)
            val placement = drop?.let { widgets.place(widgetId, it, zones) } ?: widgets.place(widgetId, first, zones)
            val placed = placement.result
            val page = placement.zone?.page
            if (placed !is LayoutResult.Changed || page == null) return@launch finishWith(placed.outcome())
            val request = pin
            if (request != null && !accept(request, placed.widget.cells.span)) {
                widgets.remove(widgetId)
                widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                return@launch finishWith(WidgetAddOutcome.Cancelled)
            }
            finishWith(WidgetAddOutcome.Added(widgetId, page))
        }
    }

    /**
     * Accepts the Pin Request [request] for the placed widget at [span], with its size options and
     * id. Placed first, so the requesting app never hears "added" for a widget that found no room.
     */
    private fun accept(
        request: LauncherApps.PinItemRequest,
        span: CellSpan,
    ): Boolean {
        val options = widgets.sizeOptionsFor(span, this)
        options.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return try {
            request.isValid && request.accept(options)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot accept widget Pin Request", e)
            false
        }
    }

    /** Where the widget of [info] goes: where it was dropped, else the first free spot from [first] on. */
    private suspend fun spotFor(info: AppWidgetProviderInfo): PagePlacement {
        val zones = graph.homeGeometry.zones(this)
        return drop?.let { widgets.findSpot(info, it, zones) } ?: widgets.findSpot(info, first, zones)
    }

    /** Home clears the launcher task, destroying this activity without a result: that is a cancel too. */
    override fun onDestroy() {
        if (isFinishing) {
            discard()
            (target as? WidgetAddTarget.Pin)?.let { WidgetDragHandOff.pins.release(it.handle) }
        }
        super.onDestroy()
    }

    private fun finishWith(outcome: WidgetAddOutcome) {
        if (outcome !is WidgetAddOutcome.Added) discard()
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_OK, Intent().putExtra(AddWidgetContract.EXTRA_OUTCOME, WidgetAddCodec.encode(outcome)))
        finish()
    }

    /** Deletes the unplaced id and its placeholder. */
    private fun discard() {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        widgets.clearPending(widgetId)
        widgets.deleteId(widgetId)
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun LayoutResult.outcome(): WidgetAddOutcome =
        when ((this as? LayoutResult.Rejected)?.reason) {
            Rejection.TooLarge -> WidgetAddOutcome.TooLarge
            null -> WidgetAddOutcome.Cancelled
            else -> WidgetAddOutcome.NoRoom
        }

    private companion object {
        const val TAG = "AddWidgetActivity"
        const val STATE_ID = "widget_id"
        const val REQUEST_BIND = 2
        const val REQUEST_CONFIGURE = 3
    }
}
