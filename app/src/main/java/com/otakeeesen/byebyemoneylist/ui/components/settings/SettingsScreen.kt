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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLlmSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var hideCheckedItems by remember { mutableStateOf(preferencesManager.getHideCheckedItems()) }

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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hide_checked_items)) },
                    trailingContent = {
                        Switch(
                            checked = hideCheckedItems,
                            onCheckedChange = {
                                hideCheckedItems = it
                                preferencesManager.setHideCheckedItems(it)
                            }
                        )
                    }
                )
                HorizontalDivider()
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
