package com.otakeeesen.byebyemoneylist.ui.components.components.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.util.CurrencyFormatter
import java.text.NumberFormat
import java.util.*

@Composable
fun YearHeader(
    year: Int,
    isExpanded: Boolean,
    totalPrice: Double,
    totalExpenses: Double = 0.0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    val context = LocalContext.current
    val formattedBalance = remember(totalPrice, context) {
        CurrencyFormatter.format(totalPrice, context)
    }
    val formattedExpenses = remember(totalExpenses, context) {
        CurrencyFormatter.format(totalExpenses, context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedBalance,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (totalPrice >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
                if (totalExpenses > 0) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formattedExpenses,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                text = stringResource(R.string.balance) + (if (totalExpenses > 0) " / " + stringResource(R.string.expenses) else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(24.dp)
                .rotate(rotationState),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun MonthHeader(
    monthName: String,
    isExpanded: Boolean,
    totalPrice: Double,
    totalExpenses: Double = 0.0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    val context = LocalContext.current
    val formattedBalance = remember(totalPrice, context) {
        CurrencyFormatter.format(totalPrice, context)
    }
    val formattedExpenses = remember(totalExpenses, context) {
        CurrencyFormatter.format(totalExpenses, context)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedBalance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (totalPrice >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                )
                if (totalExpenses > 0 && totalExpenses != -totalPrice) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formattedExpenses,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(20.dp)
                .rotate(rotationState),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
