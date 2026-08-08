package com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.DashboardWidget
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.ui.navigation.Screen

class CategoryWidget(override val config: DashboardWidgetConfig) : DashboardWidget {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Card(
        data: WidgetData,
        onTap: () -> Unit,
        onLongPress: () -> Unit,
        modifier: Modifier
    ) {
        val context = LocalContext.current
        val preferencesManager = remember { (context.applicationContext as ByeByeMoneyApplication).preferencesManager }
        val currencySymbol = preferencesManager.getCurrencySymbol() ?: "€"

        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
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
                    .height(IntrinsicSize.Min)
            ) {
                val accentColor = if (data is WidgetData.CategorySpending) {
                    Color(data.categoryColor)
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(accentColor)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        val titleText = if (data is WidgetData.CategorySpending) {
                            data.categoryName
                        } else {
                            stringResource(R.string.widget_category_spending)
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    when (data) {
                        is WidgetData.CategorySpending -> {
                            Text(
                                text = "$currencySymbol%.2f".format(data.monthTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.this_month_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${stringResource(R.string.all_time_label)}: $currencySymbol%.2f".format(data.overallTotal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is WidgetData.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun createOnTap(navController: NavController, context: Context): () -> Unit = {
        val catId = config.categoryId
        if (catId != null) {
            try {
                navController.getBackStackEntry(Screen.Shopping.route).savedStateHandle["filter_category_id"] = catId
            } catch (e: Exception) {
                navController.currentBackStackEntry?.savedStateHandle?.set("filter_category_id", catId)
            }
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
