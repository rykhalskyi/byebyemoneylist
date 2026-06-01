package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
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

    suspend fun getOrCreate(name: String, categoryIds: List<Long>): Long {
        val existing = database.storeDao().getStoreByName(name)
        if (existing != null) {
            syncCategories(existing.id, categoryIds)
            return existing.id
        }
        val id = generateId()
        database.storeDao().insertStore(StoreEntity(id = id, name = name, logoPath = null))
        syncCategories(id, categoryIds)
        return id
    }

    suspend fun insertStore(store: StoreEntity, categoryIds: List<Long> = emptyList()) {
        database.storeDao().insertStore(store)
        syncCategories(store.id, categoryIds)
    }

    suspend fun updateStore(store: StoreEntity, categoryIds: List<Long> = emptyList()) {
        database.storeDao().updateStore(store)
        syncCategories(store.id, categoryIds)
    }

    private fun syncCategories(storeId: Long, categoryIds: List<Long>) {
        database.storeDao().deleteCategoriesForStore(storeId)
        categoryIds.forEach { categoryId ->
            database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId, categoryId))
        }
    }

    suspend fun deleteStore(id: Long) {
        database.storeDao().deleteStore(id)
    }

    fun getAllStoreCategoryCrossRefs(): Flow<List<StoreCategoryCrossRef>> {
        return database.storeDao().getAllStoreCategoryCrossRefs()
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
