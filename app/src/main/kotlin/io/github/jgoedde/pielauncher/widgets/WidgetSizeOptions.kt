package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import io.github.jgoedde.pielauncher.core.home.ReportedSize
import kotlin.math.roundToInt

/**
 * The size options for a widget showing at [sizes] (Launcher3 `WidgetSizes`): the size list as
 * `OPTION_APPWIDGET_SIZES`, and the min and max over it as the legacy min/max width and height.
 * All in dp. Also the bind options of a new widget.
 */
internal fun sizeOptions(sizes: List<ReportedSize>): Bundle =
    Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(sizes.toSizeF()))
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, sizes.minOf { it.width }.roundToInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, sizes.minOf { it.height }.roundToInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, sizes.maxOf { it.width }.roundToInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, sizes.maxOf { it.height }.roundToInt())
    }

/**
 * Tells widget [id] it shows at [sizes] on display [displayId], through
 * `updateAppWidgetOptions` (merged into its options), and only when its size list differs:
 * each update is a broadcast to the provider, the two binder calls are cheaper. Blocking binder
 * calls: run off the main thread.
 */
internal fun AppWidgetManager.reportSizes(
    id: Int,
    sizes: List<ReportedSize>,
    displayId: Int,
) {
    val current = getAppWidgetOptions(id)
    val currentSizes = current.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
    if (currentSizes == sizes.toSizeF() && current.getInt(AppWidgetManager.OPTION_APPWIDGET_DISPLAY_ID, -1) == displayId) return
    updateAppWidgetOptions(id, sizeOptions(sizes).apply { putInt(AppWidgetManager.OPTION_APPWIDGET_DISPLAY_ID, displayId) })
}

private fun List<ReportedSize>.toSizeF(): List<SizeF> = map { SizeF(it.width, it.height) }
