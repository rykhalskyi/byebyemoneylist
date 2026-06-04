package com.otakeeesen.byebyemoneylist.ui.components.product

import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AddProductViewModel
import com.otakeeesen.byebyemoneylist.ui.components.scanner.CompositeScanner
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ReceiptReviewDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.LoadingDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.ErrorDialog
import com.otakeeesen.byebyemoneylist.ui.components.shared.PriceInputDialog
import kotlinx.coroutines.launch
import java.io.File

sealed class PendingProduct {
    data class Existing(val productId: Long) : PendingProduct()
    data class New(val name: String, val categoryName: String, val barcode: String) : PendingProduct()
}

@Composable
fun AddProductScreen(
    listId: Long,
    onBack: () -> Unit,
    viewModel: AddProductViewModel = viewModel(factory = AddProductViewModel.provideFactory(listId)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
    var isLlmEnabled by remember { mutableStateOf(preferencesManager.getActiveProfileId() != null) }
    
    // Update state when screen is resumed
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                isLlmEnabled = preferencesManager.getActiveProfileId() != null
            }
        }
        val lifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    
    val scanner = remember { CompositeScanner(preferencesManager) }

    var showPriceDialog by remember { mutableStateOf(false) }
    var pendingProduct by remember { mutableStateOf<PendingProduct?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var scannerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            viewModel.setScanning(true)
            coroutineScope.launch {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, tempPhotoUri!!))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, tempPhotoUri!!)
                }

                val result = scanner.parse(bitmap)
                if (result.errorMessage != null) {
                    scannerError = result.errorMessage
                }
                viewModel.setScannedReceiptResult(result)
                viewModel.setScanning(false)
            }
        }
    }

    // For scan list launcher
    val scanListLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // Handle scan list result
        // Similar to scan receipt but potentially different parsing logic or intent
    }

    // Handle price dialog result
    if (showPriceDialog && pendingProduct != null) {
        PriceInputDialog(
            initialPrice = null,
            onConfirm = { price, quantity ->
                showPriceDialog = false
                when (val product = pendingProduct) {
                    is PendingProduct.Existing -> viewModel.addExistingProduct(product.productId, price, quantity) { onBack() }
                    is PendingProduct.New -> viewModel.createAndAddProduct(product.name, product.categoryName, product.barcode, price, quantity) { onBack() }
                    null -> {}
                }
                pendingProduct = null
            },
            onDismiss = {
                showPriceDialog = false
                pendingProduct = null
            }
        )
    }

    if (uiState.isScanning) {
        LoadingDialog()
    }

    if (scannerError != null) {
        ErrorDialog(
            title = "Scan Error",
            errorMessage = scannerError!!,
            onDismiss = { scannerError = null }
        )
    }

    if (uiState.scannedReceiptResult != null) {
        ReceiptReviewDialog(
            initialReceipt = uiState.scannedReceiptResult!!,
            onConfirm = { receipt ->
                viewModel.importScannedReceipt(receipt) {
                    // Stay on the screen or go back?
                    // User might want to see the items added. 
                    // But currently we go back after adding one item.
                    // For bulk import, maybe stay? Or go back.
                    // Let's go back to be consistent with other actions.
                    onBack()
                }
            },
            onDismiss = {
                viewModel.setScannedReceiptResult(null)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search product...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                )

                IconButton(onClick = {
                    GmsBarcodeScanning.getClient(context).startScan()
                        .addOnSuccessListener { result: Barcode ->
                            viewModel.onBarcodeScanned(result.rawValue ?: "", onBack)
                        }
                        .addOnCanceledListener { /* no-op */ }
                        .addOnFailureListener { /* no-op */ }
                }) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan barcode",
                    )
                }

                if (isLlmEnabled) {
                    IconButton(onClick = {
                        val photoFile =
                            File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            photoFile
                        )
                        tempPhotoUri = uri
                        scanLauncher.launch(uri)
                    }) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "Scan receipt",
                        )
                    }
                }


            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // "Create new" option if query is not empty
                if (uiState.searchQuery.isNotBlank()) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Create \"${uiState.searchQuery}\"",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable {
                                pendingProduct = PendingProduct.New(uiState.searchQuery, "", uiState.scannedBarcode)
                                showPriceDialog = true
                            },
                        )
                        HorizontalDivider()
                    }
                }

                items(uiState.searchResults, key = { it.id }) { product ->
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = {
                            if (product.category.isNotBlank()) {
                                Text(product.category)
                            }
                        },
                        modifier = Modifier.clickable {
                            pendingProduct = PendingProduct.Existing(product.id)
                            showPriceDialog = true
                        },
                    )
                }
            }
        }
    }
}
