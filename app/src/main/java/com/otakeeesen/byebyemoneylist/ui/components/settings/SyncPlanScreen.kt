package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatchCandidate

/**
 * UI labels for the shared sync-plan editor. Wrappers supply concrete localized strings.
 */
data class SyncPlanScreenStrings(
    val groupTitle: String,
    val emptyPlanPrompt: String,
    val loadingText: String,
    val llmMatchingText: String,
    val errorTemplate: String,
    val matchedEmptyText: String,
    val uploadEmptyText: String,
    val downloadEmptyText: String,
    val selectAllText: String,
    val deselectAllText: String,
    val matchActionText: String,
    val matchPickerTitle: String,
    val noMatchCandidatesText: String,
    val unlinkContentDescription: String,
    val goBackContentDescription: String,
    val collapseSectionText: String,
    val expandSectionText: String,
    val searchPlaceholder: String,
    val confirmText: String,
    val stateInSyncText: String,
    val stateLocalChangedText: String,
    val stateServerChangedText: String,
    val stateConflictText: String,
    val conflictUseLocalText: String,
    val conflictUseServerText: String,
    val conflictPickHint: String,
    val matchedHeader: @Composable (total: Int, updates: Int) -> String,
    val conflictsHeader: @Composable (Int) -> String,
    val uploadHeader: @Composable (Int, Int) -> String,
    val downloadHeader: @Composable (Int, Int) -> String
)

