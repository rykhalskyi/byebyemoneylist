package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.components.ScannedReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddProductUiState(
    val searchQuery: String = "",
    val searchResults: List<ProductEntity> = emptyList(),
    val isLoading: Boolean = false,
    val scannedBarcode: String = "",
    val isScanning: Boolean = false,
    val scannedReceiptResult: ScannedReceipt? = null,
)

class AddProductViewModel(
    private val listId: Long,
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val priceRepository: PriceRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _scannedBarcode = MutableStateFlow("")
    
    private val _isScanning = MutableStateFlow(false)
    private val _scannedReceiptResult = MutableStateFlow<ScannedReceipt?>(null)

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<AddProductUiState> = combine(
        _searchQuery
            .debounce(300L)
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    productRepository.getProducts()
                } else {
                    productRepository.searchProducts(query)
                }
            },
        _searchQuery,
        _scannedBarcode,
        _isScanning,
        _scannedReceiptResult
    ) { results, query, scannedBarcode, isScanning, scannedReceiptResult ->
        AddProductUiState(
            searchQuery = query,
            searchResults = results,
            isLoading = false,
            scannedBarcode = scannedBarcode,
            isScanning = isScanning,
            scannedReceiptResult = scannedReceiptResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddProductUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (_scannedBarcode.value.isNotEmpty() && query != _scannedBarcode.value) {
            _scannedBarcode.value = ""
        }
    }

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    fun setScannedReceiptResult(result: ScannedReceipt?) {
        _scannedReceiptResult.value = result
    }

     fun onBarcodeScanned(barcode: String, onComplete: () -> Unit) {
         viewModelScope.launch {
             val product = withContext(Dispatchers.IO) {
                 productRepository.getProductByBarcode(barcode)
             }
             if (product != null) {
                 _scannedBarcode.value = ""
                 addExistingProduct(product.id, null, onComplete)
             } else {
                 _scannedBarcode.value = barcode
                 _searchQuery.value = barcode
             }
         }
     }

    fun importScannedReceipt(receipt: ScannedReceipt, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                shoppingListRepository.processPurchase(
                    listId = listId,
                    listName = null,
                    storeName = receipt.storeName ?: "Imported Receipt",
                    price = receipt.totalSum ?: 0.0,
                    items = receipt.items,
                    productRepository = productRepository,
                    priceRepository = priceRepository,
                    isChecked = false // Imported items are unchecked by default
                )
            }
            _scannedReceiptResult.value = null
            onComplete()
        }
    }

    fun addExistingProduct(productId: Long, price: Double?, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val nextPosition = shoppingListRepository.getMaxPositionForList(listId) + 1
                shoppingListRepository.insertShoppingListItem(
                    ShoppingListItemEntity(
                        id = generateId(),
                        shoppingListId = listId,
                        productId = productId,
                        quantity = 1,
                        isChecked = false,
                        position = nextPosition,
                        price = price,
                    )
                )
                
                // If price is provided, store it in Price table
                if (price != null) {
                    priceRepository.upsertPriceForProduct(productId, null, price)
                }
            }
            onComplete()
        }
    }

    fun createAndAddProduct(name: String, categoryName: String, barcode: String = "", price: Double? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = if (categoryName.isNotBlank()) {
                    categoryRepository.getOrCreate(categoryName)
                } else null

                val productId = generateId()
                val product = ProductEntity(
                    id = productId,
                    name = name,
                    barcode = barcode,
                    picturePath = null,
                    category = categoryName
                )
                productRepository.insertProduct(product)

                val nextPosition = shoppingListRepository.getMaxPositionForList(listId) + 1
                shoppingListRepository.insertShoppingListItem(
                    ShoppingListItemEntity(
                        id = generateId(),
                        shoppingListId = listId,
                        productId = productId,
                        quantity = 1,
                        isChecked = false,
                        position = nextPosition,
                        price = price,
                    )
                )
                
                // If price is provided, store it in Price table
                if (price != null) {
                    priceRepository.upsertPriceForProduct(productId, null, price)
                }
            }
            onComplete()
            _scannedBarcode.value = ""
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()

     companion object {
        fun provideFactory(listId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return AddProductViewModel(
                    listId,
                    application.productRepository,
                    application.shoppingListRepository,
                    application.categoryRepository,
                    application.priceRepository,
                ) as T
            }
        }
    }
}
