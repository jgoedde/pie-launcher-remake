package io.github.jgoedde.pielauncher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.core.drawer.IndexLetter
import kotlin.math.roundToInt

/** Width of the letter strip; the list leaves this much room on its right. */
internal val FastScrollerWidth = 28.dp
private val MaxLetterHeight = 28.dp
private val BubbleSize = 72.dp
private val BubbleGap = 16.dp

/**
 * The alphabet strip on the right edge of the App Drawer. Touching or dragging along it jumps the
 * list to the letter under the finger, shows a large letter bubble beside it and ticks on every
 * letter change.
 */
@Composable
internal fun FastScroller(
    letters: List<IndexLetter>,
    onDragStart: () -> Unit,
    onLetter: (IndexLetter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    var active by remember(letters) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier.width(FastScrollerWidth)) {
        val density = LocalDensity.current
        val letterHeight = minOf(maxHeight / letters.size, MaxLetterHeight)
        val letterHeightPx = with(density) { letterHeight.toPx() }
        // The letters are centred vertically; touches anywhere on the strip map onto them.
        val topPx = (constraints.maxHeight - letterHeightPx * letters.size) / 2f

        fun select(y: Float) {
            val i = ((y - topPx) / letterHeightPx).toInt().coerceIn(letters.indices)
            if (i == active) return
            active = i
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            currentOnLetter(letters[i])
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(letters, topPx, letterHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            currentOnDragStart()
                            select(down.position.y)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChange().y != 0f) select(change.position.y)
                                change.consume()
                            }
                            active = null
                        }
                    },
        ) {
            letters.forEachIndexed { i, letter ->
                Text(
                    text = letter.letter,
                    modifier = Modifier.fillMaxWidth().height(letterHeight),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        active?.let { i ->
            val bubbleY = topPx + letterHeightPx * (i + 0.5f) - with(density) { BubbleSize.toPx() } / 2f
            val bubbleX = with(density) { -(BubbleSize + BubbleGap).toPx() }
            Box(
                Modifier
                    .offset { IntOffset(bubbleX.roundToInt(), bubbleY.roundToInt()) }
                    .size(BubbleSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letters[i].letter,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
