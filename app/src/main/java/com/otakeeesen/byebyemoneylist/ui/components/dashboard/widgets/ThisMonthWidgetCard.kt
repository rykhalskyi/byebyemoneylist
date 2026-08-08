package com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.DashboardWidget
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.ui.navigation.Screen
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class ThisMonthWidget(override val config: DashboardWidgetConfig) : DashboardWidget {

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.widget_this_month),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                when (data) {
                    is WidgetData.ThisMonth -> {
                        Text(
                            text = "$currencySymbol%.2f".format(data.total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val prevMonthName = try {
                            YearMonth.now().minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        } catch (e: Exception) {
                            "last month"
                        }

                        val trendArrow = if (data.trendPercent > 0) "↑" else if (data.trendPercent < 0) "↓" else "•"
                        val trendColor = if (data.trendPercent > 0) {
                            MaterialTheme.colorScheme.error
                        } else if (data.trendPercent < 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Text(
                            text = "$trendArrow %.1f%% %s".format(Math.abs(data.trendPercent), stringResource(R.string.trend_vs_last_month)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = trendColor
                        )
                    }
                    is WidgetData.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
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

    override fun createOnTap(navController: NavController, context: Context): () -> Unit = {
        try {
            navController.getBackStackEntry(Screen.Shopping.route).savedStateHandle["filter_date"] = "this_month"
        } catch (e: Exception) {
            navController.currentBackStackEntry?.savedStateHandle?.set("filter_date", "this_month")
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
