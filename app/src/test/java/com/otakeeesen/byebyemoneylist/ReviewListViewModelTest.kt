package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ReviewListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val productRepository = Mockito.mock(ProductRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProducts loads products from repository`() = runTest {
        val products = listOf(ProductEntity(id = 1, name = "Product 1", barcode = "123", picturePath = null, category = "Test"))
        whenever(productRepository.getAllProductsOnce()).thenReturn(products)

        val viewModel = ReviewListViewModel(productRepository, testDispatcher)
        
        // Trigger the init
        testScheduler.advanceUntilIdle()

        assertEquals(products, viewModel.allProducts.value)
    }
}
