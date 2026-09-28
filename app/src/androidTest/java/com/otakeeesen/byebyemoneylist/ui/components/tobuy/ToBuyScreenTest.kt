package com.otakeeesen.byebyemoneylist.ui.components.tobuy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ToBuyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToBuyScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val listId = 5L

    private fun activeList() = ShoppingListEntity(
        id = listId,
        name = "To Buy 01.01.2026",
        createDate = 1L,
        purchaseDate = null,
        storeId = null,
        kind = ListKind.NEED_TO_BUY.name,
        isActive = true,
    )

    private fun item(id: Long, name: String) = ShoppingListItemEntity(
        id = id,
        shoppingListId = listId,
        productId = 0L,
        quantity = 1.0,
        isChecked = false,
        position = id.toInt(),
        customName = name,
    )

    private fun buildViewModel(
        active: ShoppingListEntity?,
        items: List<ShoppingListItemEntity>,
    ): ToBuyViewModel {
        val repository = mock<ShoppingListRepository>()
        whenever(repository.allShoppingLists).thenReturn(flowOf(listOfNotNull(active)))
        whenever(repository.getItemsForList(listId)).thenReturn(flowOf(items))
        return ToBuyViewModel(repository, ioDispatcher = Dispatchers.Main)
    }

    @Test
    fun rendersActiveListItems() {
        val viewModel = buildViewModel(activeList(), listOf(item(1L, "Milk"), item(2L, "Bread")))
        composeTestRule.setContent {
            ToBuyScreen(viewModel = viewModel, onBack = {})
        }

        composeTestRule.onNodeWithText("Milk").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bread").assertIsDisplayed()
    }

    @Test
    fun noActiveListShowsCreateButton() {
        val viewModel = buildViewModel(null, emptyList())
        composeTestRule.setContent {
            ToBuyScreen(viewModel = viewModel, onBack = {})
        }

        composeTestRule.onNodeWithText("Create To Buy list").assertIsDisplayed()
    }
}
