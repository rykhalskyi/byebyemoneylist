package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.components.category.CategoryPickerSheet
import com.otakeeesen.byebyemoneylist.ui.components.category.SelectionMode

enum class AddListTab {
    TO_BUY,
    SUBSCRIPTION,
    INCOME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddListDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onCreateToBuy: (carryOverNames: List<String>) -> Unit,
    onCreateSubscription: (name: String, categoryIds: List<Long>, interval: String) -> Unit,
    onCreateIncome: (name: String, categoryIds: List<Long>, isRecurring: Boolean, recurringPeriod: String, isForwardEmpty: Boolean) -> Unit,
    carryOverItems: List<PurchaseItem> = emptyList(),
    initialTab: AddListTab = AddListTab.TO_BUY,
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    var carryOverIds by remember(carryOverItems) { mutableStateOf(emptySet<Long>()) }

    var subscriptionName by remember { mutableStateOf("") }
    var subscriptionCategoryIds by remember { mutableStateOf(emptySet<Long>()) }
    var subscriptionInterval by remember { mutableStateOf("MONTH") }

    var incomeName by remember { mutableStateOf("") }
    var incomeCategoryIds by remember { mutableStateOf(emptySet<Long>()) }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringPeriod by remember { mutableStateOf("MONTH") }
    var isForwardEmpty by remember { mutableStateOf(true) }

    val incomeCategories = categories.filter { it.isIncome }

    val canConfirm = when (selectedTab) {
        AddListTab.TO_BUY -> true
        AddListTab.SUBSCRIPTION -> subscriptionName.isNotBlank()
        AddListTab.INCOME -> incomeName.isNotBlank()
    }

    fun confirm() {
        when (selectedTab) {
            AddListTab.TO_BUY ->
                onCreateToBuy(carryOverItems.filter { it.id in carryOverIds }.map { it.name })
            AddListTab.SUBSCRIPTION ->
                onCreateSubscription(subscriptionName.trim(), subscriptionCategoryIds.toList(), subscriptionInterval)
            AddListTab.INCOME ->
                onCreateIncome(incomeName.trim(), incomeCategoryIds.toList(), isRecurring, recurringPeriod, isForwardEmpty)
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_list)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddListTab.entries.forEach { tab ->
                        val label = stringResource(
                            when (tab) {
                                AddListTab.TO_BUY -> R.string.to_buy
                                AddListTab.SUBSCRIPTION -> R.string.subscription
                                AddListTab.INCOME -> R.string.income
                            }
                        )
                        if (selectedTab == tab) {
                            Button(
                                onClick = { selectedTab = tab },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedTab = tab },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                when (selectedTab) {
                    AddListTab.TO_BUY -> {
                        Text(
                            text = stringResource(R.string.new_to_buy_list_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (carryOverItems.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.carry_over_items),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            carryOverItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            carryOverIds = if (item.id in carryOverIds) {
                                                carryOverIds - item.id
                                            } else {
                                                carryOverIds + item.id
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = item.id in carryOverIds,
                                        onCheckedChange = { checked ->
                                            carryOverIds = if (checked) {
                                                carryOverIds + item.id
                                            } else {
                                                carryOverIds - item.id
                                            }
                                        },
                                    )
                                    Text(item.name)
                                }
                            }
                        }
                    }

                    AddListTab.SUBSCRIPTION -> {
                        OutlinedTextField(
                            value = subscriptionName,
                            onValueChange = { subscriptionName = it },
                            label = { Text(stringResource(R.string.list_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        CategorySelectionField(
                            categories = categories,
                            selectedIds = subscriptionCategoryIds,
                            onSelectedChange = { subscriptionCategoryIds = it },
                        )

                        RecurringPeriodSelector(
                            selectedPeriod = subscriptionInterval,
                            onPeriodSelected = { subscriptionInterval = it },
                            label = stringResource(R.string.subscription_interval),
                        )
                    }

                    AddListTab.INCOME -> {
                        OutlinedTextField(
                            value = incomeName,
                            onValueChange = { incomeName = it },
                            label = { Text(stringResource(R.string.income_source_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        if (incomeCategories.isNotEmpty()) {
                            CategorySelectionField(
                                categories = incomeCategories,
                                selectedIds = incomeCategoryIds,
                                onSelectedChange = { incomeCategoryIds = it },
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRecurring = !isRecurring },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                            Text(stringResource(R.string.recurring))
                        }

                        if (isRecurring) {
                            RecurringPeriodSelector(
                                selectedPeriod = recurringPeriod,
                                onPeriodSelected = { recurringPeriod = it },
                                label = stringResource(R.string.recurring_period),
                                periods = listOf("DAY", "WEEK", "MONTH", "YEAR"),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isForwardEmpty = !isForwardEmpty },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = isForwardEmpty, onCheckedChange = { isForwardEmpty = it })
                                Text(stringResource(R.string.start_empty))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = canConfirm) {
                Text(
                    stringResource(
                        if (selectedTab == AddListTab.INCOME) R.string.save else R.string.create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectionField(
    categories: List<CategoryEntity>,
    selectedIds: Set<Long>,
    onSelectedChange: (Set<Long>) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.categories), style = MaterialTheme.typography.labelMedium)

        val selectedNames = categories
            .filter { it.id in selectedIds }
            .joinToString(", ") { it.name }

        OutlinedTextField(
            value = if (selectedNames.isEmpty()) stringResource(R.string.select_categories_hint) else selectedNames,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { showSheet = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.select_categories))
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
    }

    if (showSheet) {
        CategoryPickerSheet(
            categories = categories,
            selectedIds = selectedIds,
            selectionMode = SelectionMode.Multi,
            title = stringResource(R.string.select_categories),
            onDismiss = { showSheet = false },
            onConfirm = { ids ->
                onSelectedChange(ids)
                showSheet = false
            }
        )
    }
}
