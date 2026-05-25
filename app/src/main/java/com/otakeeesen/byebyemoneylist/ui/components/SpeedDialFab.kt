package com.otakeeesen.byebyemoneylist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otakeeesen.byebyemoneylist.R

@Composable
fun SpeedDialFab(
    onCreateList: () -> Unit = {},
    onInStore: () -> Unit = {},
    onDirectPurchase: () -> Unit = {},
    onScanReceipt: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isOpen by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        label = "fabRotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpeedDialAction(
            visible = isOpen,
            label = R.string.create_list,
            icon = Icons.Default.Add,
            onClick = {
                onCreateList()
                isOpen = false
            },
            index = 0,
        )

        SpeedDialAction(
            visible = isOpen,
            label = R.string.in_store,
            icon = Icons.Default.Place,
            onClick = {
                onInStore()
                isOpen = false
            },
            index = 1,
        )

        SpeedDialAction(
            visible = isOpen,
            label = R.string.direct_purchase,
            icon = Icons.Default.ShoppingCart,
            onClick = {
                onDirectPurchase()
                isOpen = false
            },
            index = 2,
        )

        SpeedDialAction(
            visible = isOpen,
            label = R.string.scan_receipt,
            icon = Icons.Default.DocumentScanner,
            onClick = {
                onScanReceipt()
                isOpen = false
            },
            index = 3,
        )

        FloatingActionButton(
            onClick = { isOpen = !isOpen },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isOpen) {
                    stringResource(R.string.speed_dial_close)
                } else {
                    stringResource(R.string.speed_dial_open)
                },
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun SpeedDialAction(
    visible: Boolean,
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    index: Int,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it * (index + 1) },
        exit = fadeOut() + slideOutVertically { it * (index + 1) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Text(
                    text = stringResource(label),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(label),
                )
            }
        }
    }
}
