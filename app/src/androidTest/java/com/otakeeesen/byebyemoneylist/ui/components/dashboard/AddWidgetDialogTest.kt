package com.otakeeesen.byebyemoneylist.ui.components.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddWidgetDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toBuyOptionIsAvailableAndCreatesToBuyWidget() {
        var createdType: DashboardWidgetType? = null
        composeTestRule.setContent {
            AddWidgetDialog(
                categories = emptyList(),
                onDismiss = {},
                onConfirm = { type, _ -> createdType = type },
            )
        }

        composeTestRule.onNodeWithText("To Buy List").assertIsDisplayed()
        composeTestRule.onNodeWithText("To Buy List").performClick()
        composeTestRule.onNodeWithText("Create").performClick()

        assertEquals(DashboardWidgetType.TO_BUY, createdType)
    }
}
