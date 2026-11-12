package io.github.jgoedde.pielauncher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jgoedde.pielauncher.R

/** The import confirmation (PRD 10.3, stock M3 `AlertDialog`): Apply only when there are no errors. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportDialogHost(
    dialog: ImportDialog?,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        ImportDialog.Checking, ImportDialog.Applying ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator()
                        Text(stringResource(if (dialog == ImportDialog.Applying) R.string.import_applying else R.string.import_checking))
                    }
                },
            )
        is ImportDialog.Review -> {
            val accepted = dialog.accepted
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_review_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(
                                R.string.import_summary,
                                pluralStringResource(R.plurals.layers, accepted.layerCount, accepted.layerCount),
                                pluralStringResource(R.plurals.items, accepted.itemCount, accepted.itemCount),
                                pluralStringResource(R.plurals.warnings, accepted.warnings.size, accepted.warnings.size),
                            ),
                        )
                        Messages(accepted.warnings.map { it.path to it.message })
                    }
                },
                confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.import_apply)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_cancel)) } },
            )
        }
        is ImportDialog.Rejected ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_rejected_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.import_rejected_text))
                        Messages(dialog.errors.map { it.path to it.message })
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_close)) } },
            )
        ImportDialog.ApplyFailed ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_apply_failed_title)) },
                text = { Text(stringResource(R.string.import_apply_failed_text)) },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_close)) } },
            )
    }
}

/** Tapping the status row: the last import's warnings plus the current Unavailable Items. */
@Composable
fun StatusDialog(
    status: ConfigStatus?,
    unavailable: List<UnavailableItem>,
    onDismiss: () -> Unit,
) {
    val record = status?.record
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_status_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Heading(stringResource(R.string.settings_status_last_import)) }
                when {
                    record == null -> item { Text(stringResource(R.string.settings_status_default_details)) }
                    record.warnings.isEmpty() -> item { Text(stringResource(R.string.settings_status_no_warnings)) }
                    else -> items(record.warnings) { Message(it.path, it.message) }
                }
                item { Heading(stringResource(R.string.settings_status_unavailable)) }
                if (unavailable.isEmpty()) {
                    item { Text(stringResource(R.string.settings_status_no_unavailable)) }
                } else {
                    items(unavailable) { Text(stringResource(R.string.unavailable_item, it.layerName, it.item, it.target)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_close)) } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Path and message pairs, scrollable when long. */
@Composable
private fun Messages(messages: List<Pair<String, String>>) {
    if (messages.isEmpty()) return
    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(messages) { (path, message) -> Message(path, message) }
    }
}

@Composable
private fun Message(
    path: String,
    message: String,
) {
    Column {
        Text(path, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.settings_back))
    }
}
