package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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
import io.github.jgoedde.pielauncher.core.home.WidgetAddCodec
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Effect
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Event
import io.github.jgoedde.pielauncher.core.home.WidgetAddOutcome
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget
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
 * The widget-add trampoline (PRD 7.4), invisible: the launcher binds the widget itself. It runs
 * the effects of a [WidgetAddFlow] and reports back what happened; the flow decides every step.
 * Its state is kept in the saved state, so a bind dialog's or configure screen's result finds it
 * after a recreate or process death.
 */
@Suppress("DEPRECATION") // startAppWidgetConfigureActivityForResult only reports to onActivityResult.
class AddWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }

    /** Null while the request could not be read: then this activity only finishes. */
    private var flow: WidgetAddFlow? = null

    /** The provider being added. */
    private var info: AppWidgetProviderInfo? = null

    /**
     * The widget Pin Request this add answers (dragged in from the pin sheet); null for the Widget
     * Picker. Taken from [WidgetDragHandOff.pins] once, then kept in the saved state, so it
     * survives a recreate and process death while a system screen is up.
     */
    private var pin: LauncherApps.PinItemRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = WidgetAddCodec.target(intent.getStringExtra(AddWidgetContract.EXTRA_TARGET))
        pin =
            if (savedInstanceState != null) {
                savedInstanceState.getParcelable(STATE_PIN, LauncherApps.PinItemRequest::class.java)
            } else {
                (target as? WidgetAddTarget.Pin)?.let { WidgetDragHandOff.pins.take(it.handle) }
            }
        val provider = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROVIDER, ComponentName::class.java)
        val profile = intent.getParcelableExtra(AddWidgetContract.EXTRA_PROFILE, UserHandle::class.java)
        info =
            when {
                pin != null -> pin?.takeIf { it.isValid }?.getAppWidgetProviderInfo(this)
                provider != null && profile != null -> widgets.provider(provider, profile)
                else -> null
            }
        if (target == null) return finishWith(WidgetAddOutcome.Cancelled)
        val saved = WidgetAddFlow.State.decode(savedInstanceState?.getString(STATE_FLOW))
        val flow = WidgetAddFlow(target, info?.addable(), saved ?: WidgetAddFlow.State.Start)
        this.flow = flow
        perform(flow.start())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        flow?.let { outState.putString(STATE_FLOW, it.state.encode()) }
        outState.putParcelable(STATE_PIN, pin)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> send(Event.BindAnswered(granted = resultCode == RESULT_OK))
            REQUEST_CONFIGURE -> send(Event.Configured(ok = resultCode == RESULT_OK))
        }
    }

    /** Home clears the launcher task, destroying this activity without a result: that is a cancel too. */
    override fun onDestroy() {
        if (isFinishing) send(Event.Destroyed)
        super.onDestroy()
    }

    private fun send(event: Event) {
        flow?.let { perform(it.on(event)) }
    }

    private fun perform(effects: List<Effect>) = effects.forEach(::perform)

    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.FindSpot -> {
                val info = info ?: return send(Event.IdGone)
                val bound = effect.id
                if (bound != null && widgets.info(bound) == null) return send(Event.IdGone)
                lifecycleScope.launch { send(Event.SpotFound(widgets.findSpot(info, effect.target, zones()))) }
            }
            is Effect.Bind -> {
                val info = info ?: return
                val id = widgets.allocateId()
                val ask = widgets.bindOrAsk(id, info, widgets.sizeOptionsFor(effect.span, this))
                if (ask == null) return send(Event.Bound(id))
                send(Event.AskingToBind(id))
                startActivityForResult(ask, REQUEST_BIND)
            }
            is Effect.ShowPending -> {
                val info = info ?: return
                widgets.showPending(effect.id, info, effect.page, effect.spot.widget, effect.spot.moved)
            }
            is Effect.Configure ->
                if (!widgets.startConfigure(this, effect.id, REQUEST_CONFIGURE)) send(Event.Configured(ok = false))
            is Effect.Place -> {
                if (widgets.info(effect.id) == null) return send(Event.IdGone)
                lifecycleScope.launch { send(Event.Placed(widgets.place(effect.id, effect.target, zones()))) }
            }
            is Effect.Accept -> send(Event.Accepted(accept(effect.id, effect.span)))
            is Effect.DeleteUnplaced -> {
                widgets.clearPending(effect.id)
                widgets.deleteId(effect.id)
            }
            // In the app scope: this activity finishes right after.
            is Effect.RemovePlaced -> widgets.discard(effect.id)
            is Effect.Finish -> finishWith(effect.outcome)
        }
    }

    /** Accepts the Pin Request for placed widget [id] at [span], with its size options and id. */
    private fun accept(
        id: Int,
        span: CellSpan,
    ): Boolean {
        val request = pin ?: return false
        val options = widgets.sizeOptionsFor(span, this)
        options.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return try {
            request.isValid && request.accept(options)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot accept widget Pin Request", e)
            false
        }
    }

    private fun zones() = graph.homeGeometry.zones(this)

    private fun finishWith(outcome: WidgetAddOutcome) {
        setResult(RESULT_OK, Intent().putExtra(AddWidgetContract.EXTRA_OUTCOME, WidgetAddCodec.encode(outcome)))
        finish()
    }

    private companion object {
        const val TAG = "AddWidgetActivity"
        const val STATE_FLOW = "flow"
        const val STATE_PIN = "pin"
        const val REQUEST_BIND = 2
        const val REQUEST_CONFIGURE = 3
    }
}
