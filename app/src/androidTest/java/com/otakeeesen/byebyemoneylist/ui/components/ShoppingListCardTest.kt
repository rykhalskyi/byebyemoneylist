package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ShoppingListCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deleteList_showsConfirmationDialog() {
        val onDeleteList = mock(Function0::class.java) as () -> Unit
        val shoppingList = ShoppingList(
            id = 1L,
            title = "Test List",
            items = emptyList(),
            storeId = null
        )

        composeTestRule.setContent {
            ShoppingListCard(
                shoppingList = shoppingList,
                actualPriceRule = "",
                onDeleteList = onDeleteList
            )
        }

        // Open menu
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        
        // Click delete
        composeTestRule.onNodeWithText("Delete list").performClick()
        
        // Verify confirmation dialog shows
        composeTestRule.onNodeWithText("Delete list").assertIsDisplayed() // Title
        composeTestRule.onNodeWithText("Delete \"Test List\"?").assertIsDisplayed() // Text content
        
        // Click Yes
        composeTestRule.onNodeWithText("Yes").performClick()
        
        // Verify action called
        verify(onDeleteList).invoke()
    }

    @Test
    fun toBuyCard_showsAddItem_andTapOpensNameEditor() {
        var editedToBuy: PurchaseItem? = null
        var editedRegular: PurchaseItem? = null
        val item = PurchaseItem(
            id = 10L,
            productId = 0L,
            name = "Milk",
            price = null,
            quantity = 1.0,
            imageUrl = "",
            checked = false,
            customName = "Milk",
        )
        val toBuyList = ShoppingList(
            id = 1L,
            title = "To Buy 01.01.2026",
            items = listOf(item),
            storeId = null,
            kind = ListKind.NEED_TO_BUY,
            isActive = true,
        )

        composeTestRule.setContent {
            ShoppingListCard(
                shoppingList = toBuyList,
                actualPriceRule = "",
                isExpanded = true,
                onEditToBuyItem = { editedToBuy = it },
                onEditItem = { editedRegular = it },
            )
        }

        composeTestRule.onNodeWithText("Add item").assertIsDisplayed()

        composeTestRule.onNodeWithText("Milk").performClick()

        assertTrue("Expected the To Buy name editor callback", editedToBuy?.id == 10L)
        assertNull("Regular item editor must not be used for To Buy items", editedRegular)
    }

    @Test
    fun toBuyCard_menuOpensDedicatedScreen() {
        var opened = false
        val toBuyList = ShoppingList(
            id = 1L,
            title = "To Buy 01.01.2026",
            items = emptyList(),
            storeId = null,
            kind = ListKind.NEED_TO_BUY,
            isActive = true,
        )

        composeTestRule.setContent {
            ShoppingListCard(
                shoppingList = toBuyList,
                actualPriceRule = "",
                onOpenToBuy = { opened = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Open list").performClick()

        assertTrue("Expected onOpenToBuy to be invoked", opened)
    }
}
