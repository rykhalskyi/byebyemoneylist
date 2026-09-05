package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudApiClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextcloudSyncSettingsScreen(
    viewModel: NextcloudSyncViewModel,
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenStores: () -> Unit,
    onOpenProducts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var nextcloudUrl by remember { mutableStateOf(preferencesManager.getNextcloudUrl()) }
    var nextcloudUsername by remember { mutableStateOf(preferencesManager.getNextcloudUsername()) }
    var nextcloudPassword by remember { mutableStateOf(preferencesManager.getNextcloudPassword()) }
    var isTestingNextcloud by remember { mutableStateOf(false) }

    val syncErrorText = stringResource(R.string.nextcloud_sync_error_generic)
    val syncSuccessText = stringResource(R.string.nextcloud_sync_success_generic)
    val testOkText = stringResource(R.string.nextcloud_test_connection_success)
    val testFailTemplate = stringResource(R.string.nextcloud_test_connection_failed)
    val categoriesLabel = stringResource(R.string.categories)
    val storesLabel = stringResource(R.string.stores)
    val productsLabel = stringResource(R.string.products)
    val useLlmText = stringResource(R.string.nextcloud_sync_use_llm)
    val useLlmDesc = stringResource(R.string.nextcloud_sync_use_llm_desc)
    val useLlmUnavailable = stringResource(R.string.nextcloud_sync_use_llm_unavailable)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, syncErrorText.format(it), Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
    }
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            Toast.makeText(context, syncSuccessText, Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    Toast.makeText(context, testOkText, Toast.LENGTH_SHORT).show()
                                }.onFailure { e ->
                                    Toast.makeText(
                                        context,
                                        testFailTemplate.format(e.localizedMessage ?: "Unknown"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                isTestingNextcloud = false
                            }
                        },
                        enabled = !isTestingNextcloud && !uiState.isGenerating && !uiState.isExecuting
                    ) {
                        Text("Test Connection")
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !isTestingNextcloud && !uiState.isGenerating && !uiState.isExecuting
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.nextcloud_sync_now))
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
            }

            // Global "Use LLM" setting
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.useLlm,
                        onCheckedChange = { viewModel.setUseLlm(it) },
                        enabled = uiState.llmAvailable && !uiState.isGenerating && !uiState.isExecuting
                    )
                    Column {
                        Text(useLlmText, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (uiState.llmAvailable) useLlmDesc else useLlmUnavailable,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            // Grouped sync parts
            item {
                Text(
                    text = stringResource(R.string.nextcloud_sync_groups_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val categoryCounts = uiState.categories.counts()
            item {
                SyncGroupRow(
                    label = categoriesLabel,
                    enabled = true,
                    isBusy = uiState.isGenerating,
                    countsText = stringResource(
                        R.string.nextcloud_sync_row_counts,
                        categoryCounts.matched,
                        categoryCounts.upload,
                        categoryCounts.download
                    ),
                    onClick = onOpenCategories
                )
            }
            val storeCounts = uiState.stores.counts()
            item {
                SyncGroupRow(
                    label = storesLabel,
                    enabled = true,
                    isBusy = uiState.isGenerating,
                    countsText = stringResource(
                        R.string.nextcloud_sync_row_counts,
                        storeCounts.matched,
                        storeCounts.upload,
                        storeCounts.download
                    ),
                    onClick = onOpenStores
                )
            }
            val productCounts = uiState.products.counts()
            item {
                SyncGroupRow(
                    label = productsLabel,
                    enabled = true,
                    isBusy = uiState.isGenerating,
                    countsText = stringResource(
                        R.string.nextcloud_sync_row_counts,
                        productCounts.matched,
                        productCounts.upload,
                        productCounts.download
                    ),
                    onClick = onOpenProducts
                )
            }

            // Shopping Lists is a mirror (linked by serverId, no match routine),
            // so its row is informational only — count + status, no sub-screen.
            val shoppingListsState = uiState.shoppingLists
            item {
                ShoppingListsGroupRow(
                    label = stringResource(R.string.nextcloud_sync_shopping_lists),
                    isBusy = uiState.isGenerating || uiState.isExecuting,
                    statusText = when {
                        shoppingListsState.error != null -> stringResource(
                            R.string.nextcloud_sync_shopping_lists_error,
                            shoppingListsState.error
                        )
                        !shoppingListsState.hasSynced -> stringResource(
                            R.string.nextcloud_sync_shopping_lists_not_synced
                        )
                        else -> stringResource(
                            R.string.nextcloud_sync_shopping_lists_counts,
                            shoppingListsState.listCount,
                            shoppingListsState.skipped
                        )
                    }
                )
            }

            item {
                Button(
                    onClick = { viewModel.confirmAndSync { } },
                    enabled = (uiState.categories.planGenerated || uiState.stores.planGenerated ||
                        uiState.products.planGenerated) &&
                        !uiState.isGenerating && !uiState.isExecuting,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)
                ) {
                    if (uiState.isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.nextcloud_sync_confirm_and_sync))
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncGroupRow(
    label: String,
    enabled: Boolean,
    isBusy: Boolean,
    countsText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = countsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * Read-only row for the Shopping Lists mirror group. Unlike the match-based
 * groups there is no editor sub-screen, so the row is not clickable and only
 * reports the last sync outcome (count + status).
 */
@Composable
private fun ShoppingListsGroupRow(
    label: String,
    isBusy: Boolean,
    statusText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
