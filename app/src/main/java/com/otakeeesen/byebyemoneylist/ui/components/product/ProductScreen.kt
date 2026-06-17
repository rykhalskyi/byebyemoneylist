package com.otakeeesen.byebyemoneylist.ui.components.product

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductViewModel
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    viewModel: ProductViewModel,
    onNavigateBack: () -> Unit,
    onMerge: (Long) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanner = GmsBarcodeScanning.getClient(context)

    val title = when {
        uiState.isIncome -> stringResource(R.string.add_income_source)
        uiState.isSubscription -> stringResource(R.string.subscription_product)
        uiState.product == null -> stringResource(R.string.add_product)
        else -> stringResource(R.string.edit_product)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.product != null) {
                        IconButton(onClick = { onMerge(uiState.product!!.id) }) {
                            Icon(Icons.Default.Merge, contentDescription = "Merge")
                        }
                    }

                    IconButton(onClick = { viewModel.updateFavorite(!uiState.isFavorite) }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(
                                if (uiState.isFavorite) R.string.remove_from_favorites else R.string.mark_as_favorite
                            ),
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveProduct(onNavigateBack)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::updateName,
                        label = { Text(if (uiState.isIncome) stringResource(R.string.income_source_name) else stringResource(R.string.product_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (!uiState.isIncome && !uiState.isSubscription) {

                    item {
                        OutlinedTextField(
                            value = uiState.aliases.joinToString(", "),
                            onValueChange = { input ->
                                viewModel.updateAliases(input.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            },
                            label = { Text("Aliases (comma-separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        CategorySelector(
                            selectedCategoryId = uiState.categoryId,
                            categories = uiState.categories.filter { !it.isIncome },
                            onCategorySelected = viewModel::updateCategoryId
                        )
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = uiState.barcode,
                                onValueChange = viewModel::updateBarcode,
                                label = { Text(stringResource(R.string.barcode)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        scanner.startScan()
                                            .addOnSuccessListener { result ->
                                                viewModel.updateBarcode(result.rawValue ?: "")
                                            }
                                    }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                                    }
                                }
                            )
                        }
                    }

                    item {
                        ImagePicker(
                            imagePath = uiState.picturePath,
                            onImagePicked = viewModel::updatePicturePath
                        )
                    }
                } else {
                    item {
                        CategorySelector(
                            selectedCategoryId = uiState.categoryId,
                            categories = if (uiState.isIncome) uiState.categories.filter { it.isIncome } else uiState.categories.filter { !it.isIncome },
                            onCategorySelected = viewModel::updateCategoryId
                        )
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }

                if (uiState.prices.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.price_history),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(uiState.prices) { price ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(price.date)))
                            Text("%.2f".format(price.value))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    selectedCategoryId: Long?,
    categories: List<CategoryEntity>,
    onCategorySelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Column {
        Text(
            text = stringResource(R.string.category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onCategorySelected(category.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePicker(
    imagePath: String,
    onImagePicked: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = ImageStorageManager.saveImage(context, it)
            if (path != null) {
                onImagePicked(path)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val path = ImageStorageManager.saveBitmap(context, it)
            if (path != null) {
                onImagePicked(path)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.add_photo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(8.dp))
        
        if (imagePath.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageStorageManager.getImageFile(context, imagePath),
                    contentDescription = stringResource(R.string.product_preview),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
                
                IconButton(
                    onClick = { onImagePicked("") },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_photo))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (hasCameraPermission) {
                            cameraLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.camera))
                }
                
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.from_gallery))
                }
            }
        }
    }
}
