package com.otakeeesen.byebyemoneylist.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.CategoryWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.QuickPurchaseWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.ScanPurchaseWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.SpentTodayWidget
import com.otakeeesen.byebyemoneylist.ui.components.dashboard.widgets.ThisMonthWidget
import kotlinx.serialization.Serializable

interface DashboardWidget {
    val config: DashboardWidgetConfig

    /** Renders the widget card composable (Material3 ElevatedCard). */
    @Composable
    fun Card(
        data: WidgetData,
        onTap: () -> Unit,
        onLongPress: () -> Unit,
        modifier: Modifier,
        dragHandleModifier: Modifier
    )

    /** Creates the navigation/action callback for tap events. */
    fun createOnTap(navController: NavController, context: Context): () -> Unit
}

@Serializable
data class DashboardWidgetConfig(
    val id: String,                   // random UUID
    val type: DashboardWidgetType,    // enum
    val order: Int,
    val categoryId: Long? = null      // only for CATEGORY_SPENDING
)

@Serializable
enum class DashboardWidgetType {
    CATEGORY_SPENDING,
    SPENT_TODAY,
    QUICK_PURCHASE,
    SCAN_PURCHASE,
    THIS_MONTH
}

/** Sealed hierarchy of data payloads — each widget type maps to one subclass. */
sealed class WidgetData {
    data class CategorySpending(
        val monthTotal: Double,
        val overallTotal: Double,
        val categoryName: String,
        val categoryColor: Long,
        val categoryEmoji: String? = null
    ) : WidgetData()

    data class SpentToday(val total: Double) : WidgetData()

    object QuickPurchase : WidgetData()

    object ScanPurchase : WidgetData()

    data class ThisMonth(
        val total: Double,
        val lastMonthTotal: Double,
        val trendPercent: Float    // positive = increase, negative = decrease
    ) : WidgetData()

    object Loading : WidgetData()
}

/** Maps config → concrete widget implementation. */
fun createDashboardWidget(config: DashboardWidgetConfig): DashboardWidget = when (config.type) {
    DashboardWidgetType.CATEGORY_SPENDING -> CategoryWidget(config)
    DashboardWidgetType.SPENT_TODAY       -> SpentTodayWidget(config)
    DashboardWidgetType.QUICK_PURCHASE    -> QuickPurchaseWidget(config)
    DashboardWidgetType.SCAN_PURCHASE     -> ScanPurchaseWidget(config)
    DashboardWidgetType.THIS_MONTH        -> ThisMonthWidget(config)
}
