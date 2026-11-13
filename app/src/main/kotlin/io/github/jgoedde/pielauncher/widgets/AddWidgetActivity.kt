package io.github.jgoedde.pielauncher.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.home.WidgetAddCodec
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow
import io.github.jgoedde.pielauncher.core.home.WidgetAddOutcome
import io.github.jgoedde.pielauncher.core.home.WidgetAddTarget

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
 * The widget-add trampoline (PRD 7.4), invisible: the launcher binds the widget itself. A
 * [WidgetAddRunner] runs the [WidgetAddFlow]; the flow decides every step. Its state is kept in
 * the saved state, so a bind dialog's or configure screen's result finds it after a recreate or
 * process death.
 */
class AddWidgetActivity : ComponentActivity() {
    private val widgets by lazy { (application as PieLauncherApplication).graph.widgetHost }

    /** Null while the request could not be read: then this activity only finishes. */
    private var runner: WidgetAddRunner? = null
    private var flow: WidgetAddFlow? = null

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
        val info =
            when {
                pin != null -> pin?.takeIf { it.isValid }?.getAppWidgetProviderInfo(this)
                provider != null && profile != null -> widgets.provider(provider, profile)
                else -> null
            }
        if (target == null) return finishWith(WidgetAddOutcome.Cancelled)
        val saved = WidgetAddFlow.State.decode(savedInstanceState?.getString(STATE_FLOW))
        val flow = WidgetAddFlow(target, info?.addable(), saved ?: WidgetAddFlow.State.Start)
        this.flow = flow
        runner = WidgetAddRunner(this, flow, info, pin, onFinish = ::finishWith).also { it.start() }
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
        runner?.onActivityResult(requestCode, resultCode)
    }

    override fun onDestroy() {
        runner?.onDestroy()
        super.onDestroy()
    }

    private fun finishWith(outcome: WidgetAddOutcome) {
        setResult(RESULT_OK, Intent().putExtra(AddWidgetContract.EXTRA_OUTCOME, WidgetAddCodec.encode(outcome)))
        finish()
    }

    private companion object {
        const val STATE_FLOW = "flow"
        const val STATE_PIN = "pin"
    }
}
