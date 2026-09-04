package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        matchedHeader = { count -> stringResource(R.string.store_sync_matched, count) },
        uploadHeader = { selected, total -> stringResource(R.string.store_sync_upload, selected, total) },
        downloadHeader = { selected, total -> stringResource(R.string.store_sync_download, selected, total) }
    )

    SyncPlanScreen(
        strings = strings,
        localLabel = { local: StoreEntity -> local.name },
        serverLabel = { server: NextcloudStoreDto -> server.name },
        planLoaded = stores.planGenerated,
        isLoading = uiState.isGenerating,
        llmMatching = false,
        isSyncing = uiState.isExecuting,
        errorMessage = uiState.error,
        matched = stores.matched,
        upload = stores.upload,
        download = stores.download,
        onBack = onBack,
        onToggleUpload = viewModel::toggleUploadStore,
        onSelectAllUpload = viewModel::selectAllUploadStore,
        onToggleDownload = viewModel::toggleDownloadStore,
        onSelectAllDownload = viewModel::selectAllDownloadStore,
        onUnlinkMatch = viewModel::unlinkStoreMatch,
        onCreateMatch = viewModel::createStoreMatch,
        onConfirmAndSync = {
            viewModel.confirmAndSync { success ->
                if (success) onBack()
            }
        },
        modifier = modifier
    )
}
