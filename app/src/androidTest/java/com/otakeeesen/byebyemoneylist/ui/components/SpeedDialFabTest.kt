package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.otakeeesen.byebyemoneylist.ui.components.components.SpeedDialFab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpeedDialFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionsAreHiddenUntilFabIsTapped() {
        composeTestRule.setContent { SpeedDialFab(onCreateIncome = {}) }

        composeTestRule.onNodeWithText("To Buy").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()

        composeTestRule.onNodeWithText("Add Income Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Subscription").assertIsDisplayed()
        composeTestRule.onNodeWithText("To Buy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Purchase").assertIsDisplayed()
    }

    @Test
    fun toBuyActionCreatesToBuyList() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onCreateToBuy = { clicks++ }, onCreateIncome = {})
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("To Buy").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun subscriptionActionOpensSubscriptionSetup() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onCreateSubscription = { clicks++ }, onCreateIncome = {})
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("Add Subscription").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun incomeActionOpensIncomeDialog() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onCreateIncome = { clicks++ })
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("Add Income Source").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun purchaseActionOpensPurchaseFlow() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onPurchase = { clicks++ }, onCreateIncome = {})
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("Purchase").performClick()

        assertEquals(1, clicks)
    }
}
