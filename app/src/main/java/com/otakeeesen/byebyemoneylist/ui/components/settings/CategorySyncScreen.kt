package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.agent.AgentManager
import com.otakeeesen.byebyemoneylist.data.agent.AgentQueryExecutor
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncPhase
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.MultiLanguageCategoryMatcher
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudCategoryDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fetchErrorText = stringResource(R.string.category_sync_fetch_error)
    val syncSuccessText = stringResource(R.string.category_sync_success)
    val syncErrorTemplate = stringResource(R.string.category_sync_error)

    val db = remember { AppDatabase.getDatabase(context) }
    val preferencesManager = remember { PreferencesManager(context) }
    val syncRepository = remember { CategorySyncRepository(db.categoryDao(), preferencesManager) }

    val app = context.applicationContext as ByeByeMoneyApplication
    val agentManager = remember {
        val executor = AgentQueryExecutor(
            app.shoppingListRepository,
            app.categoryRepository,
            app.productRepository,
            app.priceRepository,
            app.storeRepository,
            app.preferencesManager
        )
        AgentManager(app.preferencesManager, executor)
    }
    val llmAvailable = remember { app.preferencesManager.getActiveProfileId() != null }

    var isLoading by remember { mutableStateOf(false) }
    var isLlmMatching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncPlan by remember { mutableStateOf<CategorySyncPlan?>(null) }
    var useLlm by remember { mutableStateOf(llmAvailable) }

    val pushList = remember { mutableStateListOf<CategoryEntity>() }
    val pullList = remember { mutableStateListOf<NextcloudCategoryDto>() }
    val selectedPush = remember { mutableStateListOf<CategoryEntity>() }
    val selectedPull = remember { mutableStateListOf<NextcloudCategoryDto>() }

    var matchedExpanded by remember { mutableStateOf(true) }
    var uploadExpanded by remember { mutableStateOf(true) }
    var downloadExpanded by remember { mutableStateOf(true) }
    var isExecuting by remember { mutableStateOf(false) }

    val onGeneratePlan: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            isLlmMatching = false
            errorMessage = null
            val result = syncRepository.generateSyncPlan(
                useLlm = useLlm && llmAvailable,
                llmCall = if (llmAvailable) {
                    { prompt -> agentManager.generateText(MultiLanguageCategoryMatcher.LLM_SYSTEM_INSTRUCTION, prompt) }
                } else {
                    null
                },
                onPhase = { phase ->
                    if (phase == CategorySyncPhase.LLM_MATCHING) isLlmMatching = true
                }
            )
            result.onSuccess { plan ->
                syncPlan = plan
                pushList.clear()
                pushList.addAll(plan.toPushToServer)
                selectedPush.clear()
                selectedPush.addAll(plan.toPushToServer)
                pullList.clear()
                pullList.addAll(plan.toPullToClient)
                selectedPull.clear()
                selectedPull.addAll(plan.toPullToClient)
            }.onFailure { e ->
                errorMessage = e.localizedMessage ?: fetchErrorText
            }
            isLlmMatching = false
            isLoading = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_sync_title)) },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useLlm,
                        onCheckedChange = { useLlm = it },
                        enabled = llmAvailable && !isLoading && !isLlmMatching
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.category_sync_use_llm),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(
                                if (llmAvailable) R.string.category_sync_use_llm_desc else R.string.category_sync_use_llm_unavailable
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            if (isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.category_sync_loading))
                    }
                }
            } else if (isLlmMatching) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.category_sync_llm_matching))
                    }
                }
            } else if (syncPlan != null) {
                val plan = syncPlan!!

                item {
                    SectionHeader(
                        title = stringResource(R.string.category_sync_matched, plan.matched.size),
                        expanded = matchedExpanded,
                        onClick = { matchedExpanded = !matchedExpanded }
                    )
                }
                item {
                    AnimatedVisibility(visible = matchedExpanded) {
                        Column {
                            if (plan.matched.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.category_sync_no_matches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                plan.matched.forEach { match ->
                                    ListItem(
                                        headlineContent = { Text("${match.localCategory.name} ↔ ${match.serverCategory.name}") },
                                        supportingContent = { Text(match.matchReason) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.category_sync_upload, selectedPush.size, pushList.size),
                        expanded = uploadExpanded,
                        onClick = { uploadExpanded = !uploadExpanded }
                    )
                }
                item {
                    AnimatedVisibility(visible = uploadExpanded) {
                        Column {
                            if (pushList.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.category_sync_nothing_to_upload),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                pushList.forEach { item ->
                                    val checked = item in selectedPush
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) {
                                                    if (item !in selectedPush) selectedPush.add(item)
                                                } else {
                                                    selectedPush.remove(item)
                                                }
                                            }
                                        )
                                        Text(item.name)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.category_sync_download, selectedPull.size, pullList.size),
                        expanded = downloadExpanded,
                        onClick = { downloadExpanded = !downloadExpanded }
                    )
                }
                item {
                    AnimatedVisibility(visible = downloadExpanded) {
                        Column {
                            if (pullList.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.category_sync_nothing_to_download),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            } else {
                                pullList.forEach { item ->
                                    val checked = item in selectedPull
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) {
                                                    if (item !in selectedPull) selectedPull.add(item)
                                                } else {
                                                    selectedPull.remove(item)
                                                }
                                            }
                                        )
                                        Text(item.name)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isExecuting = true
                                val linkedPairs = plan.matched.map { it.localCategory to it.serverCategory }
                                val res = syncRepository.executeSyncPlan(plan, selectedPush, selectedPull, linkedPairs)
                                res.onSuccess {
                                    Toast.makeText(context, syncSuccessText, Toast.LENGTH_SHORT).show()
                                    onBack()
                                }.onFailure { e ->
                                    Toast.makeText(
                                        context,
                                        syncErrorTemplate.format(e.localizedMessage ?: "Unknown"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    isExecuting = false
                                }
                            }
                        },
                        enabled = !isExecuting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.category_sync_confirm))
                        }
                    }
                }
            } else {
                if (errorMessage != null) {
                    item {
                        Text(
                            text = errorMessage ?: stringResource(R.string.category_sync_fetch_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                item {
                    Button(
                        onClick = onGeneratePlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 16.dp)
                    ) {
                        Text(stringResource(R.string.category_sync_generate))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) {
                stringResource(R.string.cd_collapse_section)
            } else {
                stringResource(R.string.cd_expand_section)
            },
            tint = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider()
}
