package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import io.github.jgoedde.pielauncher.R

/**
 * One hosted widget: its `AppWidgetHostView` in an `AndroidView`, told its size in dp. The size is
 * reported once the view is attached, so the platform can add `OPTION_APPWIDGET_DISPLAY_ID`, and
 * again only when it changes (each report is a broadcast to the provider).
 */
@Composable
fun HostedWidget(
    host: WidgetHost,
    appWidgetId: Int,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    // A view per id: never show one widget's view under another id.
    key(appWidgetId) {
        AndroidView(
            factory = { context -> host.createView(context, appWidgetId) },
            update = { view -> view.reportSize(SizeF(width.value, height.value)) },
            modifier = modifier,
        )
    }
}

private fun AppWidgetHostView.reportSize(size: SizeF) {
    if (getTag(TAG_SIZE) == size) return
    setTag(TAG_SIZE, size)
    doOnAttach { updateAppWidgetSize(Bundle(), listOf(size)) }
}

private val TAG_SIZE = R.id.widget_reported_size
