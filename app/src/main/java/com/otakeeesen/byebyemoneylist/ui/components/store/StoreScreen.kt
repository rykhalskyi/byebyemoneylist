package com.otakeeesen.byebyemoneylist.ui.components.store.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.camera.components.SquareCameraCapture
import com.otakeeesen.byebyemoneylist.ui.components.category.MultiSelectCategoryField
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    store: StoreEntity?,
    categories: List<CategoryEntity>,
    storeCategories: List<CategoryEntity>,
    onNavigateBack: () -> Unit,
    onSave: (id: Long?, name: String, logoPath: String, categoryIds: List<Long>, address: String?) -> Unit,
    onMerge: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var name by remember(store) { mutableStateOf(store?.name ?: "") }
    var address by remember(store) { mutableStateOf(store?.address ?: "") }
    var logoPath by remember(store) { mutableStateOf(store?.logoPath ?: "") }
    var selectedCategories by remember(storeCategories) { mutableStateOf(storeCategories) }
    var nameError by remember { mutableStateOf(false) }

    var showCamera by remember { mutableStateOf(false) }
    var showLogoPreview by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCamera = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (store == null) R.string.add_store else R.string.edit_store)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (store != null) {
                        IconButton(onClick = { onMerge(store.id) }) {
                            Icon(Icons.Default.Merge, contentDescription = stringResource(R.string.merge))
                        }
                    }
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            nameError = true
                        } else {
                            onSave(store?.id, trimmed, logoPath, selectedCategories.map { it.id }, address.trim().ifBlank { null })
                            onNavigateBack()
                        }
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text(stringResource(R.string.store_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.name_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Image Management
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!logoPath.isNullOrEmpty()) {
                    AsyncImage(
                        model = logoPath,
                        contentDescription = stringResource(R.string.store_logo),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showLogoPreview = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
                
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            showCamera = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo))
                }
                
                if (!logoPath.isNullOrEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { 
                            ImageStorageManager.deleteImage(logoPath)
                            logoPath = "" 
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_logo))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            MultiSelectCategoryField(
                selectedCategories = selectedCategories,
                allCategories = categories,
                onCategorySelected = { category ->
                    if (category !in selectedCategories) {
                        selectedCategories = selectedCategories + category
                    }
                },
                onCategoryRemoved = { category ->
                    selectedCategories = selectedCategories - category
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showCamera) {
        SquareCameraCapture(
            onImageCaptured = { bitmap ->
                val savedPath = ImageStorageManager.saveImage(context, bitmap)
                if (savedPath != null) {
                    if (!logoPath.isNullOrEmpty()) {
                        ImageStorageManager.deleteImage(logoPath)
                    }
                    logoPath = savedPath
                }
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    if (showLogoPreview && !logoPath.isNullOrEmpty()) {
        Dialog(onDismissRequest = { showLogoPreview = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                AsyncImage(
                    model = logoPath,
                    contentDescription = "Full Store Logo",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
