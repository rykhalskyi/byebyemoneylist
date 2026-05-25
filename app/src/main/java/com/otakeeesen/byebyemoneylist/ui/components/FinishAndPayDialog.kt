package com.otakeeesen.byebyemoneylist.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.ShoppingList

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FinishAndPayDialog(
    shoppingList: ShoppingList,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var totalText by remember { mutableStateOf(String.format("%.2f", shoppingList.actualPrice)) }
    var priceError by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
    val scanner = remember { CompositeScanner(preferencesManager) }

    var scannedReceiptResult by remember { mutableStateOf<ScannedReceipt?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            isScanning = true
            coroutineScope.launch {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, tempPhotoUri!!))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, tempPhotoUri!!)
                }
                
                val result = scanner.parse(bitmap)
                if (result.items.isNotEmpty()) {
                    scannedReceiptResult = result
                    showReviewDialog = true
                } else if (result.totalSum != null) {
                    totalText = String.format("%.2f", result.totalSum)
                }
                isScanning = false
            }
        }
    }

    if (showReviewDialog && scannedReceiptResult != null) {
        ReceiptReviewDialog(
            initialReceipt = scannedReceiptResult!!,
            onConfirm = { editedReceipt ->
                editedReceipt.totalSum?.let {
                    totalText = String.format("%.2f", it)
                }
                showReviewDialog = false
                scannedReceiptResult = null
            },
            onDismiss = {
                showReviewDialog = false
                scannedReceiptResult = null
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && tempPhotoUri != null) {
            scanLauncher.launch(tempPhotoUri!!)
        }
    }

    fun validateAndConfirm() {
        val trimmed = totalText.replace(',', '.').trim()
        if (trimmed.isBlank()) {
            priceError = true
            return
        }
        val price = trimmed.toDoubleOrNull()
        if (price == null || price < 0) {
            priceError = true
            return
        }
        priceError = false
        onConfirm(price)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_and_pay_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.estimated_total, shoppingList.itemsTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalText,
                    onValueChange = {
                        totalText = it
                        priceError = false
                    },
                    label = { Text(stringResource(R.string.actual_total)) },
                    isError = priceError,
                    trailingIcon = {
                        IconButton(onClick = {
                            val photoFile = File(context.cacheDir, "receipt_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
                            tempPhotoUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                photoFile
                            )
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = stringResource(R.string.scan_receipt)
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { validateAndConfirm() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (isScanning) {
        LoadingDialog()
    }
}