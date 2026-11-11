package com.otakeeesen.byebyemoneylist.data.integration

import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import java.time.Instant

/**
 * Pure mapping from local Room rows to the shared `bbml.purchase-upload/1`
 * contract. Contains no network or database access so it is unit-testable.
 */
object PurchaseUploadMapper {
    fun build(
        list: ShoppingListEntity,
        uploadId: String,
        clientId: String?,
        store: StoreEntity?,
        listCategories: List<CategoryEntity>,
        items: List<ShoppingListItemWithProduct>,
        productsById: Map<Long, ProductEntity>,
        categoriesById: Map<Long, CategoryEntity>,
        currency: String?,
    ): PurchaseUploadRequest {
        val storePayload = store?.let {
            PurchaseUploadStore(
                name = it.name,
                address = it.address?.takeIf { value -> value.isNotBlank() },
                receiptName = it.receiptName?.takeIf { value -> value.isNotBlank() },
            )
        }

        val itemPayloads = items.map { item ->
            val product = productsById[item.productId]
            val categoryName = item.productCategoryId
                ?.let { categoriesById[it]?.name }
                ?.takeIf { it.isNotBlank() }

            PurchaseUploadItem(
                lineId = item.id.toString(),
                name = item.customName?.takeIf { it.isNotBlank() }
                    ?: item.productName?.takeIf { it.isNotBlank() }
                    ?: "item_${item.id}",
                quantity = item.quantity,
                unitPrice = item.itemPrice ?: item.price,
                discount = item.discount,
                isCoupon = item.productId == 0L,
                barcode = product?.barcode?.takeIf { it.isNotBlank() },
                categoryHint = categoryName,
            )
        }

        return PurchaseUploadRequest(
            clientId = clientId?.takeIf { it.isNotBlank() },
            listId = uploadId,
            list = PurchaseUploadList(
                name = list.name,
                createdAt = date(list.createDate),
                purchaseDate = list.purchaseDate?.let(::date),
                currency = currency?.takeIf { it.isNotBlank() },
                finalTotal = list.finalTotal,
                store = storePayload,
                categories = listCategories.map {
                    PurchaseUploadCategory(name = it.name, isIncome = it.isIncome)
                },
            ),
            items = itemPayloads,
        )
    }

    private fun date(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).toString()
}
