package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.PriceDao
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PriceRepository(private val database: AppDatabase) {

    private val priceDao: PriceDao = database.priceDao()

    fun getPricesForProduct(productId: Long) = priceDao.getPricesForProduct(productId)

    suspend fun getLatestPrice(productId: Long, storeId: Long?): PriceEntity? {
        return withContext(Dispatchers.IO) {
            when {
                storeId != null -> {
                    // Try store-specific price first
                    priceDao.getLatestPriceForProductAtStore(productId, storeId)
                        ?: priceDao.getLatestPriceForProduct(productId)
                }
                else -> {
                    priceDao.getLatestPriceForProduct(productId)
                }
            }
        }
    }

    suspend fun upsertPriceForProduct(
        productId: Long,
        storeId: Long?,
        value: Double
    ): Long {
        return withContext(Dispatchers.IO) {
            upsertPriceForProductSync(productId, storeId, value)
        }
    }

    /**
     * Blocking variant for use inside an already-open Room transaction. Must not be
     * called from another dispatcher while a transaction is active.
     */
    fun upsertPriceForProductSync(
        productId: Long,
        storeId: Long?,
        value: Double
    ): Long {
        val now = System.currentTimeMillis()
        // Check if there's an existing price for this product+store combination
        val existing = if (storeId != null) {
            priceDao.getLatestPriceForProductAtStore(productId, storeId)
        } else {
            priceDao.getLatestPriceForProduct(productId)
        }

        if (existing != null) {
            // Update existing price entry (bump changedAt so incremental sync picks it up)
            val updated = existing.copy(value = value, date = now, changedAt = now)
            priceDao.updatePrice(updated)
            return existing.id
        } else {
            // Create new price entry
            val id = generateId()
            val priceEntity = PriceEntity(
                id = id,
                productId = productId,
                storeId = storeId,
                value = value,
                date = now
            )
            priceDao.insertPrice(priceEntity)
            return id
        }
    }

    private fun generateId(): Long = (System.currentTimeMillis() shl 20) or (java.security.SecureRandom().nextLong() and 0xFFFFF)
}