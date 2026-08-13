package com.otakeeesen.byebyemoneylist.ui.components.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetType
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (type: DashboardWidgetType, categoryId: Long?) -> Unit
) {
    var selectedType by remember { mutableStateOf(DashboardWidgetType.CATEGORY_SPENDING) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_widget),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.filter_type),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val widgetTypes = listOf(
                    DashboardWidgetType.CATEGORY_SPENDING to (stringResource(R.string.widget_category_spending) to Icons.Default.Category),
                    DashboardWidgetType.SPENT_TODAY to (stringResource(R.string.widget_spent_today) to Icons.Default.Today),
                    DashboardWidgetType.QUICK_PURCHASE to (stringResource(R.string.widget_quick_purchase) to Icons.Default.FlashOn),
                    DashboardWidgetType.SCAN_PURCHASE to (stringResource(R.string.widget_scan_purchase) to Icons.Default.DocumentScanner),
                    DashboardWidgetType.THIS_MONTH to (stringResource(R.string.widget_this_month) to Icons.Default.DateRange)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    widgetTypes.forEach { (type, pair) ->
                        val (label, icon) = pair
                        val selected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedType = type },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedType == DashboardWidgetType.CATEGORY_SPENDING) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.widget_select_category),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val currentCategoryName = categories.find { it.id == selectedCategoryId }?.name
                        ?: stringResource(R.string.widget_select_category)

                    ExposedDropdownMenuBox(
                        expanded = showCategoryDropdown,
                        onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
                    ) {
                        OutlinedTextField(
                            value = currentCategoryName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedType,
                        if (selectedType == DashboardWidgetType.CATEGORY_SPENDING) selectedCategoryId else null
                    )
                },
                enabled = selectedType != DashboardWidgetType.CATEGORY_SPENDING || selectedCategoryId != null
            ) {
                Text(text = stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
