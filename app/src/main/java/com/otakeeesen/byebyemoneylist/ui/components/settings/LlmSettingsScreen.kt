package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.LlmProfile
import com.otakeeesen.byebyemoneylist.data.LlmProvider
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    
    var profiles by remember { mutableStateOf(preferencesManager.getLlmProfiles()) }
    var activeProfileId by remember { mutableStateOf(preferencesManager.getActiveProfileId()) }

    var showProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<LlmProfile?>(null) }

    if (showProfileDialog) {
        LlmProfileDialog(
            profile = editingProfile,
            onDismiss = {
                showProfileDialog = false
                editingProfile = null
            },
            onSave = { updatedProfile ->
                val newProfiles = if (editingProfile != null) {
                    profiles.map { if (it.id == updatedProfile.id) updatedProfile else it }
                } else {
                    profiles + updatedProfile
                }
                profiles = newProfiles
                preferencesManager.saveLlmProfiles(newProfiles)
                showProfileDialog = false
                editingProfile = null
            },
            onDelete = { id ->
                val newProfiles = profiles.filter { it.id != id }
                profiles = newProfiles
                preferencesManager.saveLlmProfiles(newProfiles)
                if (activeProfileId == id) {
                    activeProfileId = null
                    preferencesManager.setActiveProfileId(null)
                }
                showProfileDialog = false
                editingProfile = null
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.llm_settings)) },
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
                    text = stringResource(R.string.section_llm_profiles),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(profiles, key = { it.id }) { profile ->
                LlmProfileCard(
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    onSelect = {
                        val newId = if (activeProfileId == profile.id) null else profile.id
                        activeProfileId = newId
                        preferencesManager.setActiveProfileId(newId)
                    },
                    onEdit = {
                        editingProfile = profile
                        showProfileDialog = true
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        editingProfile = null
                        showProfileDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.label_add_profile))
                }
            }
        }
    }
}

@Composable
fun LlmProfileCard(
    profile: LlmProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (profile.provider) {
                        LlmProvider.GEMINI -> stringResource(R.string.provider_gemini)
                        LlmProvider.SILICONFLOW -> stringResource(R.string.provider_siliconflow)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            RadioButton(
                selected = isActive,
                onClick = onSelect
            )
            
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.label_edit_profile))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmProfileDialog(
    profile: LlmProfile?,
    onDismiss: () -> Unit,
    onSave: (LlmProfile) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var provider by remember { mutableStateOf(profile?.provider ?: LlmProvider.GEMINI) }
    var apiKey by remember { mutableStateOf(profile?.apiKey ?: "") }
    var model by remember { mutableStateOf(profile?.model ?: "") }
    var connectTimeout by remember { mutableStateOf(profile?.connectTimeoutSeconds?.toString() ?: "30") }
    var readTimeout by remember { mutableStateOf(profile?.readTimeoutSeconds?.toString() ?: "60") }

    var showProviderDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (profile == null) R.string.dialog_title_add_profile else R.string.dialog_title_edit_profile)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_profile_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = showProviderDropdown,
                    onExpandedChange = { showProviderDropdown = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (provider) {
                            LlmProvider.GEMINI -> stringResource(R.string.provider_gemini)
                            LlmProvider.SILICONFLOW -> stringResource(R.string.provider_siliconflow)
                        },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_llm_provider)) },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProviderDropdown)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = showProviderDropdown,
                        onDismissRequest = { showProviderDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_gemini)) },
                            onClick = {
                                provider = LlmProvider.GEMINI
                                showProviderDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.provider_siliconflow)) },
                            onClick = {
                                provider = LlmProvider.SILICONFLOW
                                showProviderDropdown = false
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            when (provider) {
                                LlmProvider.GEMINI -> stringResource(R.string.label_gemini_key)
                                LlmProvider.SILICONFLOW -> stringResource(R.string.label_siliconflow_key)
                            }
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (provider == LlmProvider.SILICONFLOW || provider == LlmProvider.GEMINI) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.label_model_optional)) },
                        placeholder = { 
                            Text(
                                if (provider == LlmProvider.GEMINI) "gemini-2.5-flash" 
                                else "Qwen/Qwen2.5-72B-Instruct"
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (provider == LlmProvider.SILICONFLOW || provider == LlmProvider.GEMINI) {
                    OutlinedTextField(
                        value = connectTimeout,
                        onValueChange = { if (it.all { char -> char.isDigit() }) connectTimeout = it },
                        label = { Text(stringResource(R.string.label_connect_timeout)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = readTimeout,
                        onValueChange = { if (it.all { char -> char.isDigit() }) readTimeout = it },
                        label = { Text(stringResource(R.string.label_read_timeout)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        LlmProfile(
                            id = profile?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { provider.name },
                            provider = provider,
                            apiKey = apiKey,
                            model = model.ifBlank { 
                                when (provider) {
                                    LlmProvider.SILICONFLOW -> "Qwen/Qwen3-VL-32B-Instruct"
                                    LlmProvider.GEMINI -> "gemini-2.5-flash"
                                }
                            },
                            connectTimeoutSeconds = connectTimeout.toIntOrNull() ?: 30,
                            readTimeoutSeconds = readTimeout.toIntOrNull() ?: 60
                        )
                    )
                },
                enabled = apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (profile != null) {
                    TextButton(
                        onClick = { onDelete(profile.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
