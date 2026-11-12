package io.github.jgoedde.pielauncher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import io.github.jgoedde.pielauncher.core.home.CatalogGrid
import io.github.jgoedde.pielauncher.core.home.CellSpan
import io.github.jgoedde.pielauncher.core.home.PreviewFacts
import io.github.jgoedde.pielauncher.core.home.WidgetCatalog
import io.github.jgoedde.pielauncher.core.home.WidgetPreviewSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** A loaded widget preview (PRD 7.4), as [WidgetPreviewSource] chose it. */
sealed interface PreviewContent {
    /** RemoteViews in a widget host view; null [views] inflates [info]'s `initialLayout` (the preview layout). */
    class Remote(
        val info: AppWidgetProviderInfo,
        val views: RemoteViews?,
    ) : PreviewContent

    class Image(
        val bitmap: ImageBitmap,
    ) : PreviewContent

    data object Placeholder : PreviewContent
}

/**
 * Loads the preview of [info] off the main thread: [appSupplied] (a Pin Request's
 * `EXTRA_APPWIDGET_PREVIEW`), else the generated preview, `previewLayout`, `previewImage`
 * (scaled to [widthPx]), else a placeholder.
 */
suspend fun loadWidgetPreview(
    context: Context,
    host: WidgetHost,
    info: AppWidgetProviderInfo,
    widthPx: Int,
    appSupplied: RemoteViews? = null,
): PreviewContent =
    withContext(Dispatchers.IO) {
        val generated = if (appSupplied == null) host.generatedPreview(info) else null
        val facts =
            PreviewFacts(
                appSupplied = appSupplied != null,
                generated = generated != null,
                layout = info.previewLayout != Resources.ID_NULL,
                image = info.previewImage != Resources.ID_NULL,
            )
        when (WidgetPreviewSource.of(facts)) {
            WidgetPreviewSource.AppSupplied -> PreviewContent.Remote(info, appSupplied)
            WidgetPreviewSource.Generated -> PreviewContent.Remote(info, generated)
            // Launcher3's trick: a copy of the provider whose initial layout is the preview layout.
            WidgetPreviewSource.Layout -> PreviewContent.Remote(info.clone().apply { initialLayout = previewLayout }, null)
            WidgetPreviewSource.Image -> previewImage(context, info, widthPx) ?: PreviewContent.Placeholder
            WidgetPreviewSource.Placeholder -> PreviewContent.Placeholder
        }
    }

private fun previewImage(
    context: Context,
    info: AppWidgetProviderInfo,
    widthPx: Int,
): PreviewContent.Image? {
    val drawable = info.loadPreviewImage(context, 0) ?: return null
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    if (w <= 0 || h <= 0) return null
    val scale = min(1f, widthPx / w.toFloat())
    return PreviewContent.Image(
        drawable.toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1)).asImageBitmap(),
    )
}

/**
 * The preview of a widget drawn at its real size for [span] on [grid] (the Widget Catalog size
 * list), scaled down only when the space is narrower. Empty while [content] is still loading.
 */
@Composable
fun WidgetPreview(
    content: PreviewContent?,
    span: CellSpan,
    grid: CatalogGrid,
    modifier: Modifier = Modifier,
) {
    val size = WidgetCatalog.sizeList(span, grid).first()
    Box(modifier.scaledToFit(size.width, size.height)) {
        WidgetPreviewContent(content, span, Modifier.fillMaxSize())
    }
}

/** [content] filling its bounds; the placeholder draws [span]'s cells. Used for the placeholder on Home too. */
@Composable
fun WidgetPreviewContent(
    content: PreviewContent?,
    span: CellSpan,
    modifier: Modifier = Modifier,
) {
    when (content) {
        is PreviewContent.Remote ->
            // Keyed on the content: a host view shows one widget's preview only.
            key(content) {
                AndroidView(
                    factory = { context ->
                        LauncherWidgetHostView(context).apply {
                            setAppWidget(PREVIEW_ID, content.info)
                            updateAppWidget(content.views)
                        }
                    },
                    modifier = modifier,
                )
            }
        is PreviewContent.Image -> Image(content.bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
        PreviewContent.Placeholder -> PlaceholderPreview(span, modifier)
        null -> Box(modifier)
    }
}

/** Launcher3's placeholder: a rounded rect with the system widget corner radius and cell divider lines. */
@Composable
private fun PlaceholderPreview(
    span: CellSpan,
    modifier: Modifier,
) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.outlineVariant
    val radius = dimensionResource(android.R.dimen.system_app_widget_background_radius)
    Canvas(modifier) {
        val r = radius.toPx()
        drawRoundRect(fill, cornerRadius = CornerRadius(r, r))
        val stroke = 1.dp.toPx()
        for (column in 1 until span.width) {
            val x = size.width * column / span.width
            drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        for (row in 1 until span.height) {
            val y = size.height * row / span.height
            drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Lays the content out at [widthDp] × [heightDp] and scales it down to fit a narrower width. */
private fun Modifier.scaledToFit(
    widthDp: Float,
    heightDp: Float,
): Modifier =
    layout { measurable, constraints ->
        val width = widthDp.dp.roundToPx().coerceAtLeast(1)
        val height = heightDp.dp.roundToPx().coerceAtLeast(1)
        val scale = if (constraints.hasBoundedWidth) min(1f, constraints.maxWidth / width.toFloat()) else 1f
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout((width * scale).roundToInt(), (height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

/** A preview is not a bound widget. */
private const val PREVIEW_ID = -1

/** The placeholder of a widget being added (PRD 7.4): its preview, filling its target cells. */
@Composable
fun PendingWidgetPreview(
    host: WidgetHost,
    pending: PendingWidget,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val content by produceState<PreviewContent?>(null, pending.row.appWidgetId) {
        value = loadWidgetPreview(context, host, pending.info, widthPx)
    }
    WidgetPreviewContent(content, pending.row.cells.span, modifier)
}
