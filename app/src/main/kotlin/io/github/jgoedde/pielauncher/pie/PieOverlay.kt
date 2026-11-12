package io.github.jgoedde.pielauncher.pie

import android.content.pm.ShortcutInfo
import android.util.DisplayMetrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.config.PieColor
import io.github.jgoedde.pielauncher.core.config.PieItem
import io.github.jgoedde.pielauncher.core.pie.Bounds
import io.github.jgoedde.pielauncher.core.pie.PieGeometry
import io.github.jgoedde.pielauncher.core.pie.PieState
import io.github.jgoedde.pielauncher.core.pie.Point
import io.github.jgoedde.pielauncher.core.pie.ShortcutList
import io.github.jgoedde.pielauncher.icons.IconLoader
import io.github.jgoedde.pielauncher.icons.LauncherIcon
import io.github.jgoedde.pielauncher.icons.LauncherIconImage
import io.github.jgoedde.pielauncher.icons.LocalIconShape
import kotlin.math.roundToInt

private val IconSize = 52.dp
private val RowIconSize = 20.dp
private const val SCRIM_ALPHA = 0.4f
private const val HOVER_SCALE = 1.2f

/** Share of the item's colour in the Progress Disc's unfilled part. */
private const val DISC_TRACK_ALPHA = 0.2f

/** How far the Progress Disc turns while it fills, in degrees; kept small for a calm sweep. */
private const val DISC_TURN = 90f

/** Layer Switch symbols on light colours are drawn dark, on dark colours light. */
private const val LIGHT_COLOR_LUMINANCE = 0.4f

private val SymbolsFont = FontFamily(Font(R.font.material_symbols_rounded))

