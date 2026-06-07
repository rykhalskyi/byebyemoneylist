package com.otakeeesen.byebyemoneylist.ui.components.scanner

object LlmScannerConstants {
    const val RECEIPT_EXTRACTION_PROMPT = "Extract items from this receipt. Return ONLY a JSON object with: 'store_name' (string), 'items' (list of {name: string, quantity: number, price: number, discount: number, isCoupon: boolean}), and 'total_sum' (number). 'quantity' should be the number of units or weight, and 'price' should be the unit price BEFORE discount. 'discount' is the total discount amount for this item (positive number). Pay close attention to negative values on the receipt, which usually represent discounts (e.g., -1.50 or 1.50-); these should be captured as 'discount' for the preceding item. If a negative value or discount is a general coupon (not tied to a specific product), set 'isCoupon' to true, 'name' to the coupon description, 'price' to 0, and 'discount' to the coupon value. Ensure ALL discounts and coupons are included."
}
