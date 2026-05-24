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

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            isScanning = true
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extracted = ReceiptScanner.extractTotal(visionText)
                    if (extracted != null) {
                        totalText = String.format("%.2f", extracted)
                    }
                    isScanning = false
                }
                .addOnFailureListener {
                    isScanning = false
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scanLauncher.launch()
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
                    supportingText = if (priceError) {
                        { Text(stringResource(R.string.price_must_be_number)) }
                    } else if (isScanning) {
                        { Text(stringResource(R.string.scanning)) }
                    } else null,
                    trailingIcon = {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = stringResource(R.string.scan_receipt)
                                )
                            }
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
}