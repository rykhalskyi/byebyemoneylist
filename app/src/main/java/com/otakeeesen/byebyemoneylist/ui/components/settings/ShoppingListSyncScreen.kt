package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState

/**
 * Per-list Shopping Lists sync editor. Unlike the match-based group screens there are
 * no unmatched pools and no re-match picker — every list is a serverId-linked pair with
 * a git-style content state. Conflicts offer an explicit "Use local" / "Use server"
 * choice; the resulting resolution is applied by "Apply changes" for this group (see
 * [NextcloudSyncViewModel.applyGroup]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListSyncScreen(
    viewModel: NextcloudSyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.shoppingListPlan

    val loadingText = stringResource(R.string.shopping_list_sync_loading)
    val noPlanPrompt = stringResource(R.string.shopping_list_sync_no_plan_prompt)
    val generateText = stringResource(R.string.nextcloud_sync_now)
    val errorTemplate = stringResource(R.string.nextcloud_sync_error_generic)
    val successText = stringResource(R.string.nextcloud_sync_success_generic)
    val cdGoBack = stringResource(R.string.cd_go_back)

    // Opening the screen before "Sync Now" still produces a reviewable plan.
    LaunchedEffect(Unit) {
        if (!editor.planGenerated && !uiState.anyBusy) {
            viewModel.generatePlan(SyncGroup.SHOPPING_LISTS)
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, errorTemplate.format(it), Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
    }
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            Toast.makeText(context, successText, Toast.LENGTH_LONG).show()
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nextcloud_sync_shopping_list_sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = cdGoBack
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)

        when {
            !editor.planGenerated && uiState.isBusy(SyncGroup.SHOPPING_LISTS) -> {
                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(64.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(loadingText)
                }
            }

            !editor.planGenerated -> {
                Column(
                    modifier = contentModifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = noPlanPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 48.dp)
                    )
                    Button(
                        onClick = { viewModel.generatePlan(SyncGroup.SHOPPING_LISTS) },
                        enabled = !uiState.anyBusy
                    ) {
                        Text(generateText)
                    }
                }
            }

            else -> {
                val plan = editor.plan
                if (plan == null) {
                    Column(
                        modifier = contentModifier,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = noPlanPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 48.dp)
                        )
                    }
                } else {
                    ShoppingListPlanContent(
                        planState = editor,
                        modifier = contentModifier,
                        isApplying = uiState.isBusy(SyncGroup.SHOPPING_LISTS),
                        onApply = { viewModel.applyGroup(SyncGroup.SHOPPING_LISTS) },
                        onResolveConflict = viewModel::resolveShoppingListConflict
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingListPlanContent(
    planState: ShoppingListSyncPlanUiState,
    modifier: Modifier = Modifier,
    isApplying: Boolean,
    onApply: () -> Unit,
    onResolveConflict: (Long, SyncContentState) -> Unit
) {
    val plan = planState.plan ?: return
    val linked = plan.linked
    val nothingText = stringResource(R.string.shopping_list_sync_no_linked)
    val newOnServer = stringResource(R.string.shopping_list_sync_new_on_server, plan.pushToCreate.size)
    val newOnDevice = stringResource(R.string.shopping_list_sync_new_on_device, plan.pullToCreate.size)
    val willUploadText = stringResource(R.string.shopping_list_sync_will_upload)
    val willDownloadText = stringResource(R.string.shopping_list_sync_will_download)
    val applyText = stringResource(R.string.nextcloud_sync_confirm_and_sync)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (plan.pushToCreate.isNotEmpty()) {
            item {
                SyncNote(text = newOnServer)
            }
            items(plan.pushToCreate.size) { index ->
                val list = plan.pushToCreate[index]
                NewShoppingListRow(
                    listName = list.name,
                    actionLabel = willUploadText,
                    actionContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    actionContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (plan.pullToCreate.isNotEmpty()) {
            item {
                SyncNote(text = newOnDevice)
            }
            items(plan.pullToCreate.size) { index ->
                val list = plan.pullToCreate[index]
                NewShoppingListRow(
                    listName = list.name,
                    actionLabel = willDownloadText,
                    actionContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    actionContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        if (linked.isEmpty() && plan.pushToCreate.isEmpty() && plan.pullToCreate.isEmpty()) {
            item {
                Text(
                    text = nothingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(linked.size) { index ->
                val link = linked[index]
                LinkedShoppingListRow(
                    listName = link.local.name,
                    state = link.state,
                    resolution = planState.resolutions[link.local.id],
                    onResolveLocal = { onResolveConflict(link.local.id, SyncContentState.LOCAL_CHANGED) },
                    onResolveServer = { onResolveConflict(link.local.id, SyncContentState.SERVER_CHANGED) }
                )
            }
        }
        item {
            Button(
                onClick = onApply,
                enabled = !isApplying,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(applyText)
                }
            }
        }
    }
}

@Composable
private fun SyncNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun LinkedShoppingListRow(
    listName: String,
    state: SyncContentState,
    resolution: SyncContentState?,
    onResolveLocal: () -> Unit,
    onResolveServer: () -> Unit
) {
    val pickHint = stringResource(R.string.nextcloud_sync_conflict_pick_hint)
    val useLocalText = stringResource(R.string.nextcloud_sync_conflict_use_local)
    val useServerText = stringResource(R.string.nextcloud_sync_conflict_use_server)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                SyncStateChip(state = state)
            }
            if (state == SyncContentState.CONFLICT) {
                Text(
                    text = pickHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncSideButton(
                        text = useLocalText,
                        selected = resolution == SyncContentState.LOCAL_CHANGED,
                        onClick = onResolveLocal,
                        modifier = Modifier.weight(1f)
                    )
                    SyncSideButton(
                        text = useServerText,
                        selected = resolution == SyncContentState.SERVER_CHANGED,
                        onClick = onResolveServer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewShoppingListRow(
    listName: String,
    actionLabel: String,
    actionContainerColor: androidx.compose.ui.graphics.Color,
    actionContentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = listName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = actionContainerColor,
                contentColor = actionContentColor
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = actionContentColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SyncSideButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
private fun SyncStateChip(state: SyncContentState) {
    val inSyncText = stringResource(R.string.nextcloud_sync_state_in_sync)
    val localChangedText = stringResource(R.string.nextcloud_sync_state_local_changed)
    val serverChangedText = stringResource(R.string.nextcloud_sync_state_server_changed)
    val conflictText = stringResource(R.string.nextcloud_sync_state_conflict)
    val (text, container, content) = when (state) {
        SyncContentState.IN_SYNC -> Triple(
            inSyncText,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncContentState.LOCAL_CHANGED -> Triple(
            localChangedText,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SyncContentState.SERVER_CHANGED -> Triple(
            serverChangedText,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncContentState.CONFLICT -> Triple(
            conflictText,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
