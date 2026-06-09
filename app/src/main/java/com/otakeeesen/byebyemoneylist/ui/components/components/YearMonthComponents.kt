package com.otakeeesen.byebyemoneylist.ui.components.components.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.otakeeesen.byebyemoneylist.util.CurrencyFormatter
import java.text.NumberFormat
import java.util.*

@Composable
fun YearHeader(
    year: Int,
    isExpanded: Boolean,
    totalPrice: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    val context = LocalContext.current
    val formattedPrice = remember(totalPrice, context) {
        CurrencyFormatter.format(totalPrice, context)
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
        Text(
            text = formattedPrice,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "rotation"
    )

    val context = LocalContext.current
    val formattedPrice = remember(totalPrice, context) {
        CurrencyFormatter.format(totalPrice, context)
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
        Text(
            text = formattedPrice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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
