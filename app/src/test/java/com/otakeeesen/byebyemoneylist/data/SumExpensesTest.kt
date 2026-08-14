package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SumExpensesTest {

    // ── Fixtures ───────────────────────────────────────────────────────

    private fun purchaseItem(
        id: Long,
        price: Double?,
        quantity: Double = 1.0,
        checked: Boolean = true,
        discount: Double? = null,
    ) = PurchaseItem(
        id = id,
        productId = id,
        name = "Item $id",
        price = price,
        quantity = quantity,
        imageUrl = "",
        checked = checked,
        discount = discount,
    )

    private fun shoppingList(
        id: Long,
        items: List<PurchaseItem> = emptyList(),
        finalTotal: Double? = null,
        isIncome: Boolean = false,
    ) = ShoppingList(
        id = id,
        title = "List $id",
        items = items,
        finalTotal = finalTotal,
        storeId = null,
        isIncome = isIncome,
    )

    private fun entity(
        id: Long,
        finalTotal: Double? = null,
        isIncome: Boolean = false,
    ) = ShoppingListEntity(
        id = id,
        name = "List $id",
        createDate = 1_000_000L,
        purchaseDate = 1_000_000L,
        storeId = null,
        isFinished = true,
        finalTotal = finalTotal,
        isIncome = isIncome,
    )

    private fun itemWithProduct(
        id: Long,
        listId: Long,
        itemPrice: Double?,
        price: Double,
        quantity: Double = 1.0,
        discount: Double? = null,
    ) = ShoppingListItemWithProduct(
        id = id,
        shoppingListId = listId,
        productId = id,
        quantity = quantity,
        isChecked = true,
        position = 0,
        productName = "Product $id",
        productPicturePath = null,
        productStatus = "reviewed",
        productIsSubscription = false,
        productIsFavorite = false,
        itemPrice = itemPrice,
        price = price,
        discount = discount,
        customName = null,
        productCategoryId = null,
    )

    private fun adjustedItem(
        listId: Long,
        listPriceActual: Double,
        isIncome: Boolean = false,
    ) = AdjustedItem(
        productName = "Item",
        productId = listId,
        quantity = 1.0,
        itemTotal = listPriceActual,
        listPriceActual = listPriceActual,
        discount = null,
        listId = listId,
        storeId = null,
        storeName = null,
        dateMillis = 1_000_000L,
        categoryId = null,
        categoryName = null,
        isIncome = isIncome,
    )

    // ── sumExpenses(List<ShoppingList>, rule) ─────────────────────────

    @Test
    fun `domain lists sum to abs of calculateActualPrice`() {
        val groceries = shoppingList(
            id = 1L,
            items = listOf(purchaseItem(1L, price = 10.0, quantity = 2.0)),
            finalTotal = 50.0,
        )
        val snacks = shoppingList(
            id = 2L,
            items = listOf(purchaseItem(2L, price = 5.0)),
            finalTotal = 5.0,
        )

        val result = sumExpenses(listOf(groceries, snacks), "PURCHASE_PRICE")

        assertEquals(55.0, result, 0.001)
    }

    @Test
    fun `domain lists exclude income`() {
        val income = shoppingList(id = 1L, finalTotal = 100.0, isIncome = true)
        val expense = shoppingList(id = 2L, finalTotal = 20.0)

        val result = sumExpenses(listOf(income, expense), "PURCHASE_PRICE")

        assertEquals(20.0, result, 0.001)
    }

    @Test
    fun `PURCHASE_PRICE falls back to itemsTotal when finalTotal is zero or null`() {
        val nullTotal = shoppingList(
            id = 1L,
            items = listOf(purchaseItem(1L, price = 10.0, quantity = 2.0)),
            finalTotal = null,
        )
        val zeroTotal = shoppingList(
            id = 2L,
            items = listOf(purchaseItem(2L, price = 15.0, quantity = 2.0)),
            finalTotal = 0.0,
        )

        val result = sumExpenses(listOf(nullTotal, zeroTotal), "PURCHASE_PRICE")

        assertEquals(20.0 + 30.0, result, 0.001)
    }

    @Test
    fun `BIGGER_VALUE picks max of itemsTotal and finalTotal`() {
        val finalTotalWins = shoppingList(
            id = 1L,
            items = listOf(purchaseItem(1L, price = 10.0, quantity = 2.0)),
            finalTotal = 50.0,
        )
        val itemsWin = shoppingList(
            id = 2L,
            items = listOf(purchaseItem(2L, price = 20.0, quantity = 1.0)),
            finalTotal = 10.0,
        )

        val result = sumExpenses(listOf(finalTotalWins, itemsWin), "BIGGER_VALUE")

        assertEquals(50.0 + 20.0, result, 0.001)
    }

    @Test
    fun `domain empty input returns zero`() {
        assertEquals(0.0, sumExpenses(emptyList(), "PURCHASE_PRICE"), 0.001)
    }

    // ── sumExpenses(List<ShoppingListEntity>, items, rule) ────────────

    @Test
    fun `entities map to domain and sum`() {
        val lists = listOf(
            entity(id = 1L, finalTotal = 50.0),
            entity(id = 2L, finalTotal = null),
        )
        val items = listOf(
            itemWithProduct(1L, listId = 1L, itemPrice = 10.0, price = 0.0, quantity = 2.0),
            itemWithProduct(2L, listId = 2L, itemPrice = 5.0, price = 0.0),
        )

        val result = sumExpenses(lists, items, "PURCHASE_PRICE")

        assertEquals(50.0 + 5.0, result, 0.001)
    }

    @Test
    fun `entity with no items uses finalTotal only`() {
        val lists = listOf(entity(id = 1L, finalTotal = 42.0))

        val result = sumExpenses(lists, emptyList(), "PURCHASE_PRICE")

        assertEquals(42.0, result, 0.001)
    }

    @Test
    fun `entity excludes income`() {
        val lists = listOf(
            entity(id = 1L, finalTotal = 100.0, isIncome = true),
            entity(id = 2L, finalTotal = 20.0),
        )

        val result = sumExpenses(lists, emptyList(), "PURCHASE_PRICE")

        assertEquals(20.0, result, 0.001)
    }

    // ── sumExpenses(List<AdjustedItem>) ───────────────────────────────

    @Test
    fun `adjusted items count each list once`() {
        val items = listOf(
            adjustedItem(listId = 1L, listPriceActual = -50.0),
            adjustedItem(listId = 1L, listPriceActual = -50.0),
            adjustedItem(listId = 2L, listPriceActual = -30.0),
        )

        val result = sumExpenses(items)

        assertEquals(50.0 + 30.0, result, 0.001)
    }

    @Test
    fun `adjusted items exclude income and apply abs`() {
        val items = listOf(
            adjustedItem(listId = 1L, listPriceActual = -40.0),
            adjustedItem(listId = 2L, listPriceActual = 100.0, isIncome = true),
        )

        val result = sumExpenses(items)

        assertEquals(40.0, result, 0.001)
    }

    @Test
    fun `adjusted items empty input returns zero`() {
        assertEquals(0.0, sumExpenses(emptyList()), 0.001)
    }
}
