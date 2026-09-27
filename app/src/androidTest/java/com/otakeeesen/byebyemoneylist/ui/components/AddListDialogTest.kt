package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.components.shoppinglist.AddListDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddListDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val categories = listOf(
        CategoryEntity(id = 1, name = "Groceries"),
        CategoryEntity(id = 2, name = "Salary", isIncome = true),
    )

    private fun carryOverItem(id: Long, name: String) = PurchaseItem(
        id = id,
        productId = 0L,
        name = name,
        price = null,
        quantity = 1.0,
        imageUrl = "",
        checked = false,
        customName = name,
    )

    @Test
    fun defaultsToToBuyAndCreatesToBuyList() {
        var toBuy = 0
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = { _ -> toBuy++ },
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Create a new To Buy list to plan your shopping.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").performClick()

        assertEquals(1, toBuy)
    }

    @Test
    fun toBuyTabShowsCarryOverItemsAndForwardsCheckedOnes() {
        var received: List<String>? = null
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = { names -> received = names },
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
                carryOverItems = listOf(
                    carryOverItem(1L, "Milk"),
                    carryOverItem(2L, "Bread"),
                ),
            )
        }

        composeTestRule.onNodeWithText("Carry over unfinished items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Milk").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bread").assertIsDisplayed()

        composeTestRule.onNodeWithText("Milk").performClick()
        composeTestRule.onNodeWithText("Create").performClick()

        assertEquals(listOf("Milk"), received)
    }

    @Test
    fun subscriptionTabShowsIntervalDefaultingToMonthly() {
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = { _ -> },
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Subscription").performClick()

        composeTestRule.onNodeWithText("Interval").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monthly").assertIsDisplayed()
    }

    @Test
    fun incomeTabShowsIncomeFields() {
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = { _ -> },
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Income").performClick()

        composeTestRule.onNodeWithText("Income Source Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recurring").assertIsDisplayed()
    }
}