/**
 * The open Pie (PRD 4.2, 4.4, 4.6), drawn over the whole window: a ~40 % black scrim over
 * everything, including widgets and the status bar; the Progress Disc in the Inner Circle; the
 * current Layer's ring; and an open Shortcut list card. Pie Items spread out from the centre on
 * open and on every Layer Switch, and fold back as the Pie fades. Motion follows the M3 Expressive
 * motion scheme. Also tells [controller] the window bounds it keeps the Pie inside.
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
        val motion = remember { MotionScheme.expressive() }
        val fade by animateFloatAsState(if (open) 1f else 0f, motion.defaultEffectsSpec(), label = "pie fade")
        val spread = remember { Animatable(0f) }
        val layerKey = (state as? PieState.Open)?.let { it.layer.id to it.centre }
        val previousLayerKey = remember { arrayOfNulls<Any>(1) }
        LaunchedEffect(layerKey) {
            val switched = previousLayerKey[0] != null && layerKey != null
            previousLayerKey[0] = layerKey
            when {
                layerKey == null -> spread.animateTo(0f, motion.fastSpatialSpec())
                // A Layer Switch shows the new Layer at once, without spreading it out again.
                switched -> spread.snapTo(1f)
                else -> {
                    spread.snapTo(0f)
                    spread.animateTo(1f, motion.defaultSpatialSpec())
                }
            }
        }
        val pie = lastOpen[0] ?: return@BoxWithConstraints
        if (fade == 0f && !open) return@BoxWithConstraints

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA * fade)))

        val hoveredItem = pie.hovered?.let(pie.layer.items::getOrNull)?.let(controller::resolve)
        val discColor = (hoveredItem as? ResolvedItem.LayerSwitch)?.color?.toColor() ?: MaterialTheme.colorScheme.primary
        val items = pie.layer.items
        val itemSizePx = controller.metrics.itemRadius * 2
        // Keyed by Layer and centre: after a Layer Switch the disc and the items start fresh, with no
        // animation carried over from the previous Layer.
        key(pie.layer.id, pie.centre) {
            ProgressDisc(
                progress = if (open) controller.dwellProgress else null,
                color = discColor,
                centre = pie.centre,
                radiusPx = controller.metrics.innerRadius,
                motion = motion,
            )
            items.forEachIndexed { index, item ->
                val position = PieGeometry.itemPosition(pie.centre, index, items.size, controller.metrics)
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (pie.centre.x + (position.x - pie.centre.x) * spread.value - itemSizePx / 2).roundToInt(),
                                (pie.centre.y + (position.y - pie.centre.y) * spread.value - itemSizePx / 2).roundToInt(),
                            )
                        }.graphicsLayer { alpha = fade },
                ) {
                    PieItemView(
                        item = controller.resolve(item),
                        // The app whose Shortcut list is open keeps its hover outline while the list is open.
                        hovered = open && (index == pie.hovered || index == pie.shortcuts?.item),
                        sizePx = itemSizePx,
                        iconLoader = iconLoader,
                        motion = motion,
                    )
                }
            }
        }

        ShortcutCard(
            list = pie.shortcuts.takeIf { open },
            item = pie.shortcuts?.let { items.getOrNull(it.item) },
            itemPosition = pie.shortcuts?.let { PieGeometry.itemPosition(pie.centre, it.item, items.size, controller.metrics) },
            hoveredRow = pie.hoveredRow,
            controller = controller,
            iconLoader = iconLoader,
            motion = motion,
        )
    }
}

/**
 * The Progress Disc (PRD 4.4): the full Inner Circle, filling as a clock sweep from 12 o'clock
 * over a faint track of the same colour. It softens from a circle into a cookie shape and turns a
 * little as it fills.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressDisc(
    progress: Float?,
    color: Color,
    centre: Point,
    radiusPx: Float,
    motion: MotionScheme,
) {
    val shown by animateFloatAsState(if (progress != null) 1f else 0f, motion.fastEffectsSpec(), label = "disc")
    if (shown == 0f) return
    val lastProgress = remember { floatArrayOf(0f) }
    if (progress != null) lastProgress[0] = progress
    val p = lastProgress[0]
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val size = with(LocalDensity.current) { (radiusPx * 2).toDp() }
    Canvas(
        Modifier
            .offset { IntOffset((centre.x - radiusPx).roundToInt(), (centre.y - radiusPx).roundToInt()) }
            .size(size)
            .graphicsLayer {
                alpha = shown
                scaleX = 0.6f + 0.4f * shown
                scaleY = 0.6f + 0.4f * shown
            },
    ) {
        val path = morph.toPath(p).apply { transform(Matrix().apply { scale(this@Canvas.size.width, this@Canvas.size.height) }) }
        rotate(DISC_TURN * p) {
            drawPath(path, color.copy(alpha = DISC_TRACK_ALPHA))
            clipPath(path) { drawArc(color, startAngle = -90f, sweepAngle = 360f * p, useCenter = true) }
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
    motion: MotionScheme,
) {
    val scale by animateFloatAsState(if (hovered) HOVER_SCALE else 1f, motion.fastSpatialSpec(), label = "hover scale")
    val outline = if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent
    val size = with(LocalDensity.current) { sizePx.toDp() }
    val shape = LocalIconShape.current
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.border(2.dp, outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (item) {
            is ResolvedItem.App -> AppIcon(item, iconLoader)
            is ResolvedItem.Shortcut -> ShortcutIcon(item.shortcut, iconLoader, IconSize)
            is ResolvedItem.LayerSwitch -> LayerSwitchIcon(item)
            ResolvedItem.Unavailable ->
                Box(
                    Modifier.size(IconSize).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
private fun AppIcon(
    item: ResolvedItem.App,
    iconLoader: IconLoader,
) {
    val iconPx = with(LocalDensity.current) { IconSize.roundToPx() }
    val themed by iconLoader.themedIcons.collectAsState()
    // Always reload on a new app: the previous app's icon must never stay in place.
    val icon: LauncherIcon? by produceState(iconLoader.cached(item.app, iconPx), item.app, iconPx, themed) {
        value = iconLoader.cached(item.app, iconPx) ?: iconLoader.appIcon(item.app, iconPx)
    }
    LauncherIconImage(icon, IconSize)
}

/** A Shortcut's own icon, else its app's icon. Pinned Shortcuts of browsers are PWAs (PRD 8). */
@Composable
private fun ShortcutIcon(
    shortcut: ShortcutInfo,
    iconLoader: IconLoader,
    size: Dp,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val densityDpi = (density.density * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    val themed by iconLoader.themedIcons.collectAsState()
    val icon: LauncherIcon? by produceState<LauncherIcon?>(null, shortcut, sizePx, themed) {
        value = iconLoader.shortcutIcon(shortcut, sizePx, densityDpi, pinned = shortcut.isPinned)
            ?: iconLoader.ownerIcon(shortcut.`package`, shortcut.userHandle, sizePx)
    }
    LauncherIconImage(icon, size)
}

/**
 * The target Layer's Material Symbol on the target Layer's colour, light or dark for contrast,
 * clipped to the Icon Shape. Not affected by the Themed Icons switch.
 */
@Composable
private fun LayerSwitchIcon(item: ResolvedItem.LayerSwitch) {
    val color = item.color.toColor()
    val symbolColor = if (color.luminance() > LIGHT_COLOR_LUMINANCE) Color.Black.copy(alpha = 0.87f) else Color.White
    val fontSize = with(LocalDensity.current) { (IconSize * 0.55f).toSp() }
    Box(
        Modifier.size(IconSize).clip(LocalIconShape.current).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            String(Character.toChars(item.symbol)),
            color = symbolColor,
            style = TextStyle(fontFamily = SymbolsFont, fontSize = fontSize, lineHeight = fontSize),
        )
    }
}

