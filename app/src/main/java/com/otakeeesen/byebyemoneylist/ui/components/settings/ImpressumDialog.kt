package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.otakeeesen.byebyemoneylist.BuildConfig
import com.otakeeesen.byebyemoneylist.R

@Composable
fun ImpressumDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val email = "otakeesen@gmail.com"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_impressum)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.impressum_text, BuildConfig.IMPRESSUM_PUBLISHER, email),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$email")
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.contact_developer)))
                }
            ) {
                Text(stringResource(R.string.contact_developer))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_ok))
            }
        }
    )
}
