package com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.DashboardWidget
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.ui.navigation.Screen

class QuickPurchaseWidget(override val config: DashboardWidgetConfig) : DashboardWidget {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Card(
        data: WidgetData,
        onTap: () -> Unit,
        onLongPress: () -> Unit,
        modifier: Modifier
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.widget_quick_purchase),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.log_quick_purchase),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Button(
                    onClick = onTap,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = stringResource(R.string.purchase))
                }
            }
        }
    }

    override fun createOnTap(navController: NavController, context: Context): () -> Unit = {
        try {
            navController.getBackStackEntry(Screen.Shopping.route).savedStateHandle["open_purchase"] = true
        } catch (e: Exception) {
            navController.currentBackStackEntry?.savedStateHandle?.set("open_purchase", true)
        }
        navController.navigate(Screen.Shopping.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}
