package io.github.jgoedde.pielauncher.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.jgoedde.pielauncher.core.icons.IconShape
import io.github.jgoedde.pielauncher.core.icons.IconShapes

/** An app or Shortcut icon, ready to draw. */
@Immutable
sealed interface LauncherIcon {
    /** A Themed Icon: [mask] is an ALPHA_8 bitmap, tinted with Monet colours at draw time. */
    class Themed(
        val mask: ImageBitmap,
    ) : LauncherIcon

    /** An Original Icon, in its own colours. */
    class Original(
        val image: ImageBitmap,
    ) : LauncherIcon
}

/** The Icon Shape every icon is clipped to (PRD 8.4); HomeActivity provides the stored one. */
val LocalIconShape = staticCompositionLocalOf { iconShapeOf(IconShapes.Circle) }

/** [shape] as a Compose [Shape]: its 100 × 100 SVG path scaled to the drawn size. */
fun iconShapeOf(shape: IconShape): Shape = SvgPathShape(PathParser().parsePathString(shape.path).toPath())

private class SvgPathShape(
    private val path: Path,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val scaled = Path().apply { addPath(path) }
        scaled.transform(Matrix().apply { scale(size.width / VIEWPORT, size.height / VIEWPORT) })
        return Outline.Generic(scaled)
    }

    private companion object {
        const val VIEWPORT = 100f
    }
}

/**
 * Draws [icon] clipped to the Icon Shape. A Themed Icon is its mask in a Monet foreground on a
 * Monet background (Launcher3 pairs), following the system dark mode. Empty while [icon] is null.
 */
@Composable
fun LauncherIconImage(
    icon: LauncherIcon?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).clip(LocalIconShape.current)) {
        when (icon) {
            is LauncherIcon.Themed -> {
                val dark = isSystemInDarkTheme()
                val background = colorResource(if (dark) android.R.color.system_accent2_800 else android.R.color.system_accent1_100)
                val foreground = colorResource(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_700)
                Image(
                    icon.mask,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(foreground),
                    modifier = Modifier.fillMaxSize().background(background),
                )
            }
            is LauncherIcon.Original -> Image(icon.image, contentDescription = null, modifier = Modifier.fillMaxSize())
            null -> Unit
        }
    }
}
