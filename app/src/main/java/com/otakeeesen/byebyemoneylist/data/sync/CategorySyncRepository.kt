package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategorySyncRepository(
    private val categoryDao: CategoryDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
    private val matcher: MultiLanguageCategoryMatcher = MultiLanguageCategoryMatcher()
) {

    suspend fun generateSyncPlan(): Result<CategorySyncPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                throw Exception("Nextcloud credentials are not fully configured in settings.")
            }

            val serverCategories = apiClient.fetchCategories(url, user, pass).getOrThrow()
            val localCategories = categoryDao.getAllCategoriesOnce()

            val basePlan = matcher.buildSyncPlan(localCategories, serverCategories)
            basePlan
        }
    }

    suspend fun executeSyncPlan(
        plan: CategorySyncPlan,
        pushCategories: List<CategoryEntity>,
        pullCategories: List<NextcloudCategoryDto>,
        linkedPairs: List<Pair<CategoryEntity, NextcloudCategoryDto>>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            // 1. Save matched serverId updates locally
            for ((local, server) in linkedPairs) {
                if (server.id != null) {
                    categoryDao.updateServerId(local.id, server.id)
                }
            }

            // 2. Download missing categories from Server -> Client DB
            for (serverCat in pullCategories) {
                if (serverCat.id != null) {
                    val existing = categoryDao.getByServerId(serverCat.id)
                    if (existing == null) {
                        val newLocal = CategoryEntity(
                            name = serverCat.name,
                            color = serverCat.color ?: "#FF6B6B",
                            emoji = serverCat.emoji,
                            isIncome = serverCat.income,
                            serverId = serverCat.id
                        )
                        categoryDao.insertCategory(newLocal)
                    }
                }
            }

            // 3. Batch Upload missing categories from Client -> Server
            if (pushCategories.isNotEmpty()) {
                val dtoList = pushCategories.map { cat ->
                    NextcloudCategoryDto(
                        name = cat.name,
                        color = cat.color,
                        emoji = cat.emoji,
                        income = cat.isIncome,
                        tempId = cat.id.toString()
                    )
                }

                val createdDtos = apiClient.createCategoryBatch(url, user, pass, dtoList).getOrThrow()

                // Update local Categories with assigned server UUIDs returned by Nextcloud
                for (created in createdDtos) {
                    val tempIdLong = created.tempId?.toLongOrNull()
                    if (tempIdLong != null && created.id != null) {
                        categoryDao.updateServerId(tempIdLong, created.id)
                    }
                }
            }

            true
        }
    }
}
