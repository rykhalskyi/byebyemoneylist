package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.components.category.CategoryPickerSheet
import com.otakeeesen.byebyemoneylist.ui.components.category.SelectionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIncomeDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, categoryIds: List<Long>, storeName: String, isRecurring: Boolean, recurringPeriod: String, isForwardEmpty: Boolean, isSubscription: Boolean, isIncome: Boolean) -> Unit,
    initialName: String = "",
    initialCategories: List<CategoryEntity> = emptyList(),
    initialIsRecurring: Boolean = false,
    initialRecurringPeriod: String = "MONTH",
    initialIsForwardEmpty: Boolean = true,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategoryIds by remember { mutableStateOf(initialCategories.map { it.id }.toSet()) }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var recurringPeriod by remember { mutableStateOf(initialRecurringPeriod) }
    var isForwardEmpty by remember { mutableStateOf(initialIsForwardEmpty) }

    val incomeCategories = categories.filter { it.isIncome }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_income_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.income_source_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (incomeCategories.isNotEmpty()) {
                    var showCategorySheet by remember { mutableStateOf(false) }
                    Text(stringResource(R.string.categories), style = MaterialTheme.typography.labelMedium)

                    val selectedNames = incomeCategories
                        .filter { it.id in selectedCategoryIds }
                        .joinToString(", ") { it.name }

                    OutlinedTextField(
                        value = if (selectedNames.isEmpty()) "Select categories..." else selectedNames,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showCategorySheet = true }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select categories")
                            }
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    if (showCategorySheet) {
                        CategoryPickerSheet(
                            categories = incomeCategories,
                            selectedIds = selectedCategoryIds,
                            selectionMode = SelectionMode.Multi,
                            title = "Select Categories",
                            onDismiss = { showCategorySheet = false },
                            onConfirm = { ids ->
                                selectedCategoryIds = ids
                                showCategorySheet = false
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecurring = !isRecurring },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text(stringResource(R.string.recurring))
                }

                if (isRecurring) {
                    RecurringPeriodSelector(
                        selectedPeriod = recurringPeriod,
                        onPeriodSelected = { recurringPeriod = it }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isForwardEmpty = !isForwardEmpty },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isForwardEmpty, onCheckedChange = { isForwardEmpty = it })
                        Text(stringResource(R.string.start_empty))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, selectedCategoryIds.toList(), "", isRecurring, recurringPeriod, isForwardEmpty, false, true)
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringPeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val periods = listOf("DAY", "WEEK", "MONTH", "YEAR")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPeriod,
            onValueChange = {},
            readOnly = true,
            label = { Text("Recurring Period") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            periods.forEach { period ->
                DropdownMenuItem(
                    text = { Text(period) },
                    onClick = {
                        onPeriodSelected(period)
                        expanded = false
                    }
                )
            }
        }
    }
}
