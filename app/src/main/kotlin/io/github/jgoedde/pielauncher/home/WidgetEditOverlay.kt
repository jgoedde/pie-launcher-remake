package io.github.jgoedde.pielauncher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import io.github.jgoedde.pielauncher.core.home.EditMode

/**
 * The drop-target bar at the top while a widget is dragged (PRD 7.3, Launcher3
 * `DeleteDropTarget` and `SecondaryDropTarget`): "Remove", plus "Widget settings" when the
 * widget is reconfigurable. A button lights up while the finger is over it.
 */
@Composable
internal fun DropTargetBar(
    editor: WidgetEditor,
    modifier: Modifier = Modifier,
) {
    // A new widget dragged in from outside is not placed yet: nothing to remove or set up.
    val dragging = (editor.mode as? EditMode.Dragging)?.drag?.dragged?.isNew == false
    AnimatedVisibility(dragging, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            DropTarget(editor, DropAction.Remove, R.string.widget_remove, R.drawable.close)
            if (editor.draggedIsReconfigurable) {
                DropTarget(editor, DropAction.WidgetSettings, R.string.widget_settings, R.drawable.settings)
            }
        }
    }
}

@Composable
private fun DropTarget(
    editor: WidgetEditor,
    action: DropAction,
    label: Int,
    icon: Int,
) {
    val hovered = editor.hovered == action
    val colors = MaterialTheme.colorScheme
    val background =
        when {
            !hovered -> Color.Transparent
            action == DropAction.Remove -> colors.errorContainer
            else -> colors.primaryContainer
        }
    val content =
        when {
            !hovered -> Color.White
            action == DropAction.Remove -> colors.onErrorContainer
            else -> colors.onPrimaryContainer
        }
    Row(
        Modifier
            .onGloballyPositioned { editor.dropTargets[action] = it.boundsInWindow() }
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(stringResource(label), color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/** The dragged widget's picture under the finger, above everything on the Home Page. */
@Composable
internal fun DragPicture(editor: WidgetEditor) {
    val image = editor.dragImage ?: return
    Box(
        Modifier.graphicsLayer {
            translationX = image.finger.x - image.grab.x
            translationY = image.finger.y - image.grab.y
        },
    ) {
        Image(image.image, contentDescription = null)
    }
}
