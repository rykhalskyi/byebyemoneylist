package com.otakeeesen.byebyemoneylist.ui.components.product

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Merge
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.ui.components.camera.components.SquareCameraCapture
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ProductViewModel
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    productId: Long?,
    initialIsSubscription: Boolean = false,
    onNavigateBack: () -> Unit,
    onSave: (id: Long?, name: String, barcode: String, picturePath: String, categoryId: Long?, aliases: List<String>, isSubscription: Boolean, isFavorite: Boolean) -> Unit,
    onMerge: (Long) -> Unit,
    viewModel: ProductViewModel = viewModel(factory = ProductViewModel.createFactory(productId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Using local state to manage edits before saving
    var name by remember(uiState.product) { mutableStateOf(uiState.product?.name ?: "") }
    var barcode by remember(uiState.product) { mutableStateOf(uiState.product?.barcode ?: "") }
    var picturePath by remember(uiState.product) { mutableStateOf(uiState.product?.picturePath ?: "") }
    var selectedCategoryId by remember(uiState.product) { mutableStateOf(uiState.product?.categoryId) }
    var aliasText by remember(uiState.aliases) { mutableStateOf(uiState.aliases.joinToString(", ") { it.aliasName }) }
    var isSubscription by remember(uiState.product, initialIsSubscription) { 
        mutableStateOf(uiState.product?.isSubscription ?: initialIsSubscription) 
    }
    var isFavorite by remember(uiState.product) { mutableStateOf(uiState.product?.isFavorite ?: false) }

    var showCamera by remember { mutableStateOf(false) }
    var showProductPreview by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(if (productId == null) R.string.add_product_catalog else R.string.edit_product)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(
                                if (isFavorite) R.string.remove_from_favorites else R.string.mark_as_favorite
                            ),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (productId != null && !isSubscription) {
                        IconButton(onClick = { onMerge(productId) }) {
                            Icon(Icons.Default.Merge, contentDescription = stringResource(R.string.merge))
                        }
                    }
                    TextButton(onClick = {
                        val aliases = aliasText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(productId, name, barcode, picturePath, selectedCategoryId, aliases, isSubscription, isFavorite)
                        onNavigateBack()
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) {
 innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Form Fields
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.product_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (!isSubscription) {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text(stringResource(R.string.aliases_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            // Category Selection
            var categoryExpanded by remember { mutableStateOf(false) }
            val selectedCategory = uiState.categories.find { it.id == selectedCategoryId }
            val selectedCategoryName = selectedCategory?.name ?: ""
            val selectedCategoryColor = try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(selectedCategory?.color ?: "#00000000"))
            } catch (e: Exception) {
                androidx.compose.ui.graphics.Color.Transparent
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {}, // Read only
                    label = { Text(stringResource(R.string.category)) },
                    readOnly = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(selectedCategoryColor)
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    uiState.categories.forEach { category ->
                        val color = try {
                            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(category.color))
                        } catch (e: Exception) {
                            androidx.compose.ui.graphics.Color.Gray
                        }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(category.name)
                                }
                            },
                            onClick = {
                                selectedCategoryId = category.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            
            // Barcode
            if (!isSubscription) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(stringResource(R.string.barcode)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        GmsBarcodeScanning.getClient(context).startScan()
                            .addOnSuccessListener { result: Barcode -> barcode = result.rawValue ?: "" }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_barcode))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            // Image Management
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (picturePath.isNotEmpty()) {
                    AsyncImage(
                        model = picturePath,
                        contentDescription = stringResource(R.string.product_preview),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showProductPreview = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
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
                
                if (picturePath.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { 
                            ImageStorageManager.deleteImage(picturePath)
                            picturePath = "" 
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_photo))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Price History Table
            Text(text = stringResource(R.string.price_history), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            LazyColumn {
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

    if (showCamera) {
        SquareCameraCapture(
            onImageCaptured = { bitmap ->
                val savedPath = ImageStorageManager.saveImage(context, bitmap)
                if (savedPath != null) {
                    // Delete old image if it was a local file
                    if (picturePath.isNotEmpty()) {
                        ImageStorageManager.deleteImage(picturePath)
                    }
                    picturePath = savedPath
                }
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    if (showProductPreview && picturePath.isNotEmpty()) {
        Dialog(onDismissRequest = { showProductPreview = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                AsyncImage(
                    model = picturePath,
                    contentDescription = stringResource(R.string.product_preview),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
