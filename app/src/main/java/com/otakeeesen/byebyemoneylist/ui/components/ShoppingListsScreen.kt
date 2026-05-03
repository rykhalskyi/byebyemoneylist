package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel

@Composable
fun ShoppingListsScreen(
    viewModel: ShoppingListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        items(uiState.shoppingLists) { shoppingList ->
            ShoppingListCard(
                shoppingList = shoppingList,
                onItemCheckedChange = { item, checked ->
                    viewModel.toggleItemChecked(item, checked)
                },
                onDeleteList = {
                    viewModel.deleteShoppingList(shoppingList)
                }
            )
        }
    }
}