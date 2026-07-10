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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.category.CategoryChipsField
import com.otakeeesen.byebyemoneylist.ui.components.components.SmartSelectField

@Composable
fun CreateShoppingListDialog(
    categories: List<CategoryEntity>,
    stores: List<StoreEntity>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, categoryIds: List<Long>, storeName: String, isRecurring: Boolean, recurringPeriod: String, isForwardEmpty: Boolean, isSubscription: Boolean) -> Unit,
    onImportFromClipboard: (() -> Unit)? = null,
    initialName: String = "",
    initialCategories: List<CategoryEntity> = emptyList(),
    initialStore: String = "",
    initialIsRecurring: Boolean = false,
    initialRecurringPeriod: String = "MONTH",
    initialIsForwardEmpty: Boolean = true,
    initialIsSubscription: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategories by remember { mutableStateOf(initialCategories) }
    var storeText by remember { mutableStateOf(initialStore) }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var recurringPeriod by remember { mutableStateOf(initialRecurringPeriod) }
    var isForwardEmpty by remember { mutableStateOf(initialIsForwardEmpty) }
    var isSubscription by remember { mutableStateOf(initialIsSubscription) }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = initialName.isNotEmpty()
    var pendingStoreConfirm by remember { mutableStateOf<String?>(null) }
    var pendingConfirmData by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun validateAndConfirm() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = true
            return
        }
        nameError = false

        val trimmedStore = storeText.trim()
        val storeExists = stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (trimmedStore.isNotEmpty() && !storeExists) {
            pendingStoreConfirm = trimmedStore
            pendingConfirmData = Pair(trimmedName, trimmedStore)
            return
        }

        onConfirm(trimmedName, selectedCategories.map { it.id }, trimmedStore, if (isSubscription) true else isRecurring, recurringPeriod, if (isSubscription) false else isForwardEmpty, isSubscription)
        onDismiss()
    }

    // Store confirmation dialog
    pendingStoreConfirm?.let { storeName ->
        AlertDialog(
            onDismissRequest = { pendingStoreConfirm = null },
            title = { Text(stringResource(R.string.new_store_title)) },
            text = { Text(stringResource(R.string.new_store_confirmation, storeName)) },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingConfirmData
                    pendingStoreConfirm = null
                    pendingConfirmData = null
                    if (data != null) {
                        onConfirm(data.first, selectedCategories.map { it.id }, data.second, if (isSubscription) true else isRecurring, recurringPeriod, if (isSubscription) false else isForwardEmpty, isSubscription)
                        onDismiss()
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStoreConfirm = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) R.string.edit_shopping_list else R.string.create_shopping_list)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.list_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                CategoryChipsField(
                    selectedCategories = selectedCategories,
                    allCategories = categories,
                    onCategorySelected = { category ->
                        if (category !in selectedCategories) {
                            selectedCategories = selectedCategories + category
                        }
                    },
                    onCategoryRemoved = { category ->
                        selectedCategories = selectedCategories - category
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                SmartSelectField(
                    value = storeText,
                    onValueChange = { storeText = it },
                    label = stringResource(R.string.store),
                    items = stores,
                    itemToText = { it.name },
                    onItemSelected = { storeText = it.name }
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.subscription), modifier = Modifier.weight(1f))
                    Switch(
                        checked = isSubscription,
                        onCheckedChange = { isSubscription = it }
                    )
                }

                if (!isSubscription) {
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.recurring), modifier = Modifier.weight(1f))
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                    }
                }

                if (isRecurring || isSubscription) {
                    Spacer(Modifier.height(12.dp))
                    
                    RecurringSettingsSection(
                        period = recurringPeriod,
                        onPeriodChange = { recurringPeriod = it },
                        forwardEmpty = if (isSubscription) false else isForwardEmpty,
                        onForwardEmptyChange = { if (!isSubscription) isForwardEmpty = it },
                        isSubscription = isSubscription
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { validateAndConfirm() }) {
                Text(stringResource(if (isEditing) R.string.save else R.string.create))
            }
        },
        dismissButton = {
            Row {
                if (onImportFromClipboard != null) {
                    TextButton(onClick = {
                        onImportFromClipboard()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.import_from_clipboard))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringSettingsSection(
    period: String,
    onPeriodChange: (String) -> Unit,
    forwardEmpty: Boolean,
    onForwardEmptyChange: (Boolean) -> Unit,
    isSubscription: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var expanded by remember { mutableStateOf(false) }
        val periods = listOf("WEEK", "MONTH", "YEAR")
        val periodLabels = mapOf(
            "WEEK" to stringResource(R.string.period_week),
            "MONTH" to stringResource(R.string.period_month),
            "YEAR" to stringResource(R.string.period_year)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = periodLabels[period] ?: period,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.recurring_period)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                periods.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(periodLabels[p] ?: p) },
                        onClick = {
                            onPeriodChange(p)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isSubscription) Modifier else Modifier.clickable { onForwardEmptyChange(!forwardEmpty) })
        ) {
            Checkbox(
                checked = forwardEmpty,
                onCheckedChange = { onForwardEmptyChange(it) },
                enabled = !isSubscription
            )
            Text(stringResource(R.string.start_empty), color = if (isSubscription) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
        }
    }
}
