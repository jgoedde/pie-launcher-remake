package io.github.jgoedde.pielauncher.widgets

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.os.PersistableBundle
import android.os.UserHandle
import android.view.DragEvent
import android.view.View
import io.github.jgoedde.pielauncher.core.home.PinHandOff
import io.github.jgoedde.pielauncher.core.home.SpanBounds
import io.github.jgoedde.pielauncher.core.home.WidgetAddCodec
import io.github.jgoedde.pielauncher.core.home.WidgetDragCarry

/**
 * What a widget drag onto Home carries (ADR 0006): the system drag-and-drop hand-off from the
 * Widget Picker and the pin sheet to Home.
 */
sealed interface WidgetDragPayload {
    /** A provider from the Widget Picker: Home binds and configures it on drop. */
    data class Provider(
        val provider: ComponentName,
        val profile: UserHandle,
    ) : WidgetDragPayload

    /**
     * A widget Pin Request from the pin sheet, held in [WidgetDragHandOff.pins] under [token]:
     * Home takes it on drop, binds the widget and accepts the request, without configure.
     */
    data class Pin(
        val token: Long,
    ) : WidgetDragPayload

    /** What `:core` needs of this payload. */
    val carry: WidgetDragCarry
        get() =
            when (this) {
                is Provider -> WidgetDragCarry.Provider
                is Pin -> WidgetDragCarry.Pin(token)
            }
}

/**
 * The widget drag hand-off over system drag-and-drop (Launcher3 `DragAndDrop.startDrag`): a
 * global drag with the launcher's own MIME type. The ClipDescription carries the widget's span
 * bounds, so Home can preview the reflow from the drag's start (the ClipData itself only arrives
 * with the drop), and a Pin Request's token, so Home can release it however the drag ends; the
 * ClipData carries the [WidgetDragPayload] as an Intent.
 */
object WidgetDragHandOff {
    /** Only Home accepts drags of this type. */
    const val MIME_TYPE = "application/vnd.io.github.jgoedde.pielauncher.widget"

    private const val EXTRA_BOUNDS = "span_bounds"
    private const val EXTRA_PROVIDER = "provider"
    private const val EXTRA_PROFILE = "profile"
    private const val EXTRA_PIN_TOKEN = "pin_token"

    /**
     * Pin Requests on their way from the pin sheet to Home. The drag carries only a token: a
     * global drag's ClipData is visible to other apps' windows, and the request itself would let
     * them accept it. The pin sheet and Home share this process, so the drop takes it from here.
     */
    val pins = PinHandOff<LauncherApps.PinItemRequest>()

    /**
     * Starts dragging a widget with [bounds] from [view], drawn as [shadow] held at its centre.
     * False when the system refused the drag.
     */
    fun start(
        view: View,
        payload: WidgetDragPayload,
        bounds: SpanBounds,
        shadow: Bitmap,
    ): Boolean {
        val description =
            ClipDescription("widget", arrayOf(MIME_TYPE)).apply {
                extras =
                    PersistableBundle().apply {
                        putString(EXTRA_BOUNDS, WidgetAddCodec.encode(bounds))
                        if (payload is WidgetDragPayload.Pin) putLong(EXTRA_PIN_TOKEN, payload.token)
                    }
            }
        val intent =
            when (payload) {
                is WidgetDragPayload.Provider ->
                    Intent().putExtra(EXTRA_PROVIDER, payload.provider).putExtra(EXTRA_PROFILE, payload.profile)
                is WidgetDragPayload.Pin -> Intent().putExtra(EXTRA_PIN_TOKEN, payload.token)
            }
        return view.startDragAndDrop(ClipData(description, ClipData.Item(intent)), BitmapShadow(shadow), null, View.DRAG_FLAG_GLOBAL)
    }

    /** The span bounds of the widget [event] drags; null when it is no widget drag of ours. */
    fun boundsOf(event: DragEvent): SpanBounds? {
        val description = event.clipDescription ?: return null
        if (!description.hasMimeType(MIME_TYPE)) return null
        return WidgetAddCodec.bounds(description.extras?.getString(EXTRA_BOUNDS))
    }

    /** The token of the Pin Request the widget drag [event] holds; null for a Widget Picker drag or any other. */
    fun heldPinOf(event: DragEvent): Long? {
        val description = event.clipDescription ?: return null
        if (!description.hasMimeType(MIME_TYPE)) return null
        return description.extras?.takeIf { it.containsKey(EXTRA_PIN_TOKEN) }?.getLong(EXTRA_PIN_TOKEN)
    }

    /** What the dropped widget drag [event] carries; null on any other drop. */
    fun payloadOf(event: DragEvent): WidgetDragPayload? {
        val clip = event.clipData ?: return null
        if (!clip.description.hasMimeType(MIME_TYPE) || clip.itemCount == 0) return null
        val intent = clip.getItemAt(0).intent ?: return null
        if (intent.hasExtra(EXTRA_PIN_TOKEN)) return WidgetDragPayload.Pin(intent.getLongExtra(EXTRA_PIN_TOKEN, 0))
        val provider = intent.getParcelableExtra(EXTRA_PROVIDER, ComponentName::class.java) ?: return null
        val profile = intent.getParcelableExtra(EXTRA_PROFILE, UserHandle::class.java) ?: return null
        return WidgetDragPayload.Provider(provider, profile)
    }
}

/** A drag shadow of [bitmap], held at its centre (Home assumes the finger is on the widget's centre). */
private class BitmapShadow(
    private val bitmap: Bitmap,
) : View.DragShadowBuilder() {
    override fun onProvideShadowMetrics(
        outShadowSize: Point,
        outShadowTouchPoint: Point,
    ) {
        outShadowSize.set(bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1))
        outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2)
    }

    override fun onDrawShadow(canvas: Canvas) = canvas.drawBitmap(bitmap, 0f, 0f, null)
}
