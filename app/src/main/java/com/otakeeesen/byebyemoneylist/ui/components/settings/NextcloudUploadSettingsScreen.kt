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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.viewmodel.NextcloudUploadSettingsEvent
import com.otakeeesen.byebyemoneylist.ui.viewmodel.NextcloudUploadSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudUploadSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NextcloudUploadSettingsViewModel = viewModel(factory = NextcloudUploadSettingsViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val connectionOk = stringResource(R.string.nextcloud_connection_ok)
    val connectionFailedTemplate = stringResource(R.string.nextcloud_connection_failed)
    val insecureUrl = stringResource(R.string.nextcloud_insecure_url)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                NextcloudUploadSettingsEvent.ConnectionSuccess -> connectionOk
                is NextcloudUploadSettingsEvent.ConnectionFailure ->
                    connectionFailedTemplate.format(event.message ?: "Unknown error")
                NextcloudUploadSettingsEvent.InsecureUrl -> insecureUrl
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
                value = uiState.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { Text(stringResource(R.string.nextcloud_server_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.nextcloud_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.nextcloud_app_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTesting && uiState.serverUrl.isNotBlank() &&
                    uiState.username.isNotBlank() && uiState.password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (uiState.isTesting) {
                        stringResource(R.string.nextcloud_testing_connection)
                    } else {
                        stringResource(R.string.nextcloud_test_connection)
                    }
                )
            }
        }
    }
}
