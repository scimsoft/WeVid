package com.scimsoft.wevid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.scimsoft.wevid.R

/** Reason keys stored on the report document. */
private val reportReasons = listOf(
    "inappropriate" to R.string.report_reason_inappropriate,
    "harassment" to R.string.report_reason_harassment,
    "child_safety" to R.string.report_reason_child_safety,
    "spam" to R.string.report_reason_spam,
    "other" to R.string.report_reason_other,
)

@Composable
fun ReportDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String) -> Unit,
) {
    var selected by remember { mutableStateOf(reportReasons.first().first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                reportReasons.forEach { (key, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = key },
                    ) {
                        RadioButton(
                            selected = selected == key,
                            onClick = { selected = key },
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(selected) }) {
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
