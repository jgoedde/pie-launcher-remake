package io.github.jgoedde.pielauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.PinAdd
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Effect
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Event
import io.github.jgoedde.pielauncher.core.home.WidgetAddOutcome
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget
import kotlinx.coroutines.launch

/**
 * Runs the effects of a [WidgetAddFlow] (PRD 7.4) for [activity], which hosts the add: the add
 * trampoline, or the pin sheet. It binds, configures, places and accepts the Pin Request; showing
 * the pin sheet ([onOffer]) and ending the add ([onFinish]) are the host's. The host forwards
 * [onActivityResult], [onDestroy] and [save]; the flow's state comes back from [saved], so a
 * system screen's result finds the flow after a recreate or process death.
 */
@Suppress("DEPRECATION") // startAppWidgetConfigureActivityForResult only reports to onActivityResult.
class WidgetAddRunner(
    private val activity: ComponentActivity,
    target: WidgetAddTarget,
    /** The host's saved state, if it is being recreated. */
    saved: Bundle?,
    /** The provider being added; null when it or the Pin Request is gone. */
    private val info: AppWidgetProviderInfo?,
    /** The widget Pin Request this add answers, if any. */
    private val pin: LauncherApps.PinItemRequest?,
    private val onOffer: (PinAdd) -> Unit = {},
    private val onFinish: (WidgetAddOutcome) -> Unit,
) {
    private val graph by lazy { (activity.application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }
    private val flow =
        WidgetAddFlow(target, info?.addable(), WidgetAddFlow.State.decode(saved?.getString(STATE_FLOW)) ?: WidgetAddFlow.State.Start)

    fun start() = perform(flow.start())

    fun send(event: Event) = perform(flow.on(event))

    fun save(outState: Bundle) = outState.putString(STATE_FLOW, flow.state.encode())

    /** A bind dialog's or configure screen's result. */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
    ) {
        when (requestCode) {
            REQUEST_BIND -> send(Event.BindAnswered(granted = resultCode == Activity.RESULT_OK))
            REQUEST_CONFIGURE -> send(Event.Configured(ok = resultCode == Activity.RESULT_OK))
        }
    }

    /** Home clears the launcher task, destroying the host without a result: that is a cancel too. */
    fun onDestroy() {
        if (activity.isFinishing) send(Event.Destroyed)
    }

    private fun perform(effects: List<Effect>) = effects.forEach(::perform)

    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.FindSpot -> {
                val info = info ?: return send(Event.IdGone)
                val bound = effect.id
                if (bound != null && widgets.info(bound) == null) return send(Event.IdGone)
                activity.lifecycleScope.launch { send(Event.SpotFound(widgets.findSpot(info, effect.target, zones()))) }
            }
            is Effect.Offer -> onOffer(effect.add)
            is Effect.Bind -> {
                val info = info ?: return
                val id = widgets.allocateId()
                val ask = widgets.bindOrAsk(id, info, widgets.sizeOptionsFor(effect.span, activity))
                if (ask == null) return send(Event.Bound(id))
                send(Event.AskingToBind(id))
                activity.startActivityForResult(ask, REQUEST_BIND)
            }
            is Effect.ShowPending -> {
                val info = info ?: return
                widgets.showPending(effect.id, info, effect.page, effect.spot.widget, effect.spot.moved)
            }
            is Effect.Configure ->
                if (!widgets.startConfigure(activity, effect.id, REQUEST_CONFIGURE)) send(Event.Configured(ok = false))
            is Effect.Place -> {
                if (widgets.info(effect.id) == null) return send(Event.IdGone)
                activity.lifecycleScope.launch { send(Event.Placed(widgets.place(effect.id, effect.target, zones()))) }
            }
            is Effect.Accept -> send(Event.Accepted(accept(effect.id, effect.span)))
            is Effect.DeleteUnplaced -> {
                widgets.clearPending(effect.id)
                widgets.deleteId(effect.id)
            }
            // In the app scope: the host finishes right after.
            is Effect.RemovePlaced -> widgets.discard(effect.id)
            is Effect.Finish -> onFinish(effect.outcome)
        }
    }

    /** Accepts the Pin Request for placed widget [id] at [span], with its size options and id. */
    private fun accept(
        id: Int,
        span: CellSpan,
    ): Boolean {
        val request = pin ?: return false
        val options = widgets.sizeOptionsFor(span, activity)
        options.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return try {
            request.isValid && request.accept(options)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Cannot accept widget Pin Request", e)
            false
        }
    }

    private fun zones() = graph.homeGeometry.zones(activity)

    private companion object {
        const val TAG = "WidgetAddRunner"
        const val STATE_FLOW = "flow"
        const val REQUEST_BIND = 2
        const val REQUEST_CONFIGURE = 3
    }
}
