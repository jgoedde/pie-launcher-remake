package io.github.jgoedde.pielauncher.widgets

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Logs every past exit of this process that Android 17's memory limiter caused (PRD 7.1), so an
 * unexplained launcher restart can be traced to image-heavy widgets.
 */
fun logMemoryLimiterExits(context: Context) {
    val exits = context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 0)
    exits
        .filter { it.description?.contains("MemoryLimiter") == true }
        .forEach { Log.w("MemoryExits", "Killed by the memory limiter at ${it.timestamp}: ${it.description} (pss ${it.pss} kB)") }
}
