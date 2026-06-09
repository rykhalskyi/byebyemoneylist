package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListLogicTest {

    @Test
    fun `itemsTotal calculates sum correctly including discounts`() {
        val items = listOf(
            PurchaseItem(id = 1, productId = 1, name = "Apple", quantity = 2.0, price = 1.5, checked = true, imageUrl = ""), // 3.0
            PurchaseItem(id = 2, productId = 2, name = "Bread", quantity = 1.0, price = 2.0, checked = true, discount = 0.5, imageUrl = ""), // 2.0 - 0.5 = 1.5
            PurchaseItem(id = 3, productId = 3, name = "Coupon", quantity = 1.0, price = 0.0, checked = true, discount = 1.0, imageUrl = ""), // 0.0 - 1.0 = -1.0
            PurchaseItem(id = 4, productId = 4, name = "Unchecked", quantity = 1.0, price = 10.0, checked = false, imageUrl = "") // Ignored
        )

        val list = ShoppingList(
            id = 1,
            title = "Test List",
            items = items,
            isFinished = false,
            storeId = null
        )

        // Expected: 3.0 + 1.5 - 1.0 = 3.5
        assertEquals(3.5, list.itemsTotal, 0.001)
    }

    @Test
    fun `itemsTotal includes all items for subscriptions regardless of checked status`() {
        val items = listOf(
            PurchaseItem(id = 1, productId = 1, name = "Netflix", quantity = 1.0, price = 15.0, checked = false, imageUrl = ""),
            PurchaseItem(id = 2, productId = 2, name = "Spotify", quantity = 1.0, price = 10.0, checked = false, discount = 2.0, imageUrl = "")
        )

        val list = ShoppingList(
            id = 1,
            title = "Subscriptions",
            items = items,
            isFinished = false,
            isSubscription = true,
            storeId = null
        )

        // Expected: 15.0 + (10.0 - 2.0) = 23.0
        assertEquals(23.0, list.itemsTotal, 0.001)
    }
}
