package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Scaffold
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel

@Composable
fun ShoppingListsScreen(
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            SpeedDialFab(
                onCreateList = { viewModel.createList() },
                onInStore = { viewModel.inStore() },
                onDirectPurchase = { viewModel.directPurchase() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(uiState.shoppingLists, key = { it.id }) { shoppingList ->
                ShoppingListCard(
                    shoppingList = shoppingList,
                    onItemCheckedChange = { item, checked ->
                        viewModel.toggleItemChecked(item, checked)
                    },
                    onDeleteList = {
                        viewModel.deleteShoppingList(shoppingList)
                    },
                    onFinishAndPay = {
                        viewModel.finishAndPay(shoppingList)
                    },
                )
            }
        }
    }
}
