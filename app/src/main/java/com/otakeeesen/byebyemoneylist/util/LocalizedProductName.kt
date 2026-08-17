package com.otakeeesen.byebyemoneylist.util

import android.content.Context
import com.otakeeesen.byebyemoneylist.R
import com.otakeeesen.byebyemoneylist.data.QUICK_PURCHASE_PRODUCT_ID
import com.otakeeesen.byebyemoneylist.data.ProductStat

/**
 * Resolves the display name for a [ProductStat], translating the virtual
 * "Quick Purchase" product name into the current locale.
 */
fun localizedProductStatName(stat: ProductStat, context: Context): String =
    if (stat.productId == QUICK_PURCHASE_PRODUCT_ID) {
        context.getString(R.string.quick_purchase_title)
    } else {
        stat.name
    }
