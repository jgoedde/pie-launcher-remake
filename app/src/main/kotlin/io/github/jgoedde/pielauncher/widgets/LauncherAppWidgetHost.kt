package io.github.jgoedde.pielauncher.widgets

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.core.view.children
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.WidgetBackground
import io.github.jgoedde.pielauncher.core.home.WidgetViewTree
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

    /** Forgets every view created so far, so a destroyed Activity's views are not kept alive. */
    fun releaseViews() = clearViews()

    companion object {
        const val HOST_ID = 0x5069_6501
    }
}

/**
 * A hosted widget (PRD 7.1), drawn like Launcher3: inflates RemoteViews off the main thread,
 * fills its cells without the framework's default padding, is clipped to the system widget
 * corner radius around its background unless it rounds itself, picks its light-background layout
 * on a light wallpaper, and shows its own error view when it cannot be drawn. No bitmaps are
 * cached. Widgets take their dynamic colours from the system themselves (no `setColorResources`).
 */
internal class LauncherWidgetHostView(
    context: Context,
) : AppWidgetHostView(context) {
    /** While set, `setPadding` is ignored: the framework pads every widget by 8 dp in `setAppWidget`. */
    private var settingWidget = false

    private val cornerRadius = resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    private val roundedBounds = Rect()
    private var roundsCorners = false

    init {
        setExecutor(Dispatchers.Default.asExecutor())
        setOnLightBackground(wallpaperWantsDarkText(context))
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    if (roundsCorners) {
                        outline.setRoundRect(roundedBounds, cornerRadius)
                    } else {
                        outline.setRect(0, 0, view.width, view.height)
                        outline.alpha = 0f
                    }
                }
            }
        clipToOutline = true
    }

    override fun setAppWidget(
        appWidgetId: Int,
        info: AppWidgetProviderInfo?,
    ) {
        settingWidget = true
        try {
            super.setAppWidget(appWidgetId, info)
        } finally {
            settingWidget = false
        }
    }

    override fun setPadding(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (!settingWidget) super.setPadding(left, top, right, bottom)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        enforceRoundedCorners()
    }

    /** Fits the outline to the widget's background, in this view's coordinates (Launcher3 `enforceRoundedCorners`). */
    private fun enforceRoundedCorners() {
        val background = WidgetBackground.find<View>(this, ViewTree)
        roundsCorners = cornerRadius > 0f && !WidgetBackground.optsOut(background, ViewTree)
        if (roundsCorners) {
            roundedBounds.set(0, 0, background.width, background.height)
            if (background !== this) offsetDescendantRectToMyCoords(background, roundedBounds)
        }
        invalidateOutline()
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

/** A widget's view tree as background detection sees it. */
private object ViewTree : WidgetViewTree<View> {
    override fun children(view: View): List<View> = (view as? ViewGroup)?.children?.toList().orEmpty()

    override fun isVisible(view: View): Boolean = view.visibility == View.VISIBLE

    override fun draws(view: View): Boolean = view.background != null || view.foreground != null || !view.willNotDraw()

    override fun hasBackgroundId(view: View): Boolean = view.id == android.R.id.background

    override fun clipsToOutline(view: View): Boolean = view.clipToOutline
}

/** Whether the wallpaper wants dark text on it (a light wallpaper), as the workspace uses it in Launcher3. */
private fun wallpaperWantsDarkText(context: Context): Boolean {
    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return false
    return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
}
