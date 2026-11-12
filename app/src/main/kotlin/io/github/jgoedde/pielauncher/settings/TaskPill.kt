package io.github.jgoedde.pielauncher.settings

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R
import kotlinx.coroutines.delay

/**
 * The task pill: drops in under the app bar while a Pie Config task runs, its LoadingIndicator
 * settles into a check (or a cross in `error` colour) when the task ends, then it leaves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TaskPill(
    feedback: TaskFeedback?,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<TaskFeedback?>(null) }
    var workingSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        if (feedback.state == TaskState.Working) {
            shown = feedback
            workingSince = SystemClock.uptimeMillis()
            return@LaunchedEffect
        }
        // Writing a file takes milliseconds; keep the indicator long enough to read as progress.
        if (shown?.run == feedback.run) delay(MIN_WORKING_MILLIS - (SystemClock.uptimeMillis() - workingSince))
        shown = feedback
        delay(if (feedback.state == TaskState.Failed) FAILED_HOLD_MILLIS else DONE_HOLD_MILLIS)
        shown = null
    }
    val current = shown
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
        modifier = modifier,
    ) {
        // Keeps the last content while sliding out.
        var last by remember { mutableStateOf(current) }
        if (current != null) last = current
        val pill = last ?: return@AnimatedVisibility
        val failed = pill.state == TaskState.Failed
        val container by animateColorAsState(
            if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            label = "taskPillContainer",
        )
        val content = if (failed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 6.dp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        pill.state,
                        transitionSpec = {
                            scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) togetherWith
                                scaleOut()
                        },
                        label = "taskPillIcon",
                    ) { state ->
                        when (state) {
                            TaskState.Working -> LoadingIndicator(Modifier.size(32.dp), color = content)
                            TaskState.Done -> Icon(painterResource(R.drawable.check), contentDescription = null)
                            TaskState.Failed -> Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                    }
                }
                AnimatedContent(
                    pill,
                    label = "taskPillText",
                ) { Text(stringResource(it.text()), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@StringRes
private fun TaskFeedback.text(): Int =
    when (task) {
        SettingsTask.Import -> state.pick(R.string.task_import_working, R.string.task_import_done, R.string.task_import_failed)
        SettingsTask.ExportConfig ->
            state.pick(R.string.task_export_config_working, R.string.task_export_config_done, R.string.task_export_failed)
        SettingsTask.ExportInventory ->
            state.pick(R.string.task_export_inventory_working, R.string.task_export_inventory_done, R.string.task_export_failed)
        SettingsTask.Reset -> state.pick(R.string.task_reset_working, R.string.task_reset_done, R.string.task_reset_failed)
    }

private fun TaskState.pick(
    working: Int,
    done: Int,
    failed: Int,
): Int =
    when (this) {
        TaskState.Working -> working
        TaskState.Done -> done
        TaskState.Failed -> failed
    }

private const val MIN_WORKING_MILLIS = 600L
private const val DONE_HOLD_MILLIS = 2_500L
private const val FAILED_HOLD_MILLIS = 4_000L
