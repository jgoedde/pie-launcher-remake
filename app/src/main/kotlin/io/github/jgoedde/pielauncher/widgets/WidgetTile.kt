package io.github.jgoedde.pielauncher.widgets

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.CellSpan
import kotlinx.coroutines.launch

/**
 * One widget in the Widget Picker or the pin sheet (Launcher3 `WidgetDetails`): its [preview],
 * then the [label] (one line), the span as "W × H" and the [description] (up to three lines).
 * A [tooLarge] widget is greyed out with "Too large for this home screen". When [showAdd], an
 * "Add" button fades in over the preview. [previewModifier] goes on the preview's touch area,
 * for a touch-and-hold drag.
 */
@Composable
fun WidgetTile(
    label: String,
    span: CellSpan,
    description: String,
    tooLarge: Boolean,
    showAdd: Boolean,
    onTap: () -> Unit,
    onAdd: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    previewModifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (tooLarge) DISABLED_ALPHA else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            preview()
            // Previews never take touches themselves: they belong to the tile.
            Box(Modifier.matchParentSize().then(previewModifier).clickable(onClick = onTap, indication = null, interactionSource = null))
            AddButton(showAdd, onAdd)
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.widget_span, span.width, span.height),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tooLarge) {
            Text(
                stringResource(R.string.widget_too_large),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The "Add" pill over a selected tile's preview, fading in and out. */
@Composable
private fun AddButton(
    visible: Boolean,
    onAdd: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(ADD_TOGGLE_MILLIS)),
        exit = fadeOut(tween(ADD_TOGGLE_MILLIS)),
    ) {
        Button(onClick = onAdd, shape = RoundedCornerShape(50), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.widget_picker_add))
        }
    }
}

/** Launcher3 `AddButtonDefaults.TOGGLE_ANIMATION_DURATION`. */
private const val ADD_TOGGLE_MILLIS = 400
private const val DISABLED_ALPHA = 0.38f

/**
 * Touch and hold on a widget preview to drag it (PRD 7.4): [recording] goes on the preview and
 * keeps it as drawn, [touch] on its touch area ([WidgetTile]'s `previewModifier`).
 */
class PreviewPickUp(
    val recording: Modifier,
    val touch: Modifier,
)

/**
 * A [PreviewPickUp] for the preview of [key]: a long press gives `LONG_PRESS` haptics and calls
 * [onPickUp] with the view to start the system drag from and the preview as a software bitmap
 * (the shadow surface may not draw hardware bitmaps). Nothing is picked up unless [enabled].
 */
@Composable
fun rememberPreviewPickUp(
    key: Any,
    enabled: Boolean,
    onPickUp: (View, Bitmap) -> Unit,
): PreviewPickUp {
    val picture = rememberGraphicsLayer()
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pickUp by rememberUpdatedState(onPickUp)
    val recording =
        Modifier.drawWithContent {
            picture.record { this@drawWithContent.drawContent() }
            drawLayer(picture)
        }
    val touch =
        if (!enabled) {
            Modifier
        } else {
            Modifier.pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            val shadow = picture.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                            pickUp(view, shadow)
                        }
                    },
                    onDrag = { change, _ -> change.consume() },
                )
            }
        }
    return PreviewPickUp(recording, touch)
}
