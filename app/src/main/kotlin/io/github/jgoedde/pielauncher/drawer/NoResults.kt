package io.github.jgoedde.pielauncher.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.jgoedde.pielauncher.R

private const val SETTLE_ROTATION_DEGREES = 40f

/**
 * Shown when a search has no hits: a hint and a large `search_off` symbol on a Monet container
 * colour, inside an Expressive shape that morphs from a circle into a cookie as it appears.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoResults(
    query: String,
    modifier: Modifier = Modifier,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow))
    }
    val container = MaterialTheme.colorScheme.tertiaryContainer

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            Modifier
                .size(160.dp)
                .drawWithCache {
                    val path = Path()
                    val scale = Matrix().apply { scale(size.width, size.height) }
                    onDrawBehind {
                        path.rewind()
                        morph.toPath(progress.value, path)
                        path.transform(scale)
                        rotate(SETTLE_ROTATION_DEGREES * progress.value) { drawPath(path, container) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.search_off),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.drawer_no_results, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
