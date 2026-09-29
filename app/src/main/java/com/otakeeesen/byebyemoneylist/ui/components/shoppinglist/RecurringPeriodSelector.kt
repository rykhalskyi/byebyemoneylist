package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.otakeeesen.byebyemoneylist.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringPeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    label: String,
    periods: List<String> = listOf("WEEK", "MONTH", "YEAR"),
) {
    var expanded by remember { mutableStateOf(false) }
    val periodLabels = mapOf(
        "DAY" to stringResource(R.string.period_day),
        "WEEK" to stringResource(R.string.period_week),
        "MONTH" to stringResource(R.string.period_month),
        "YEAR" to stringResource(R.string.period_year)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = periodLabels[selectedPeriod] ?: selectedPeriod,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
                    text = { Text(periodLabels[period] ?: period) },
                    onClick = {
                        onPeriodSelected(period)
                        expanded = false
                    }
                )
            }
        }
    }
}
