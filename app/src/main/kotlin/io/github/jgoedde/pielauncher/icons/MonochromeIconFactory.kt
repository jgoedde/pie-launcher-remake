/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by Pie Launcher: MonochromeIconFactory's flattening, with its pixel processing moved to
 * :core (generatedMonochrome); Launcher3 BaseIconFactory's legacy-icon wrapping; rendering of
 * monochrome layers and Original Icons to the visible part of the adaptive layer.
 */
package io.github.jgoedde.pielauncher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.jgoedde.pielauncher.core.icons.IconTraits
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import io.github.jgoedde.pielauncher.core.icons.generatedMonochrome as generatedMonochromePixels

/**
 * Renders icon drawables to the visible area of an adaptive icon, [sizePx] square: masks as
 * ALPHA_8 bitmaps, Original Icons as ARGB bitmaps. Clipping to the Icon Shape happens at draw time.
 */
internal object MonochromeIconFactory {
    /** What [maskSource][io.github.jgoedde.pielauncher.core.icons.maskSource] needs to know of [drawable]. */
    fun traits(drawable: Drawable) =
        IconTraits(
            hasMonochrome = (drawable as? AdaptiveIconDrawable)?.monochrome != null,
            // The source type of a Shortcut icon is hidden API; a bitmap foreground is the tell.
            isBitmap = drawable is BitmapDrawable || (drawable as? AdaptiveIconDrawable)?.foreground is BitmapDrawable,
        )

    /** The Generated Monochrome of [drawable]; a legacy icon is wrapped to adaptive first. */
    fun generatedMonochrome(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap {
        val icon = wrapToAdaptive(drawable)
        val layer = layerSize(sizePx)
        val flat = createBitmap(layer, layer)
        Canvas(flat).apply {
            drawColor(Color.BLACK)
            drawLayer(icon.background, layer)
            drawLayer(icon.foreground, layer)
        }
        val pixels = IntArray(layer * layer)
        flat.getPixels(pixels, 0, layer, 0, 0, layer, layer)
        flat.recycle()
        return alphaBitmap(generatedMonochromePixels(pixels, layer, sizePx), sizePx)
    }

    /** [drawable]'s own monochrome layer as a mask; null when it has none. */
    fun monochromeLayer(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap? {
        val monochrome = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
        return createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8).also { drawVisible(it, listOf(monochrome)) }
    }

    /** [drawable] in its own colours; an adaptive icon without the system's shape mask. */
    fun original(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap {
        if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(sizePx, sizePx)
        return createBitmap(sizePx, sizePx).also { drawVisible(it, listOf(drawable.background, drawable.foreground)) }
    }

    /** A mask of [size] × [size] tightly packed alpha bytes as an ALPHA_8 bitmap. */
    fun alphaBitmap(
        alpha: ByteArray,
        size: Int,
    ): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val rowBytes = bitmap.rowBytes
        val padded =
            if (rowBytes == size) {
                alpha
            } else {
                ByteArray(rowBytes * size).also { out -> repeat(size) { alpha.copyInto(out, it * rowBytes, it * size, it * size + size) } }
            }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(padded))
        return bitmap
    }

    /** Draws adaptive [layers] so their visible centre fills [target]. */
    private fun drawVisible(
        target: Bitmap,
        layers: List<Drawable?>,
    ) {
        val size = target.width
        val layer = layerSize(size)
        val canvas = Canvas(target)
        val inset = (layer - size) / 2f
        canvas.translate(-inset, -inset)
        layers.forEach { canvas.drawLayer(it, layer) }
    }

    private fun Canvas.drawLayer(
        layer: Drawable?,
        layerPx: Int,
    ) {
        layer ?: return
        layer.setBounds(0, 0, layerPx, layerPx)
        layer.draw(this)
    }

    /** An adaptive layer is 108 dp of which the centre 72 dp are visible. */
    private fun layerSize(visiblePx: Int) = (visiblePx * (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())).roundToInt()

    /**
     * Launcher3's wrapping of a legacy icon: centred on a white background at its legacy scale.
     * Launcher3 also shrinks the icon by its visible area first (IconNormalizer); this port does not.
     */
    private fun wrapToAdaptive(drawable: Drawable): AdaptiveIconDrawable =
        drawable as? AdaptiveIconDrawable
            ?: AdaptiveIconDrawable(ColorDrawable(Color.WHITE), InsetDrawable(drawable, (1 - legacyIconScale) / 2))

    /** Launcher3 `LEGACY_ICON_SCALE`: 70 % of the visible area. */
    private val legacyIconScale = .7f * (1f / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction()))
}
