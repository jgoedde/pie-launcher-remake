package io.github.jgoedde.pielauncher.config

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import dev.zacsweers.metro.Inject

/** Resolves the default apps of the Default Config (PRD 9.6) through their intents. */
@Inject
class DefaultApps(
    private val application: Application,
) {
    /** Dialer, messaging, camera, browser and Settings, in that order; null where nothing is set. */
    fun packages(): List<String?> =
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(Settings.ACTION_SETTINGS),
        ).map(::resolve)

    /** The default handler's package; null when none is set (the system would show a chooser). */
    private fun resolve(intent: Intent): String? =
        application.packageManager
            .resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            ?.activityInfo
            ?.packageName
            ?.takeUnless { it == CHOOSER_PACKAGE }

    private companion object {
        /** The resolver activity that stands in when several apps handle an intent. */
        const val CHOOSER_PACKAGE = "android"
    }
}
