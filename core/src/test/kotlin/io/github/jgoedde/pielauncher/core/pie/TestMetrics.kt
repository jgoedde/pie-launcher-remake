package io.github.jgoedde.pielauncher.core.pie

/**
 * Fixed Pie sizes for tests (the prototype's px at density 1), so tuning the defaults in
 * [PieMetrics.scaled] on the device does not move every expected position.
 */
internal val testMetrics =
    PieMetrics(
        radius = 120f,
        captureRadius = 30f,
        centreRadius = 26f,
        itemRadius = 26f,
        margin = 8f,
        cardWidth = 184f,
        cardRowHeight = 44f,
        cardPadding = 6f,
        cardGap = 10f,
    )
