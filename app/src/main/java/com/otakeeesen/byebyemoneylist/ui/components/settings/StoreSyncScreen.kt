package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudStoreDto

@Composable
fun StoreSyncScreen(
    viewModel: NextcloudSyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stores = uiState.stores

    // Opening the screen before "Fetch latest" still produces a reviewable plan,
    // refreshed for just this group.
    LaunchedEffect(Unit) {
        if (!stores.planGenerated && !uiState.anyBusy) {
            viewModel.generatePlan(SyncGroup.STORES)
        }
    }

    val strings = SyncPlanScreenStrings(
        groupTitle = stringResource(R.string.store_sync_title),
        emptyPlanPrompt = stringResource(R.string.nextcloud_sync_no_plan_prompt),
        loadingText = stringResource(R.string.store_sync_loading),
        llmMatchingText = stringResource(R.string.store_sync_loading),
        errorTemplate = stringResource(R.string.store_sync_error),
        matchedEmptyText = stringResource(R.string.store_sync_no_matches),
        uploadEmptyText = stringResource(R.string.store_sync_nothing_to_upload),
        downloadEmptyText = stringResource(R.string.store_sync_nothing_to_download),
        selectAllText = stringResource(R.string.nextcloud_sync_select_all),
        deselectAllText = stringResource(R.string.nextcloud_sync_deselect_all),
        matchActionText = stringResource(R.string.nextcloud_sync_action_match),
        matchPickerTitle = stringResource(R.string.nextcloud_sync_match_picker_title),
        noMatchCandidatesText = stringResource(R.string.nextcloud_sync_no_match_candidates),
        unlinkContentDescription = stringResource(R.string.cd_unlink_match),
        goBackContentDescription = stringResource(R.string.cd_go_back),
        collapseSectionText = stringResource(R.string.cd_collapse_section),
        expandSectionText = stringResource(R.string.cd_expand_section),
        searchPlaceholder = stringResource(R.string.search),
        confirmText = stringResource(R.string.nextcloud_sync_confirm_and_sync),
        stateInSyncText = stringResource(R.string.nextcloud_sync_state_in_sync),
        stateLocalChangedText = stringResource(R.string.nextcloud_sync_state_local_changed),
        stateServerChangedText = stringResource(R.string.nextcloud_sync_state_server_changed),
        stateConflictText = stringResource(R.string.nextcloud_sync_state_conflict),
        conflictUseLocalText = stringResource(R.string.nextcloud_sync_conflict_use_local),
        conflictUseServerText = stringResource(R.string.nextcloud_sync_conflict_use_server),
        conflictPickHint = stringResource(R.string.nextcloud_sync_conflict_pick_hint),
        matchedHeader = { total, updates ->
            if (updates > 0) {
                val updatesText = pluralStringResource(R.plurals.nextcloud_sync_row_updates, updates, updates)
                stringResource(R.string.store_sync_matched_updates, total, updatesText)
            } else {
                stringResource(R.string.store_sync_matched, total)
            }
        },
        conflictsHeader = { count -> stringResource(R.string.nextcloud_sync_conflicts_header, count) },
        uploadHeader = { selected, total -> stringResource(R.string.store_sync_upload, selected, total) },
        downloadHeader = { selected, total -> stringResource(R.string.store_sync_download, selected, total) }
    )

    SyncPlanScreen(
        strings = strings,
        localLabel = { local: StoreEntity -> local.name },
        serverLabel = { server: NextcloudStoreDto -> server.name },
        planLoaded = stores.planGenerated,
        isLoading = !stores.planGenerated && uiState.isBusy(SyncGroup.STORES),
        llmMatching = false,
        isSyncing = uiState.isExecuting(SyncGroup.STORES),
        errorMessage = uiState.error,
        matched = stores.matched,
        conflicts = stores.conflicts,
        upload = stores.upload,
        download = stores.download,
        onBack = onBack,
        onToggleUpload = viewModel::toggleUploadStore,
        onSelectAllUpload = viewModel::selectAllUploadStore,
        onToggleDownload = viewModel::toggleDownloadStore,
        onSelectAllDownload = viewModel::selectAllDownloadStore,
        onUnlinkMatch = viewModel::unlinkStoreMatch,
        onToggleUpdate = viewModel::toggleUpdateStore,
        onResolveConflict = viewModel::resolveStoreConflict,
        onCreateMatch = viewModel::createStoreMatch,
        onFetchPlan = { viewModel.generatePlan(SyncGroup.STORES) },
        fetchPlanLabel = stringResource(R.string.nextcloud_sync_now),
        fetchBusy = uiState.isGenerating(SyncGroup.STORES),
        onApplyPlan = { viewModel.applyGroup(SyncGroup.STORES) },
        modifier = modifier
    )
}
