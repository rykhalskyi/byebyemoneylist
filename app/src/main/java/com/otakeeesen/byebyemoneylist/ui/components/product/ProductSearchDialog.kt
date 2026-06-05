package com.otakeeesen.byebyemoneylist.ui.components.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProductSearchDialog(
    onDismiss: () -> Unit,
    onProductSelected: (ProductEntity) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val productRepository = remember { (context.applicationContext as ByeByeMoneyApplication).productRepository }
    
    var allProducts by remember { mutableStateOf<List<ProductEntity>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            allProducts = productRepository.getAllProductsOnce()
        }
    }

    val searchResults = allProducts.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.barcode.contains(searchQuery, ignoreCase = true) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_product)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_product)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(searchResults) { product ->
                        ListItem(
                            headlineContent = { Text(product.name) },
                            modifier = Modifier.clickable { onProductSelected(product) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