/**
 * Shared per-group sync plan editor.
 *
 * [upload] / [download] are the unmatched pools. Every unmatched row can open a "Match…"
 * picker listing unmatched items from the opposite side (Option A re-match). Every candidate
 * is also selectable for upload/download — including items unlinked after a previous sync
 * (re-syncing such an item creates a new entry with a new id on the destination side).
 *
 * [matched] pairs carry a content-state badge; pairs with pending changes are toggleable
 * (checkbox) so the user can include or exclude each update from the confirmed sync.
 *
 * [conflicts] are pairs changed on both sides. They are never auto-applied: the user picks
 * "Use local" or "Use server" per row; unresolved conflicts are skipped on confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <Local, Server> SyncPlanScreen(
    strings: SyncPlanScreenStrings,
    localLabel: (Local) -> String,
    serverLabel: (Server) -> String,
    planLoaded: Boolean,
    isLoading: Boolean,
    llmMatching: Boolean,
    isSyncing: Boolean,
    errorMessage: String?,
    matched: List<SyncMatchCandidate<Local, Server>>,
    conflicts: List<SyncConflict<Local, Server>>,
    upload: List<SyncCandidate<Local>>,
    download: List<SyncCandidate<Server>>,
    onBack: () -> Unit,
    onToggleUpload: (Local) -> Unit,
    onSelectAllUpload: (Boolean) -> Unit,
    onToggleDownload: (Server) -> Unit,
    onSelectAllDownload: (Boolean) -> Unit,
    onUnlinkMatch: (SyncMatch<Local, Server>) -> Unit,
    onToggleUpdate: (SyncMatch<Local, Server>) -> Unit,
    onResolveConflict: (SyncMatch<Local, Server>, SyncContentState) -> Unit,
    onCreateMatch: (Local, Server) -> Unit,
    modifier: Modifier = Modifier
) {
    var matchedExpanded by remember { mutableStateOf(true) }
    var conflictsExpanded by remember { mutableStateOf(true) }
    var uploadExpanded by remember { mutableStateOf(true) }
    var downloadExpanded by remember { mutableStateOf(true) }

    // Re-match picker anchors (Option A). Exactly one is non-null when the picker is open.
    var anchorLocal by remember { mutableStateOf<Local?>(null) }
    var anchorServer by remember { mutableStateOf<Server?>(null) }

    val selectedUploadCount = upload.count { it.selected }
    val selectedDownloadCount = download.count { it.selected }
    val selectedUpdatesCount = matched.count { it.selected && it.isUpdate }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(strings.groupTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.goBackContentDescription
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
            if (isLoading) {
                item {
                    CenteredProgress(strings.loadingText)
                }
            } else if (llmMatching) {
                item {
                    CenteredProgress(strings.llmMatchingText)
                }
            } else if (!planLoaded) {
                item {
                    Text(
                        text = strings.emptyPlanPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        textAlign = TextAlign.Center
                    )
                }
                if (errorMessage != null) {
                    item { ErrorText(errorMessage, strings.errorTemplate) }
                }
            } else {
                item {
                    SectionHeader(
                        title = strings.matchedHeader(matched.size, selectedUpdatesCount),
                        expanded = matchedExpanded,
                        onClick = { matchedExpanded = !matchedExpanded },
                        collapseSectionText = strings.collapseSectionText,
                        expandSectionText = strings.expandSectionText
                    )
                }
                item {
                    AnimatedVisibility(visible = matchedExpanded) {
                        Column {
                            if (matched.isEmpty()) {
                                EmptyText(strings.matchedEmptyText)
                            } else {
                                matched.forEach { candidate ->
                                    MatchedRow(
                                        candidate = candidate,
                                        label = "${localLabel(candidate.match.local)} ↔ ${serverLabel(candidate.match.server)}",
                                        strings = strings,
                                        onToggleUpdate = { onToggleUpdate(candidate.match) },
                                        onUnlink = { onUnlinkMatch(candidate.match) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (conflicts.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = strings.conflictsHeader(conflicts.size),
                            expanded = conflictsExpanded,
                            onClick = { conflictsExpanded = !conflictsExpanded },
                            collapseSectionText = strings.collapseSectionText,
                            expandSectionText = strings.expandSectionText
                        )
                    }
                    item {
                        AnimatedVisibility(visible = conflictsExpanded) {
                            Column {
                                conflicts.forEach { conflict ->
                                    ConflictRow(
                                        conflict = conflict,
                                        label = "${localLabel(conflict.match.local)} ↔ ${serverLabel(conflict.match.server)}",
                                        strings = strings,
                                        onResolve = { side -> onResolveConflict(conflict.match, side) },
                                        onUnlink = { onUnlinkMatch(conflict.match) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = strings.uploadHeader(selectedUploadCount, upload.size),
                        expanded = uploadExpanded,
                        onClick = { uploadExpanded = !uploadExpanded },
                        collapseSectionText = strings.collapseSectionText,
                        expandSectionText = strings.expandSectionText
                    )
                }
                item {
                    AnimatedVisibility(visible = uploadExpanded) {
                        Column {
                            if (upload.isEmpty()) {
                                EmptyText(strings.uploadEmptyText)
                            } else {
                                SelectAllRow(
                                    allSelected = selectedUploadCount == upload.size,
                                    strings = strings,
                                    onToggleAll = {
                                        onSelectAllUpload(selectedUploadCount != upload.size)
                                    }
                                )
                                upload.forEach { row ->
                                    SyncCandidateRow(
                                        label = localLabel(row.item),
                                        candidate = row,
                                        matchActionText = strings.matchActionText,
                                        onToggle = { onToggleUpload(row.item) },
                                        onMatch = { anchorLocal = row.item }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = strings.downloadHeader(selectedDownloadCount, download.size),
                        expanded = downloadExpanded,
                        onClick = { downloadExpanded = !downloadExpanded },
                        collapseSectionText = strings.collapseSectionText,
                        expandSectionText = strings.expandSectionText
                    )
                }
                item {
                    AnimatedVisibility(visible = downloadExpanded) {
                        Column {
                            if (download.isEmpty()) {
                                EmptyText(strings.downloadEmptyText)
                            } else {
                                SelectAllRow(
                                    allSelected = selectedDownloadCount == download.size,
                                    strings = strings,
                                    onToggleAll = {
                                        onSelectAllDownload(selectedDownloadCount != download.size)
                                    }
                                )
                                download.forEach { row ->
                                    SyncCandidateRow(
                                        label = serverLabel(row.item),
                                        candidate = row,
                                        matchActionText = strings.matchActionText,
                                        onToggle = { onToggleDownload(row.item) },
                                        onMatch = { anchorServer = row.item }
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    item { ErrorText(errorMessage, strings.errorTemplate) }
                }
            }
        }
    }

    val openLocal = anchorLocal
    if (openLocal != null) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { anchorLocal = null; query = "" },
            title = { Text(strings.matchPickerTitle) },
            text = {
                MatchCandidatePicker(
                    candidates = download,
                    label = { serverLabel(it.item) },
                    searchPlaceholder = strings.searchPlaceholder,
                    query = query,
                    onQueryChange = { query = it },
                    noCandidatesText = strings.noMatchCandidatesText,
                    onPick = { candidate ->
                        onCreateMatch(openLocal, candidate.item)
                        anchorLocal = null
                        query = ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { anchorLocal = null; query = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val openServer = anchorServer
    if (openServer != null) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { anchorServer = null; query = "" },
            title = { Text(strings.matchPickerTitle) },
            text = {
                MatchCandidatePicker(
                    candidates = upload,
                    label = { localLabel(it.item) },
                    searchPlaceholder = strings.searchPlaceholder,
                    query = query,
                    onQueryChange = { query = it },
                    noCandidatesText = strings.noMatchCandidatesText,
                    onPick = { candidate ->
                        onCreateMatch(candidate.item, openServer)
                        anchorServer = null
                        query = ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { anchorServer = null; query = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CenteredProgress(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text)
    }
}

@Composable
private fun <T> SyncCandidateRow(
    label: String,
    candidate: SyncCandidate<T>,
    matchActionText: String,
    onToggle: () -> Unit,
    onMatch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = candidate.selected,
            onCheckedChange = { onToggle() }
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onMatch) {
            Text(matchActionText)
        }
    }
}

@Composable
private fun <Local, Server> MatchedRow(
    candidate: SyncMatchCandidate<Local, Server>,
    label: String,
    strings: SyncPlanScreenStrings,
    onToggleUpdate: () -> Unit,
    onUnlink: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (candidate.isUpdate) {
            Checkbox(
                checked = candidate.selected,
                onCheckedChange = { onToggleUpdate() }
            )
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                candidate.match.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StateBadge(state = candidate.match.contentState, strings = strings)
        IconButton(onClick = onUnlink) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = strings.unlinkContentDescription
            )
        }
    }
}

@Composable
private fun StateBadge(state: SyncContentState, strings: SyncPlanScreenStrings) {
    val (text, container, content) = when (state) {
        SyncContentState.IN_SYNC -> Triple(
            strings.stateInSyncText,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SyncContentState.LOCAL_CHANGED -> Triple(
            strings.stateLocalChangedText,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SyncContentState.SERVER_CHANGED -> Triple(
            strings.stateServerChangedText,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        SyncContentState.CONFLICT -> Triple(
            strings.stateConflictText,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun <Local, Server> ConflictRow(
    conflict: SyncConflict<Local, Server>,
    label: String,
    strings: SyncPlanScreenStrings,
    onResolve: (SyncContentState) -> Unit,
    onUnlink: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    conflict.match.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onUnlink) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = strings.unlinkContentDescription
                )
            }
        }
        Text(
            text = strings.conflictPickHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConflictSideButton(
                text = strings.conflictUseLocalText,
                selected = conflict.resolvedTo == SyncContentState.LOCAL_CHANGED,
                onClick = { onResolve(SyncContentState.LOCAL_CHANGED) },
                modifier = Modifier.weight(1f)
            )
            ConflictSideButton(
                text = strings.conflictUseServerText,
                selected = conflict.resolvedTo == SyncContentState.SERVER_CHANGED,
                onClick = { onResolve(SyncContentState.SERVER_CHANGED) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConflictSideButton(
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
private fun <T> MatchCandidatePicker(
    candidates: List<SyncCandidate<T>>,
    label: (SyncCandidate<T>) -> String,
    searchPlaceholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    noCandidatesText: String,
    onPick: (SyncCandidate<T>) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val filtered = candidates.filter { label(it).contains(query.trim(), ignoreCase = true) }
        if (filtered.isEmpty()) {
            Text(
                noCandidatesText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filtered.forEach { candidate ->
                ListItem(
                    headlineContent = { Text(label(candidate)) },
                    modifier = Modifier.clickable { onPick(candidate) }
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    collapseSectionText: String,
    expandSectionText: String
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
            contentDescription = if (expanded) collapseSectionText else expandSectionText,
            tint = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider()
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ErrorText(message: String, errorTemplate: String) {
    Text(
        text = errorTemplate.format(message),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SelectAllRow(
    allSelected: Boolean,
    strings: SyncPlanScreenStrings,
    onToggleAll: () -> Unit
) {
    TextButton(onClick = onToggleAll) {
        Text(if (allSelected) strings.deselectAllText else strings.selectAllText)
    }
}
