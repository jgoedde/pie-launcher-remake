package io.github.jgoedde.pielauncher.pie

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.config.PieColor
import io.github.jgoedde.pielauncher.core.pie.Bounds
import io.github.jgoedde.pielauncher.core.pie.PieGeometry
import io.github.jgoedde.pielauncher.core.pie.PieState
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.icons.IconShape
import io.github.jgoedde.pielauncher.icons.LauncherIcon
import io.github.jgoedde.pielauncher.icons.LauncherIconImage
import kotlin.math.roundToInt

private val IconSize = 44.dp
private const val SCRIM_ALPHA = 0.4f
private const val HOVER_SCALE = 1.2f

/**
 * The open Pie (PRD 4.2), drawn over the whole window: a ~40 % black scrim over everything,
 * including widgets and the status bar, and the current Layer's ring. Pie Items pop out from the
 * centre and fade with the scrim. The hovered item gets an outline and scales up. Also tells
 * [controller] the window bounds it clamps the ring into.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PieOverlay(
    controller: PieController,
    iconLoader: IconLoader,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val bars = WindowInsets.systemBars
        val window =
            Bounds(
                left = bars.getLeft(density, direction).toFloat(),
                top = bars.getTop(density).toFloat(),
                right = constraints.maxWidth - bars.getRight(density, direction).toFloat(),
                bottom = constraints.maxHeight - bars.getBottom(density).toFloat(),
            )
        SideEffect { controller.window = window }

        val state = controller.state
        // The last open state stays drawn while the Pie fades out.
        val lastOpen = remember { arrayOfNulls<PieState.Open>(1) }
        if (state is PieState.Open) lastOpen[0] = state
        val open = state is PieState.Open
        val motion = MaterialTheme.motionScheme
        val fade by animateFloatAsState(if (open) 1f else 0f, motion.defaultEffectsSpec(), label = "pie fade")
        val spread by animateFloatAsState(if (open) 1f else 0f, motion.defaultSpatialSpec(), label = "pie spread")
        val pie = lastOpen[0] ?: return@BoxWithConstraints
        if (fade == 0f && !open) return@BoxWithConstraints

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA * fade)))
        val items = pie.layer.items
        val itemSizePx = controller.metrics.itemRadius * 2
        items.forEachIndexed { index, item ->
            val position = PieGeometry.itemPosition(pie.centre, index, items.size, controller.metrics)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (pie.centre.x + (position.x - pie.centre.x) * spread - itemSizePx / 2).roundToInt(),
                            (pie.centre.y + (position.y - pie.centre.y) * spread - itemSizePx / 2).roundToInt(),
                        )
                    }.graphicsLayer { alpha = fade },
            ) {
                PieItemView(
                    item = controller.resolve(item),
                    hovered = open && pie.hovered == index,
                    sizePx = itemSizePx,
                    iconLoader = iconLoader,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PieItemView(
    item: ResolvedItem,
    hovered: Boolean,
    sizePx: Float,
    iconLoader: IconLoader,
) {
    val scale by animateFloatAsState(
        if (hovered) HOVER_SCALE else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hover scale",
    )
    val outline = if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent
    val size = with(LocalDensity.current) { sizePx.toDp() }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.border(2.dp, outline, IconShape),
        contentAlignment = Alignment.Center,
    ) {
        when (item) {
            is ResolvedItem.App -> {
                val iconPx = with(LocalDensity.current) { IconSize.roundToPx() }
                val icon: LauncherIcon? by produceState(iconLoader.cached(item.app, iconPx), item.app, iconPx) {
                    if (value == null) value = iconLoader.appIcon(item.app, iconPx)
                }
                LauncherIconImage(icon, IconSize)
            }
            // The target Layer's symbol arrives with Layer Switches (#30).
            is ResolvedItem.LayerSwitch -> Box(Modifier.size(IconSize).clip(IconShape).background(item.color.toColor()))
            ResolvedItem.Unavailable ->
                Box(
                    Modifier.size(IconSize).clip(IconShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.pie_unavailable),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(IconSize / 2),
                    )
                }
        }
    }
}

@Composable
private fun PieColor.toColor(): Color =
    when (this) {
        is PieColor.Hex -> Color(0xFF000000.toInt() or rgb)
        PieColor.Role.Primary -> MaterialTheme.colorScheme.primary
        PieColor.Role.Secondary -> MaterialTheme.colorScheme.secondary
        PieColor.Role.Tertiary -> MaterialTheme.colorScheme.tertiary
    }
