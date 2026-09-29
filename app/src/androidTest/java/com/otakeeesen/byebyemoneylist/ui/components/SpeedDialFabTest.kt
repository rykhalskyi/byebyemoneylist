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
        composeTestRule.setContent { SpeedDialFab() }

        composeTestRule.onNodeWithText("Add List").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()

        composeTestRule.onNodeWithText("Add List").assertIsDisplayed()
        composeTestRule.onNodeWithText("Purchase").assertIsDisplayed()
    }

    @Test
    fun addActionOpensAddListDialog() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onAdd = { clicks++ })
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("Add List").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun purchaseActionOpensPurchaseFlow() {
        var clicks = 0
        composeTestRule.setContent {
            SpeedDialFab(onPurchase = { clicks++ })
        }

        composeTestRule.onNodeWithContentDescription("Open actions").performClick()
        composeTestRule.onNodeWithContentDescription("Purchase").performClick()

        assertEquals(1, clicks)
    }
}
