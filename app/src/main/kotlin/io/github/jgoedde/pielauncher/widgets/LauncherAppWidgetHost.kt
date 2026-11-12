package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import io.github.jgoedde.pielauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * The `AppWidgetHost` with the fixed host id. Widget ids are scoped to it, so [HOST_ID] must
 * never change. Every view it creates is a [LauncherWidgetHostView].
 */
internal class LauncherAppWidgetHost(
    context: Context,
    private val onRemoved: (Int) -> Unit,
) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetHostView(context)

    /** The system removed the widget (e.g. its provider went away): drop its layout row (PRD 7.1). */
    override fun onAppWidgetRemoved(appWidgetId: Int) = onRemoved(appWidgetId)

    companion object {
        const val HOST_ID = 0x5069_6501
    }
}

/**
 * A hosted widget (PRD 7.1): inflates RemoteViews off the main thread, takes the launcher's Monet
 * colours, and shows its own error view when a widget cannot be drawn. No bitmaps are cached.
 */
internal class LauncherWidgetHostView(
    context: Context,
) : AppWidgetHostView(context) {
    init {
        setExecutor(Dispatchers.Default.asExecutor())
        setColorResources(monetColors(context))
    }

    override fun getErrorView(): View {
        val night = context.resources.configuration.isNightModeActive
        return TextView(context).apply {
            setText(R.string.widget_error)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(
                context.getColor(
                    if (night) android.R.color.system_on_surface_variant_dark else android.R.color.system_on_surface_variant_light,
                ),
            )
            setBackgroundColor(
                context.getColor(
                    if (night) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light,
                ),
            )
        }
    }
}

/**
 * The Monet tonal palette (`system_accent1..3_*`, `system_neutral1..2_*`) resolved in [context],
 * so widgets that use it draw with the launcher's colours. Only these ids can be overlaid; other
 * `system_*` colours make the overlay fail.
 */
private fun monetColors(context: Context): SparseIntArray {
    val colors = SparseIntArray()
    android.R.color::class.java.fields
        .filter { TONAL_PALETTE.matches(it.name) }
        .forEach { field ->
            val id = field.getInt(null)
            colors.put(id, context.getColor(id))
        }
    return colors
}

private val TONAL_PALETTE = Regex("""system_(accent[123]|neutral[12])_\d+""")
