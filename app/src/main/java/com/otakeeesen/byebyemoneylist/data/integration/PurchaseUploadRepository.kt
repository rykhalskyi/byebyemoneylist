package com.otakeeesen.byebyemoneylist.data.integration

import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.listKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Builds and uploads a finished purchase list to the Nextcloud import inbox.
 * One-way only: the client never reads remote state and never maps entities.
 */
class PurchaseUploadRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val apiClient: PurchaseUploadApiClient,
) {
    fun isConfigured(): Boolean = preferencesManager.isNextcloudConfigured()

    suspend fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<Unit> = apiClient.testConnection(serverUrl, username, password)

    suspend fun uploadList(listId: Long): PurchaseUploadResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext PurchaseUploadResult.NotConfigured
        }

        val dao = database.shoppingListDao()
        val list = dao.getShoppingListById(listId)
            ?: return@withContext PurchaseUploadResult.NotEligible
        if (!isEligible(list)) {
            return@withContext PurchaseUploadResult.NotEligible
        }

        val uploadId = dao.getUploadId(list.id) ?: UUID.randomUUID().toString().also {
            dao.setUploadId(list.id, it)
        }

        val store = list.storeId?.let { database.storeDao().getStoreById(it) }
        val allCategories = database.categoryDao().getAllCategoriesOnce()
        val categoriesById = allCategories.associateBy { it.id }
        val listCategoryIds = dao.getCategoriesForShoppingListSync(list.id)
        val listCategories = listCategoryIds.mapNotNull { categoriesById[it] }
        val items = dao.getItemsWithProductForListsSync(listOf(list.id))
        val products = database.productDao().getProductsByIds(
            items.map { it.productId }.filter { it > 0L }.distinct(),
        ).associateBy { it.id }

        val payload = PurchaseUploadMapper.build(
            list = list,
            uploadId = uploadId,
            clientId = preferencesManager.getSyncUserId(),
            store = store,
            listCategories = listCategories,
            items = items,
            productsById = products,
            categoriesById = categoriesById,
            currency = preferencesManager.getCurrencySymbol(),
        )

        try {
            val result = apiClient.upload(
                serverUrl = preferencesManager.getNextcloudUrl(),
                username = preferencesManager.getNextcloudUsername(),
                password = preferencesManager.getNextcloudPassword(),
                payload = payload,
            ).getOrThrow()

            val sentAt = System.currentTimeMillis()
            dao.updateLastUploadedAt(list.id, sentAt)
            PurchaseUploadResult.Success(sentAt, result.alreadyImported)
        } catch (e: PurchaseUploadAuthException) {
            PurchaseUploadResult.AuthError
        } catch (e: PurchaseUploadAppNotFoundException) {
            PurchaseUploadResult.AppNotFound
        } catch (e: PurchaseUploadRejectedException) {
            PurchaseUploadResult.Rejected(e.message ?: "Upload rejected")
        } catch (e: PurchaseUploadException) {
            PurchaseUploadResult.NetworkError
        } catch (e: Exception) {
            PurchaseUploadResult.NetworkError
        }
    }

    private fun isEligible(list: ShoppingListEntity): Boolean =
        list.isFinished &&
            list.listKind == ListKind.PURCHASE &&
            !list.isIncome &&
            !list.isSubscription
}
