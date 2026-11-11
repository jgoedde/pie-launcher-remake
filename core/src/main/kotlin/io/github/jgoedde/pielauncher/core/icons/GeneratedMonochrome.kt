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
 * Modified by Pie Launcher: the pixel processing of MonochromeIconFactory.generateMono(), as a
 * pure function over flattened ARGB pixels that returns only the visible part of the layer.
 */
package io.github.jgoedde.pielauncher.core.icons

import kotlin.math.roundToInt

/**
 * Bumped whenever [generatedMonochrome] changes its output, so cached masks are regenerated.
 * Part of every mask cache key.
 */
const val MASK_ALGORITHM_VERSION = 1

/**
 * The Generated Monochrome of an adaptive icon (port of AOSP `MonochromeIconFactory`).
 *
 * [flat] is the full adaptive layer, [layerSize] × [layerSize] ARGB pixels, with the background
 * and foreground already drawn over opaque black. Each pixel's grey value (the RGB average)
 * becomes its alpha; alphas are stretched to the full 0–255 range.
 *
 * Returns the alpha of the centred [visibleSize] × [visibleSize] part, row by row.
 */
fun generatedMonochrome(
    flat: IntArray,
    layerSize: Int,
    visibleSize: Int,
): ByteArray {
    require(flat.size == layerSize * layerSize) { "flat must hold layerSize² pixels" }
    require(visibleSize in 1..layerSize) { "visibleSize must be within the layer" }
    val alpha = IntArray(flat.size) { grey(flat[it]) }
    val min = alpha.min()
    val max = alpha.max()
    if (min < max) {
        val range = (max - min).toFloat()
        // The top and bottom edge rows lie outside the visible area and show the background. If
        // they are brighter than the middle of the range, the icon is dark on light: flip it.
        val edgePixels = layerSize * (layerSize / EDGE_ROWS_DIVISOR).coerceAtLeast(1)
        var sum = 0L
        for (i in 0 until edgePixels) sum += alpha[i] + alpha[alpha.size - 1 - i]
        val edgeMapped = (sum / (edgePixels * 2f) - min) / range
        val flip = edgeMapped > 0.5f
        for (i in alpha.indices) {
            val stretched = ((alpha[i] - min) * 0xFF / range).roundToInt()
            alpha[i] = if (flip) 0xFF - stretched else stretched
        }
    }
    val offset = (layerSize - visibleSize) / 2
    return ByteArray(visibleSize * visibleSize) { i ->
        alpha[(i / visibleSize + offset) * layerSize + i % visibleSize + offset].toByte()
    }
}

/**
 * AOSP renders the layer at 4/3 of the icon bitmap size and samples the rows outside the icon
 * bitmap, (4/3 - 1) / 2 = 1/8 of the layer, top and bottom.
 */
private const val EDGE_ROWS_DIVISOR = 8

private fun grey(argb: Int): Int = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3
