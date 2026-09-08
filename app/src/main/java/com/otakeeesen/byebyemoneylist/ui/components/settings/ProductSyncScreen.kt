package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudProductDto

@Composable
fun ProductSyncScreen(
    viewModel: NextcloudSyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val products = uiState.products

    // Opening the screen before "Fetch latest" still produces a reviewable plan,
    // refreshed for just this group.
    LaunchedEffect(Unit) {
        if (!products.planGenerated && !uiState.anyBusy) {
            viewModel.generatePlan(SyncGroup.PRODUCTS)
        }
    }

    val strings = SyncPlanScreenStrings(
        groupTitle = stringResource(R.string.product_sync_title),
        emptyPlanPrompt = stringResource(R.string.nextcloud_sync_no_plan_prompt),
        loadingText = stringResource(R.string.product_sync_loading),
        llmMatchingText = stringResource(R.string.product_sync_loading),
        errorTemplate = stringResource(R.string.product_sync_error),
        matchedEmptyText = stringResource(R.string.product_sync_no_matches),
        uploadEmptyText = stringResource(R.string.product_sync_nothing_to_upload),
        downloadEmptyText = stringResource(R.string.product_sync_nothing_to_download),
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
                stringResource(R.string.product_sync_matched_updates, total, updatesText)
            } else {
                stringResource(R.string.product_sync_matched, total)
            }
        },
        conflictsHeader = { count -> stringResource(R.string.nextcloud_sync_conflicts_header, count) },
        uploadHeader = { selected, total -> stringResource(R.string.product_sync_upload, selected, total) },
        downloadHeader = { selected, total -> stringResource(R.string.product_sync_download, selected, total) }
    )

    SyncPlanScreen(
        strings = strings,
        localLabel = { local: ProductEntity ->
            if (local.barcode.isNotBlank()) "${local.name} (${local.barcode})" else local.name
        },
        serverLabel = { server: NextcloudProductDto ->
            if (!server.barcode.isNullOrBlank()) "${server.name} (${server.barcode})" else server.name
        },
        planLoaded = products.planGenerated,
        isLoading = !products.planGenerated && uiState.isBusy(SyncGroup.PRODUCTS),
        llmMatching = false,
        isSyncing = uiState.isExecuting(SyncGroup.PRODUCTS),
        errorMessage = uiState.error,
        matched = products.matched,
        conflicts = products.conflicts,
        upload = products.upload,
        download = products.download,
        onBack = onBack,
        onToggleUpload = viewModel::toggleUploadProduct,
        onSelectAllUpload = viewModel::selectAllUploadProduct,
        onToggleDownload = viewModel::toggleDownloadProduct,
        onSelectAllDownload = viewModel::selectAllDownloadProduct,
        onUnlinkMatch = viewModel::unlinkProductMatch,
        onToggleUpdate = viewModel::toggleUpdateProduct,
        onResolveConflict = viewModel::resolveProductConflict,
        onCreateMatch = viewModel::createProductMatch,
        onFetchPlan = { viewModel.generatePlan(SyncGroup.PRODUCTS) },
        fetchPlanLabel = stringResource(R.string.nextcloud_sync_now),
        fetchBusy = uiState.isGenerating(SyncGroup.PRODUCTS),
        onApplyPlan = { viewModel.applyGroup(SyncGroup.PRODUCTS) },
        modifier = modifier
    )
}
