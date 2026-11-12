package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.ReportedSize
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * One hosted widget: its `AppWidgetHostView` in the long-press [WidgetFrame] (reporting to
 * [touches]) in an `AndroidView`. The widget is told the size of
 * its [span] on [grid] (the Widget Catalog size list): once the view is attached, so the display
 * is known, and again only when the size list changes (each report is a broadcast to the
 * provider). [onLightBackground] picks the widget's light-background layout on a light wallpaper.
 */
@Composable
fun HostedWidget(
    host: WidgetHost,
    appWidgetId: Int,
    span: CellSpan,
    grid: CatalogGrid,
    onLightBackground: Boolean,
    touches: WidgetTouchListener?,
    modifier: Modifier = Modifier,
) {
    val sizes = remember(span, grid) { WidgetCatalog.sizeList(span, grid) }
    // A view per id: never show one widget's view under another id.
    key(appWidgetId) {
        AndroidView(
            factory = { context ->
                WidgetFrame(context).apply {
                    addView(host.createView(context, appWidgetId), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
            },
            update = { frame ->
                frame.listener = touches
                val view = frame.getChildAt(0) as AppWidgetHostView
                // Takes effect from the widget's next update; the first one already follows the wallpaper.
                view.setOnLightBackground(onLightBackground)
                view.reportSizes(sizes)
            },
            modifier = modifier,
        )
    }
}

private fun AppWidgetHostView.reportSizes(sizes: List<ReportedSize>) {
    if (getTag(TAG_SIZES) == sizes) return
    setTag(TAG_SIZES, sizes)
    doOnAttach { view ->
        @Suppress("UNCHECKED_CAST")
        val latest = view.getTag(TAG_SIZES) as List<ReportedSize>
        val id = appWidgetId
        val displayId = view.display.displayId
        val manager = AppWidgetManager.getInstance(view.context)
        sizeReports.execute { manager.reportSizes(id, latest, displayId) }
    }
}

private val TAG_SIZES = R.id.widget_reported_size

/** One at a time and in order, so a later size never loses to an earlier one. */
private val sizeReports = Dispatchers.IO.limitedParallelism(1).asExecutor()
