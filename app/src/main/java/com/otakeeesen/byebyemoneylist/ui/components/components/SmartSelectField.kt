package com.otakeeesen.byebyemoneylist.ui.components.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.otakeeesen.byebyemoneylist.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SmartSelectField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    items: List<T>,
    itemToText: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    itemContent: @Composable ((T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    
    val filteredItems = remember(value, items) {
        if (value.isEmpty()) {
            items
        } else {
            items.filter { itemToText(it).contains(value, ignoreCase = true) }
        }
    }

    val exactMatchExists = items.any { itemToText(it).equals(value, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            supportingText = supportingText,
            leadingIcon = leadingIcon,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            filteredItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        if (itemContent != null) {
                            itemContent(item)
                        } else {
                            Text(itemToText(item))
                        }
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                )
            }

            if (value.isNotBlank() && !exactMatchExists) {
                if (filteredItems.isNotEmpty()) {
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.create_new, value),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        // Keep the current text value, just close the menu
                        expanded = false
                    },
                )
            }
        }
    }
}
