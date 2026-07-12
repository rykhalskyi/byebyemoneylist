package com.otakeeesen.byebyemoneylist.ui.components.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.material.icons.filled.Share
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ExportViewModel
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.sync.SyncFolderRepository
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.Cloud

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLlmSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    val exportViewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
    val defaultFilename = remember {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        "byebyemoney_export_$dateStr.csv"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportViewModel.exportData(
                context = context,
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                },
                onError = { e ->
                    Toast.makeText(context, context.getString(R.string.export_error, e.localizedMessage ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val versionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    val privacyPolicyUrl = stringResource(R.string.url_privacy_policy)
    val repoUrl = stringResource(R.string.url_repo)
    
    var actualPriceRule by remember { mutableStateOf(preferencesManager.getActualPriceRule()) }
    var showRuleDropdown by remember { mutableStateOf(false) }

    var currencySymbol by remember { mutableStateOf(preferencesManager.getCurrencySymbol() ?: "None") }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    val currencyOptions = listOf("None", "€", "₴", "$", "£", "zł")

    val application = context.applicationContext as ByeByeMoneyApplication
    val syncFolderRepo = application.syncFolderRepository
    var showRemoveFolderDialog by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(syncFolderRepo.prefs.getSyncDisplayName() ?: "") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            syncFolderRepo.persistFolderUri(uri)
            val displayName = syncFolderRepo.getFolderDisplayName()
            Toast.makeText(context, context.getString(R.string.folder_selected, displayName ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
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
                    text = stringResource(R.string.section_receipt_scanning),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.section_llm_profiles)) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToLlmSettings() }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    text = stringResource(R.string.section_display),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = showCurrencyDropdown,
                    onExpandedChange = { showCurrencyDropdown = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = currencySymbol,
                        onValueChange = {},
                        label = { Text("Currency Symbol") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyDropdown)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCurrencyDropdown,
                        onDismissRequest = { showCurrencyDropdown = false }
                    ) {
                        currencyOptions.forEach { symbol ->
                            DropdownMenuItem(
                                text = { Text(symbol) },
                                onClick = {
                                    currencySymbol = symbol
                                    preferencesManager.setCurrencySymbol(if (symbol == "None") "" else symbol)
                                    showCurrencyDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = showRuleDropdown,
                    onExpandedChange = { showRuleDropdown = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = when (actualPriceRule) {
                            "BIGGER_VALUE" -> stringResource(R.string.rule_bigger_value)
                            else -> stringResource(R.string.rule_purchase_price)
                        },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_actual_price_rule)) },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showRuleDropdown)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showRuleDropdown,
                        onDismissRequest = { showRuleDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rule_bigger_value)) },
                            onClick = {
                                actualPriceRule = "BIGGER_VALUE"
                                preferencesManager.setActualPriceRule("BIGGER_VALUE")
                                showRuleDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rule_purchase_price)) },
                            onClick = {
                                actualPriceRule = "PURCHASE_PRICE"
                                preferencesManager.setActualPriceRule("PURCHASE_PRICE")
                                showRuleDropdown = false
                            }
                        )
                    }
                }
                HorizontalDivider()
            }

            item {
                Text(
                    text = stringResource(R.string.section_data_management),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_data)) },
                    supportingContent = { Text(stringResource(R.string.export_data_desc)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.export_data)
                        )
                    },
                    modifier = Modifier.clickable {
                        exportLauncher.launch(defaultFilename)
                    }
                )
                HorizontalDivider()
            }


            item {
                Text(
                    text = stringResource(R.string.shared_lists_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val folderSet = syncFolderRepo.isFolderSet()
                if (folderSet) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.change_shared_folder)) },
                        supportingContent = {
                            Text(syncFolderRepo.getFolderDisplayName() ?: stringResource(R.string.no_folder_selected))
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.select_shared_folder)
                            )
                        },
                        modifier = Modifier.clickable {
                            folderPickerLauncher.launch(null)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.remove_shared_folder)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.FolderDelete,
                                contentDescription = stringResource(R.string.remove_shared_folder)
                            )
                        },
                        modifier = Modifier.clickable {
                            showRemoveFolderDialog = true
                        }
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.select_shared_folder)) },
                        supportingContent = { Text(stringResource(R.string.export_data_desc)) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = stringResource(R.string.select_shared_folder)
                            )
                        },
                        modifier = Modifier.clickable {
                            folderPickerLauncher.launch(null)
                        }
                    )
                }
                HorizontalDivider()
            }

            if (syncFolderRepo.isFolderSet()) {
                item {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            syncFolderRepo.prefs.setSyncDisplayName(it.ifBlank { null })
                        },
                        label = { Text(stringResource(R.string.display_name_label)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        singleLine = true
                    )
                }
            }

            if (showRemoveFolderDialog) {
                item {
                    AlertDialog(
                        onDismissRequest = { showRemoveFolderDialog = false },
                        title = { Text(stringResource(R.string.remove_shared_folder)) },
                        text = { Text(stringResource(R.string.remove_folder_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                syncFolderRepo.clearFolder()
                                Toast.makeText(context, context.getString(R.string.no_folder_selected), Toast.LENGTH_SHORT).show()
                                showRemoveFolderDialog = false
                            }) {
                                Text(stringResource(R.string.delete))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveFolderDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_version, versionName ?: "Unknown")) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                    }
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_privacy_policy)) },
                    supportingContent = { Text(privacyPolicyUrl) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
                        context.startActivity(intent)
                    }
                )
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.label_repo)) },
                    supportingContent = { Text(repoUrl) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}
