package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.otakeeesen.byebyemoneylist.data.ShoppingList
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
}
