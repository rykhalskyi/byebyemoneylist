package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudSyncSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val categorySyncRepo = remember { CategorySyncRepository(db.categoryDao(), preferencesManager) }

    var nextcloudUrl by remember { mutableStateOf(preferencesManager.getNextcloudUrl()) }
    var nextcloudUsername by remember { mutableStateOf(preferencesManager.getNextcloudUsername()) }
    var nextcloudPassword by remember { mutableStateOf(preferencesManager.getNextcloudPassword()) }
    var showNextcloudSyncDialog by remember { mutableStateOf(false) }
    var isTestingNextcloud by remember { mutableStateOf(false) }

    if (showNextcloudSyncDialog) {
        CategorySyncDialog(
            syncRepository = categorySyncRepo,
            onDismiss = { showNextcloudSyncDialog = false }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nextcloud_sync_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Server Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = nextcloudUrl,
                    onValueChange = {
                        nextcloudUrl = it
                        preferencesManager.setNextcloudUrl(it)
                    },
                    label = { Text("Nextcloud Server URL") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = nextcloudUsername,
                    onValueChange = {
                        nextcloudUsername = it
                        preferencesManager.setNextcloudUsername(it)
                    },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = nextcloudPassword,
                    onValueChange = {
                        nextcloudPassword = it
                        preferencesManager.setNextcloudPassword(it)
                    },
                    label = { Text("App Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isTestingNextcloud = true
                                val client = NextcloudApiClient()
                                val result = client.testConnection(nextcloudUrl, nextcloudUsername, nextcloudPassword)
                                result.onSuccess {
                                    Toast.makeText(context, "Connection successful!", Toast.LENGTH_SHORT).show()
                                }.onFailure { e ->
                                    Toast.makeText(context, "Connection failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                                isTestingNextcloud = false
                            }
                        },
                        enabled = !isTestingNextcloud
                    ) {
                        Text("Test Connection")
                    }

                    Button(
                        onClick = { showNextcloudSyncDialog = true }
                    ) {
                        Text("Sync Categories Now")
                    }
                }
            }
        }
    }
}
