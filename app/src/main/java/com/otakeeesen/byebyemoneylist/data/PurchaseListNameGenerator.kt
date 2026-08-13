package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PurchaseListNameGenerator(private val shoppingListRepository: ShoppingListRepository) {

    suspend fun generate(store: String, date: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(date))
        val baseName = if (store.isNotBlank()) "$store $dateStr" else "Quick Purchase $dateStr"

        val calendar = Calendar.getInstance().apply { timeInMillis = date }
        val startOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val sameDayLists = shoppingListRepository.getFinishedListsInTimeRange(startOfDay, endOfDay)
        val sameNameCount = sameDayLists.count { it.name == baseName }

        return if (sameNameCount == 0) baseName else "$baseName ${sameNameCount + 1}"
    }
}
