package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudCategoryDto
import kotlinx.coroutines.launch

@Composable
fun CategorySyncDialog(
    syncRepository: CategorySyncRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncPlan by remember { mutableStateOf<CategorySyncPlan?>(null) }

    val pushList = remember { mutableStateListOf<CategoryEntity>() }
    val pullList = remember { mutableStateListOf<NextcloudCategoryDto>() }
    var isExecuting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = syncRepository.generateSyncPlan()
        result.onSuccess { plan ->
            syncPlan = plan
            pushList.clear()
            pushList.addAll(plan.toPushToServer)
            pullList.clear()
            pullList.addAll(plan.toPullToClient)
            isLoading = false
        }.onFailure { e ->
            errorMessage = e.localizedMessage ?: "Failed to fetch Nextcloud categories"
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category Synchronization") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 400.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching categories & matching...")
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "Error",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (syncPlan != null) {
                    val plan = syncPlan!!
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Matched Categories (${plan.matched.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(plan.matched) { match ->
                            ListItem(
                                headlineContent = { Text("${match.localCategory.name} ↔ ${match.serverCategory.name}") },
                                supportingContent = { Text(match.matchReason) }
                            )
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                text = "Upload to Nextcloud (${pushList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(pushList) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        if (!checked) pushList.remove(item)
                                    }
                                )
                                Text(item.name)
                            }
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                text = "Download to Client (${pullList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(pullList) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        if (!checked) pullList.remove(item)
                                    }
                                )
                                Text(item.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (syncPlan != null && !isLoading && !isExecuting) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isExecuting = true
                            val plan = syncPlan!!
                            val linkedPairs = plan.matched.map { it.localCategory to it.serverCategory }
                            val res = syncRepository.executeSyncPlan(plan, pushList, pullList, linkedPairs)
                            res.onSuccess {
                                Toast.makeText(context, "Categories synchronized successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.onFailure { e ->
                                Toast.makeText(context, "Sync error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                isExecuting = false
                            }
                        }
                    }
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirm & Sync")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
