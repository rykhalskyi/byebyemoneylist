package com.otakeeesen.byebyemoneylist.ui.components.scanner

object LlmScannerConstants {
    const val RECEIPT_EXTRACTION_PROMPT = "Extract items from this receipt. Return ONLY a JSON object with: 'store_name' (string), 'items' (list of {name: string, quantity: number, price: number}), and 'total_sum' (number). 'quantity' should be the number of units or weight, and 'price' should be the unit price. Do NOT include items with negative prices (e.g., discounts or coupons). "
}