/**
 * The Shortcut list card (PRD 4.6), placed by `:core` beside the app's icon. Rows sit at the
 * heights the gesture hit-tests them at; the hovered row is highlighted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutCard(
    list: ShortcutList?,
    item: PieItem?,
    itemPosition: Point?,
    hoveredRow: Int?,
    controller: PieController,
    iconLoader: IconLoader,
    motion: MotionScheme,
) {
    // The last list stays drawn while the card animates out.
    val last = remember { arrayOfNulls<ShownCard>(1) }
    if (list != null && item != null && itemPosition != null) {
        (controller.resolve(item) as? ResolvedItem.App)?.let { last[0] = ShownCard(list.card, itemPosition, it.shortcuts) }
    }
    val (card, icon, shortcuts) = last[0] ?: return
    val metrics = controller.metrics
    val density = LocalDensity.current
    // The card grows out of the corner nearest the icon.
    val origin =
        TransformOrigin(
            pivotFractionX = if (card.left >= icon.x) 0f else 1f,
            pivotFractionY = if (card.top >= icon.y - metrics.itemRadius) 0f else 1f,
        )
    AnimatedVisibility(
        visible = list != null,
        modifier = Modifier.offset { IntOffset(card.left.roundToInt(), card.top.roundToInt()) },
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f, transformOrigin = origin),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f, transformOrigin = origin),
    ) {
        with(density) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.width((card.right - card.left).toDp()),
            ) {
                Column(Modifier.padding(vertical = metrics.cardPadding.toDp(), horizontal = metrics.cardPadding.toDp())) {
                    shortcuts.forEachIndexed { row, shortcut ->
                        ShortcutRow(shortcut, hovered = row == hoveredRow, heightPx = metrics.cardRowHeight, iconLoader = iconLoader)
                    }
                }
            }
        }
    }
}

private data class ShownCard(
    val card: Bounds,
    val icon: Point,
    val shortcuts: List<ShortcutInfo>,
)

@Composable
private fun ShortcutRow(
    shortcut: ShortcutInfo,
    hovered: Boolean,
    heightPx: Float,
    iconLoader: IconLoader,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { heightPx.toDp() })
            .clip(MaterialTheme.shapes.medium)
            .background(if (hovered) colors.secondaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShortcutIcon(shortcut, iconLoader, RowIconSize)
        Text(
            (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
            color = if (hovered) colors.onSecondaryContainer else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
