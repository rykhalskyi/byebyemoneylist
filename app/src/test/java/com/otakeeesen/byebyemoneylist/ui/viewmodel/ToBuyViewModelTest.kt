package com.otakeeesen.byebyemoneylist.ui.viewmodel

import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ToBuyViewModelTest {

    private val listId = 42L
    private val testScheduler = TestCoroutineScheduler()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var repository: ShoppingListRepository

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        repository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun activeList() = ShoppingListEntity(
        id = listId,
        name = "To Buy 01.01.2026",
        createDate = 1000L,
        purchaseDate = null,
        storeId = null,
        kind = ListKind.NEED_TO_BUY.name,
        isActive = true,
    )

    private fun item(id: Long, name: String, checked: Boolean, position: Int) = ShoppingListItemEntity(
        id = id,
        shoppingListId = listId,
        productId = 0L,
        quantity = 1.0,
        isChecked = checked,
        position = position,
        customName = name,
    )

    private fun stub(active: ShoppingListEntity?, items: List<ShoppingListItemEntity> = emptyList()) {
        whenever(repository.allShoppingLists).thenReturn(flowOf(listOfNotNull(active)))
        whenever(repository.getItemsForList(listId)).thenReturn(flowOf(items))
    }

    private fun createViewModel() = ToBuyViewModel(repository, ioDispatcher = testDispatcher)

    @Test
    fun `maps active list and items into ui state`() = runTest(testDispatcher) {
        stub(
            active = activeList(),
            items = listOf(
                item(2L, "Bread", checked = true, position = 1),
                item(1L, "Milk", checked = false, position = 0),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listId, state.activeListId)
        assertEquals("To Buy 01.01.2026", state.title)
        assertEquals(2, state.totalCount)
        assertEquals(1, state.checkedCount)
        assertEquals(listOf("Milk", "Bread"), state.items.map { it.name })
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertFalse(state.isLoading)
    }

    @Test
    fun `no active list yields empty ready state`() = runTest(testDispatcher) {
        stub(active = null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.activeListId)
        assertTrue(state.items.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `inactive need to buy list is ignored`() = runTest(testDispatcher) {
        val inactive = activeList().copy(isActive = false)
        whenever(repository.allShoppingLists).thenReturn(flowOf(listOf(inactive)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeListId)
    }

    @Test
    fun `addItem trims persists and clears the input`() = runTest(testDispatcher) {
        stub(active = activeList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNewItemText("  Oat milk  ")
        viewModel.addItem()
        advanceUntilIdle()

        verify(repository).addToBuyItem(listId, "Oat milk")
        assertEquals("", viewModel.uiState.value.newItemText)
    }

    @Test
    fun `addItem ignores blank input`() = runTest(testDispatcher) {
        stub(active = activeList())
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNewItemText("   ")
        viewModel.addItem()
        advanceUntilIdle()

        verify(repository, never()).addToBuyItem(any(), any())
    }

    @Test
    fun `toggleChecked updates state and persists`() = runTest(testDispatcher) {
        stub(active = activeList(), items = listOf(item(1L, "Milk", checked = false, position = 0)))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val current = viewModel.uiState.value.items.first()

        viewModel.toggleChecked(current, true)
        assertEquals(true, viewModel.uiState.value.items.first().isChecked)

        advanceUntilIdle()
        verify(repository).updateItemChecked(1L, true)
    }

    @Test
    fun `renameItem trims and persists customName`() = runTest(testDispatcher) {
        stub(active = activeList(), items = listOf(item(1L, "Milk", checked = false, position = 0)))
        whenever(repository.getShoppingListItemById(1L)).thenReturn(item(1L, "Milk", checked = false, position = 0))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val current = viewModel.uiState.value.items.first()
        viewModel.startEditingItem(current)

        viewModel.renameItem(current, "  Almond milk  ")
        advanceUntilIdle()

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(repository).updateShoppingListItem(captor.capture())
        assertEquals("Almond milk", captor.firstValue.customName)
        assertNull(viewModel.uiState.value.editingItem)
    }

    @Test
    fun `deleteItem persists and undo restores`() = runTest(testDispatcher) {
        stub(active = activeList(), items = listOf(item(1L, "Milk", checked = false, position = 0)))
        val deleted = item(1L, "Milk", checked = false, position = 0)
        whenever(repository.deleteShoppingListItemAndReturn(1L)).thenReturn(deleted)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteItem(viewModel.uiState.value.items.first())
        testScheduler.runCurrent()
        verify(repository).deleteShoppingListItemAndReturn(1L)

        viewModel.undoDelete()
        advanceUntilIdle()
        verify(repository).insertShoppingListItem(deleted)
    }

    @Test
    fun `deleteItem schedules an undo event`() = runTest(testDispatcher) {
        stub(active = activeList(), items = listOf(item(1L, "Milk", checked = false, position = 0)))
        whenever(repository.deleteShoppingListItemAndReturn(1L)).thenReturn(item(1L, "Milk", checked = false, position = 0))
        val viewModel = createViewModel()
        advanceUntilIdle()

        var event: ToBuyEvent? = null
        val job = launch { event = viewModel.events.first() }
        viewModel.deleteItem(viewModel.uiState.value.items.first())
        advanceUntilIdle()

        assertEquals("Milk", (event as ToBuyEvent.ItemDeleted).name)
        job.cancel()
    }

    @Test
    fun `createActiveList forwards the new id`() = runTest(testDispatcher) {
        stub(active = null)
        whenever(repository.createToBuyList(any())).thenReturn(77L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        var received: Long? = null
        viewModel.createActiveList("To Buy") { received = it }
        advanceUntilIdle()

        assertEquals(77L, received)
    }
}
