package io.github.jgoedde.pielauncher.widgets

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.core.home.LayoutResult
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId
import kotlinx.coroutines.launch

/** How an "Add widget" ended. */
enum class AddWidgetOutcome { Added, NoSpace, Cancelled }

/** Runs [AddWidgetActivity] for one Widget Zone of the Home Page. */
class AddWidgetContract : ActivityResultContract<WidgetZoneId, AddWidgetOutcome>() {
    override fun createIntent(
        context: Context,
        input: WidgetZoneId,
    ): Intent = Intent(context, AddWidgetActivity::class.java).putExtra(EXTRA_ZONE, input.name)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): AddWidgetOutcome =
        intent
            ?.getStringExtra(EXTRA_OUTCOME)
            ?.let { name -> AddWidgetOutcome.entries.firstOrNull { it.name == name } }
            ?: AddWidgetOutcome.Cancelled

    internal companion object {
        const val EXTRA_ZONE = "zone"
        const val EXTRA_OUTCOME = "outcome"
    }
}

/**
 * The widget-add trampoline (PRD 7.4), invisible: allocate an id → system picker
 * (`ACTION_APPWIDGET_PICK`) → bind if the picker did not (`bindAppWidgetIdIfAllowed`, else the
 * `ACTION_APPWIDGET_BIND` dialog) → configure unless the provider makes it optional and
 * reconfigurable → place at the first free spot of the zone. Any cancel or failure deletes the id.
 * The space check runs before configuring, so the user never configures a widget that cannot go
 * anywhere.
 */
@Suppress("DEPRECATION") // startAppWidgetConfigureActivityForResult only reports to onActivityResult.
class AddWidgetActivity : ComponentActivity() {
    private val graph by lazy { (application as PieLauncherApplication).graph }
    private val widgets by lazy { graph.widgetHost }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val zone: WidgetZoneId by lazy {
        val name = intent.getStringExtra(AddWidgetContract.EXTRA_ZONE)
        WidgetZoneId.entries.firstOrNull { it.name == name } ?: WidgetZoneId.HomeUpper
    }

    private val targets: List<WidgetZoneId>
        get() = listOf(zone)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated while a system screen is up: its result is still on the way.
            widgetId = savedInstanceState.getInt(STATE_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            return
        }
        widgetId = widgets.allocateId()
        try {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                REQUEST_PICK,
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No widget picker", e)
            finishWith(AddWidgetOutcome.Cancelled)
        }
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
            REQUEST_PICK -> onPicked(data)
            REQUEST_BIND -> onBound()
            REQUEST_CONFIGURE -> onConfigured()
        }
    }

    private fun onPicked(data: Intent?) {
        if (widgets.info(widgetId) != null) return onBound()
        // The picker normally binds the widget itself; if it did not, bind it here.
        val provider =
            data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
                ?: return finishWith(AddWidgetOutcome.Cancelled)
        if (widgets.bindIfAllowed(widgetId, Process.myUserHandle(), provider)) return onBound()
        startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider),
            REQUEST_BIND,
        )
    }

    private fun onBound() {
        val info = widgets.info(widgetId) ?: return finishWith(AddWidgetOutcome.Cancelled)
        lifecycleScope.launch {
            if (widgets.findSpot(info, targets, graph.homeGeometry.zones(this@AddWidgetActivity)) !is LayoutResult.Changed) {
                return@launch finishWith(AddWidgetOutcome.NoSpace)
            }
            if (info.needsConfigure()) configure() else onConfigured()
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
            finishWith(if (placed is LayoutResult.Changed) AddWidgetOutcome.Added else AddWidgetOutcome.NoSpace)
        }
    }

    /** Home clears the launcher task, destroying this activity without a result: that is a cancel too. */
    override fun onDestroy() {
        if (isFinishing && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgets.deleteId(widgetId)
        super.onDestroy()
    }

    private fun finishWith(outcome: AddWidgetOutcome) {
        if (outcome != AddWidgetOutcome.Added && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgets.deleteId(widgetId)
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_OK, Intent().putExtra(AddWidgetContract.EXTRA_OUTCOME, outcome.name))
        finish()
    }

    private companion object {
        const val TAG = "AddWidgetActivity"
        const val STATE_ID = "widget_id"
        const val REQUEST_PICK = 1
        const val REQUEST_BIND = 2
        const val REQUEST_CONFIGURE = 3
    }
}
