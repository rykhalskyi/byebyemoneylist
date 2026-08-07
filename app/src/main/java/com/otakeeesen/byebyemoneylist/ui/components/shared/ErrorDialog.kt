package com.otakeeesen.byebyemoneylist.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorDialog(
    title: String,
    errorMessage: String,
    onDismiss: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .sizeIn(maxHeight = 400.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = {
                    onDismiss()
                    onSecondaryAction()
                }) {
                    Text(secondaryActionLabel)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            } else {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(errorMessage))
                }) {
                    Text("Copy")
                }
            }
        }
    )
}
