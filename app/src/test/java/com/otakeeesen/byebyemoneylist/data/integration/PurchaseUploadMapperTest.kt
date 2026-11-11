package com.otakeeesen.byebyemoneylist.data.integration

import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseUploadMapperTest {

    private val category = CategoryEntity(id = 3L, name = "Dairy")
    private val store = StoreEntity(
        id = 7L,
        name = "Lidl",
        logoPath = null,
        address = "Hauptstr. 1",
        receiptName = "LIDL",
    )
    private val list = ShoppingListEntity(
        id = 1L,
        name = "Purchase 09.11.2026",
        createDate = 1_728_571_200_000L,
        purchaseDate = 1_728_571_500_000L,
        storeId = 7L,
        isFinished = true,
        finalTotal = 43.99,
        kind = "PURCHASE",
    )

    @Test
    fun `builds the shared contract from local rows`() {
        val product = ProductEntity(
            id = 5L,
            name = "Milk",
            barcode = "4001234567890",
            picturePath = null,
            categoryId = 3L,
        )
        val item = item(
            id = 10L,
            productId = 5L,
            customName = null,
            productName = "Milk",
            itemPrice = 1.29,
            price = 1.29,
            discount = 0.2,
            productCategoryId = 3L,
        )

        val request = PurchaseUploadMapper.build(
            list = list,
            uploadId = "b1f0c7d2-3b1e-4a67-9c55-0e1d2f3a4b5c",
            clientId = "client-1",
            store = store,
            listCategories = listOf(category),
            items = listOf(item),
            productsById = mapOf(5L to product),
            categoriesById = mapOf(3L to category),
            currency = "€",
        )

        assertEquals(PURCHASE_UPLOAD_SCHEMA, request.schema)
        assertEquals("b1f0c7d2-3b1e-4a67-9c55-0e1d2f3a4b5c", request.listId)
        assertEquals("client-1", request.clientId)
        assertEquals("Purchase 09.11.2026", request.list.name)
        assertEquals("2024-10-10T14:40:00Z", request.list.createdAt)
        assertEquals("Lidl", request.list.store?.name)
        assertEquals("Hauptstr. 1", request.list.store?.address)
        assertEquals("LIDL", request.list.store?.receiptName)
        assertEquals(1, request.list.categories.size)
        assertEquals("Dairy", request.list.categories[0].name)

        val mapped = request.items.single()
        assertEquals("10", mapped.lineId)
        assertEquals("Milk", mapped.name)
        assertEquals(2.0, mapped.quantity, 0.0)
        assertEquals(1.29, mapped.unitPrice ?: 0.0, 0.0)
        assertEquals(0.2, mapped.discount ?: 0.0, 0.0)
        assertFalse(mapped.isCoupon)
        assertEquals("4001234567890", mapped.barcode)
        assertEquals("Dairy", mapped.categoryHint)
    }

    @Test
    fun `coupons use custom name and latest price fallback`() {
        val coupon = item(
            id = 11L,
            productId = 0L,
            customName = "Store coupon",
            productName = null,
            itemPrice = null,
            price = 0.0,
            discount = 0.5,
            productCategoryId = null,
        )

        val request = PurchaseUploadMapper.build(
            list = list,
            uploadId = "upload-id",
            clientId = null,
            store = null,
            listCategories = emptyList(),
            items = listOf(coupon),
            productsById = emptyMap(),
            categoriesById = emptyMap(),
            currency = null,
        )

        val mapped = request.items.single()
        assertTrue(mapped.isCoupon)
        assertEquals("Store coupon", mapped.name)
        assertEquals(0.0, mapped.unitPrice ?: 0.0, 0.0)
        assertNull(mapped.barcode)
        assertNull(mapped.categoryHint)
        assertNull(request.clientId)
        assertNull(request.list.store)
        assertNull(request.list.currency)
    }

    private fun item(
        id: Long,
        productId: Long,
        customName: String?,
        productName: String?,
        itemPrice: Double?,
        price: Double,
        discount: Double?,
        productCategoryId: Long?,
    ) = ShoppingListItemWithProduct(
        id = id,
        shoppingListId = 1L,
        productId = productId,
        quantity = 2.0,
        isChecked = true,
        position = 0,
        productName = productName,
        productPicturePath = null,
        productStatus = "reviewed",
        productIsSubscription = false,
        productIsFavorite = false,
        itemPrice = itemPrice,
        price = price,
        discount = discount,
        customName = customName,
        productCategoryId = productCategoryId,
    )
}
