package com.otakeeesen.byebyemoneylist.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var hideCheckedItems by remember { mutableStateOf(preferencesManager.getHideCheckedItems()) }

    var llmProvider by remember { mutableStateOf(preferencesManager.getLlmProvider()) }
    var geminiApiKey by remember { mutableStateOf(preferencesManager.getGeminiApiKey()) }
    var siliconFlowApiKey by remember { mutableStateOf(preferencesManager.getSiliconFlowApiKey()) }
    var siliconFlowModel by remember { mutableStateOf(preferencesManager.getSiliconFlowModel()) }

    var showProviderMenu by remember { mutableStateOf(false) }

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
                Box {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_llm_provider)) },
                        supportingContent = {
                            Text(
                                when (llmProvider) {
                                    "GEMINI" -> stringResource(R.string.provider_gemini)
                                    "SILICONFLOW" -> stringResource(R.string.provider_siliconflow)
                                    else -> stringResource(R.string.provider_none)
                                }
                            )
                        },
                        modifier = Modifier.clickable { showProviderMenu = true }
                    )
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_none)) },
                            onClick = {
                                llmProvider = "NONE"
                                preferencesManager.setLlmProvider("NONE")
                                showProviderMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_gemini)) },
                            onClick = {
                                llmProvider = "GEMINI"
                                preferencesManager.setLlmProvider("GEMINI")
                                showProviderMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_siliconflow)) },
                            onClick = {
                                llmProvider = "SILICONFLOW"
                                preferencesManager.setLlmProvider("SILICONFLOW")
                                showProviderMenu = false
                            }
                        )
                    }
                }
            }

            if (llmProvider == "GEMINI") {
                item {
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = {
                            geminiApiKey = it
                            preferencesManager.setGeminiApiKey(it)
                        },
                        label = { Text(stringResource(R.string.label_gemini_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }

            if (llmProvider == "SILICONFLOW") {
                item {
                    OutlinedTextField(
                        value = siliconFlowApiKey,
                        onValueChange = {
                            siliconFlowApiKey = it
                            preferencesManager.setSiliconFlowApiKey(it)
                        },
                        label = { Text(stringResource(R.string.label_siliconflow_key)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = siliconFlowModel,
                        onValueChange = {
                            siliconFlowModel = it
                            preferencesManager.setSiliconFlowModel(it)
                        },
                        label = { Text(stringResource(R.string.label_siliconflow_model)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
