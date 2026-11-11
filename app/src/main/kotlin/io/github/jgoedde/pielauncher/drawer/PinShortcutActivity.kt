package io.github.jgoedde.pielauncher.drawer

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import io.github.jgoedde.pielauncher.PieLauncherApplication
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.shortcuts.PinOutcome
import io.github.jgoedde.pielauncher.platform.entryKey
import kotlinx.coroutines.launch

/**
 * Handles Pin Requests for Shortcuts (`CONFIRM_PIN_SHORTCUT`, PRD 7.7). Invisible: every request
 * is accepted without asking (the requesting app already asked), recorded, and confirmed with a
 * toast. The Shortcut lands in the App Drawer with a New Badge, never in a Layer.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Theme.NoDisplay: must finish before onResume.
        finish()
        val graph = (application as PieLauncherApplication).graph
        val request = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (request == null || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || !request.isValid) return
        val info = request.shortcutInfo ?: return
        val accepted =
            try {
                request.accept()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot accept Pin Request for ${info.`package`}/${info.id}", e)
                false
            }
        if (!accepted) return

        val app = application
        graph.appScope.launch {
            val outcome = graph.shortcutPins.record(info)
            if (outcome == PinOutcome.Added) graph.newBadges.add(listOf(graph.shortcutPins.keyOf(info).entryKey))
            val label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
            val message =
                when (outcome) {
                    PinOutcome.Added -> R.string.pin_added
                    PinOutcome.AlreadyPinned -> R.string.pin_already_added
                }
            Toast.makeText(app, app.getString(message, label), Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "PinShortcutActivity"
    }
}
