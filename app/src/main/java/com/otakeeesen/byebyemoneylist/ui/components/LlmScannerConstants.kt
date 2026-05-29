package com.otakeeesen.byebyemoneylist.ui.components

object LlmScannerConstants {
    const val RECEIPT_EXTRACTION_PROMPT = "Extract items from this receipt. Return ONLY a JSON object with: 'store_name' (string), 'items' (list of {name: string, quantity: number, price: number}), and 'total_sum' (number). Do NOT include items with negative prices (e.g., discounts or coupons)."
}
