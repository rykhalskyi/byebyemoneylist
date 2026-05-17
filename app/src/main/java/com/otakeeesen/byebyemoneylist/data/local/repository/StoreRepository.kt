package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

class StoreRepository(private val database: AppDatabase) {

    val allStores: Flow<List<StoreEntity>> = database.storeDao().getAllStores()

    fun getAllStoresOnce(): List<StoreEntity> {
        return database.storeDao().getAllStoresOnce()
    }

    suspend fun getStoreByName(name: String): StoreEntity? {
        return database.storeDao().getStoreByName(name)
    }

    suspend fun getOrCreate(name: String, category: String): Long {
        val existing = database.storeDao().getStoreByName(name)
        if (existing != null) {
            if (existing.category != category) {
                database.storeDao().updateStore(existing.copy(category = category))
            }
            return existing.id
        }
        val id = generateId()
        database.storeDao().insertStore(StoreEntity(id = id, name = name, logoPath = null, category = category))
        return id
    }

    suspend fun insertStore(store: StoreEntity) {
        database.storeDao().insertStore(store)
    }

    suspend fun updateStore(store: StoreEntity) {
        database.storeDao().updateStore(store)
    }

    suspend fun deleteStore(id: Long) {
        database.storeDao().deleteStore(id)
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
