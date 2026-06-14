package com.otakeeesen.byebyemoneylist.ui.components.product

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    productId: Long?,
    initialIsSubscription: Boolean = false,
    initialIsIncome: Boolean = false,
    onNavigateBack: () -> Unit,
    onSave: (Long?, String, String, String, Long?, List<String>, Boolean, Boolean, Boolean) -> Unit,
    onMerge: (Long) -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as ByeByeMoneyApplication
    val productRepository = application.productRepository
    val categoryRepository = application.categoryRepository

    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var picturePath by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var aliases by remember { mutableStateOf(listOf<String>()) }
    var isSubscription by remember { mutableStateOf(initialIsSubscription) }
    var isIncome by remember { mutableStateOf(initialIsIncome) }
    var isFavorite by remember { mutableStateOf(false) }

    var allCategories by remember { mutableStateOf(emptyList<CategoryEntity>()) }
    var isLoading by remember { mutableStateOf(true) }

    val scanner = GmsBarcodeScanning.getClient(context)

    val isNewProduct = productId == null
    val title = when {
        isIncome -> stringResource(R.string.add_income_source)
        isSubscription -> stringResource(R.string.subscription_product)
        isNewProduct -> stringResource(R.string.add_product)
        else -> stringResource(R.string.edit_product)
    }

    LaunchedEffect(productId) {
        allCategories = categoryRepository.getAllCategoriesOnce()
        if (productId != null) {
            val product = productRepository.getProductById(productId)
            if (product != null) {
                name = product.name
                barcode = product.barcode
                picturePath = product.picturePath ?: ""
                categoryId = product.categoryId
                isSubscription = product.isSubscription
                isIncome = product.isIncome
                isFavorite = product.isFavorite
                aliases = productRepository.getAliasesByProductId(productId).map { it.aliasName }
            }
        }
        isLoading = false
    }

    // Update isIncome based on selected category
    LaunchedEffect(categoryId) {
        val category = allCategories.find { it.id == categoryId }
        if (category != null) {
            isIncome = category.isIncome
        }
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
                    if (productId != null) {
                        IconButton(onClick = { onMerge(productId) }) {
                            Icon(Icons.Default.Merge, contentDescription = "Merge")
                        }
                    }
                    Button(
                        onClick = {
                            onSave(productId, name, barcode, picturePath, categoryId, aliases, isSubscription, isFavorite, isIncome)
                            onNavigateBack()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
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
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (isIncome) stringResource(R.string.income_source_name) else stringResource(R.string.product_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (!isIncome) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text(stringResource(R.string.barcode)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        scanner.startScan()
                                            .addOnSuccessListener { result ->
                                                barcode = result.rawValue ?: ""
                                            }
                                    }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isIncome = !isIncome },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(checked = isIncome, onCheckedChange = { isIncome = it })
                        Text(stringResource(R.string.is_income), modifier = Modifier.padding(start = 16.dp))
                    }
                }

                item {
                    CategorySelector(
                        selectedCategoryId = categoryId,
                        categories = if (isIncome) allCategories.filter { it.isIncome } else allCategories.filter { !it.isIncome },
                        onCategorySelected = { categoryId = it }
                    )
                }

                if (!isIncome) {
                    item {
                        OutlinedTextField(
                            value = aliases.joinToString(", "),
                            onValueChange = { input ->
                                aliases = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            },
                            label = { Text("Aliases (comma-separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFavorite = !isFavorite },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                        Text(stringResource(R.string.mark_as_favorite))
                    }
                }

                if (!isIncome) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSubscription = !isSubscription },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSubscription, onCheckedChange = { isSubscription = it })
                            Text(stringResource(R.string.subscription_product))
                        }
                    }
                }

                item {
                    ImagePicker(
                        imagePath = picturePath,
                        onImagePicked = { picturePath = it }
                    )
                }
                
                item { Spacer(Modifier.height(32.dp)) }
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
