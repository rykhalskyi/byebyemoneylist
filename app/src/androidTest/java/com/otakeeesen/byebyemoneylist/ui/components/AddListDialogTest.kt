package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test
    fun defaultsToToBuyAndCreatesToBuyList() {
        var toBuy = 0
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = { toBuy++ },
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Create a new To Buy list to plan your shopping.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create").performClick()

        assertEquals(1, toBuy)
    }

    @Test
    fun subscriptionTabShowsIntervalDefaultingToMonthly() {
        composeTestRule.setContent {
            AddListDialog(
                categories = categories,
                onDismiss = {},
                onCreateToBuy = {},
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
                onCreateToBuy = {},
                onCreateSubscription = { _, _, _ -> },
                onCreateIncome = { _, _, _, _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Income").performClick()

        composeTestRule.onNodeWithText("Income Source Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recurring").assertIsDisplayed()
    }
}
