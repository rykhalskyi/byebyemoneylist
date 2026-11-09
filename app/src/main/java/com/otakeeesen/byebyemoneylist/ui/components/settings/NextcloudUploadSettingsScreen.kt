package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudUploadSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val uploadRepository = remember {
        (context.applicationContext as ByeByeMoneyApplication).purchaseUploadRepository
    }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(preferencesManager.getNextcloudUrl()) }
    var username by remember { mutableStateOf(preferencesManager.getNextcloudUsername()) }
    var password by remember { mutableStateOf(preferencesManager.getNextcloudPassword()) }
    var testing by remember { mutableStateOf(false) }

    val connectionOk = stringResource(R.string.nextcloud_connection_ok)
    val connectionFailedTemplate = stringResource(R.string.nextcloud_connection_failed)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nextcloud_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_go_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.nextcloud_upload_description),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    preferencesManager.setNextcloudUrl(it)
                },
                label = { Text(stringResource(R.string.nextcloud_server_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    preferencesManager.setNextcloudUsername(it)
                },
                label = { Text(stringResource(R.string.nextcloud_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    preferencesManager.setNextcloudPassword(it)
                },
                label = { Text(stringResource(R.string.nextcloud_app_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    testing = true
                    scope.launch {
                        val result = uploadRepository.testConnection(serverUrl, username, password)
                        val message = result.fold(
                            onSuccess = { connectionOk },
                            onFailure = { error ->
                                connectionFailedTemplate.format(
                                    error.localizedMessage ?: error.message ?: "Unknown error",
                                )
                            },
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        testing = false
                    }
                },
                enabled = !testing && serverUrl.isNotBlank() &&
                    username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (testing) {
                        stringResource(R.string.nextcloud_testing_connection)
                    } else {
                        stringResource(R.string.nextcloud_test_connection)
                    }
                )
            }
        }
    }
}
