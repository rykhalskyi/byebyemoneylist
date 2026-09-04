package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductAliasDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductSyncRepository(
    private val productDao: ProductDao,
    private val productAliasDao: ProductAliasDao,
    private val categoryDao: CategoryDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
    private val matcher: ProductSyncMatcher = ProductSyncMatcher()
) : SyncRepository<ProductEntity, NextcloudProductDto> {

    override suspend fun generateSyncPlan(
        useLlm: Boolean,
        llmCall: (suspend (prompt: String) -> String?)?,
        onPhase: (SyncPhase) -> Unit
    ): Result<SyncPlan<ProductEntity, NextcloudProductDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                throw Exception("Nextcloud credentials are not fully configured in settings.")
            }

            onPhase(SyncPhase.FETCHING)
            val serverProducts = apiClient.fetchProducts(url, user, pass, type = "all").getOrThrow()
            val localProducts = productDao.getAllProductsOnce()
            val aliasesByProductId = productAliasDao.getAllAliasesOnce()
                .groupBy { it.productId }
                .mapValues { (_, aliases) -> aliases.map { it.aliasName } }
            matcher.buildPlan(localProducts, serverProducts, aliasesByProductId)
        }
    }

    override suspend fun executeSyncPlan(
        plan: SyncPlan<ProductEntity, NextcloudProductDto>,
        pushItems: List<ProductEntity>,
        pullItems: List<NextcloudProductDto>,
        linkedPairs: List<Pair<ProductEntity, NextcloudProductDto>>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            // 1. Persist the matched server ids locally (matched by barcode/name or manually).
            for ((local, server) in linkedPairs) {
                server.id?.let { productDao.updateServerId(local.id, it) }
            }

            // 2. Download missing products from Server -> Client DB. Local ids are generated
            //    without collisions; the server id is stored so future syncs re-link them.
            val allLocal = productDao.getAllProductsOnce()
            val localIds = allLocal.map { it.id }.toMutableSet()
            var nextId = (localIds.maxOrNull() ?: 0L) + 1

            for (serverProduct in pullItems) {
                val serverId = serverProduct.id ?: continue
                if (productDao.getByServerId(serverId) != null) continue
                while (nextId in localIds) nextId++
                localIds.add(nextId)

                val localCategoryId = serverProduct.categoryId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { categoryDao.getByServerId(it)?.id }

                productDao.insertProduct(
                    ProductEntity(
                        id = nextId,
                        name = serverProduct.name,
                        barcode = serverProduct.barcode ?: "",
                        picturePath = null,
                        categoryId = localCategoryId,
                        status = serverProduct.status ?: "reviewed",
                        changedAt = System.currentTimeMillis(),
                        isSubscription = serverProduct.isSubscription,
                        isFavorite = serverProduct.isFavorite,
                        isIncome = serverProduct.isIncome,
                        serverId = serverId
                    )
                )
                serverProduct.aliases
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { alias ->
                        productAliasDao.insertAlias(
                            ProductAliasEntity(
                                id = 0,
                                productId = nextId,
                                aliasName = alias,
                                storeId = null
                            )
                        )
                    }
            }

            // 3. Upload missing products from Client -> Server. The server has no batch
            //    endpoint, so products are created one by one; each returned id is stored
            //    locally so future syncs re-link them. The local category is mapped to the
            //    server category uuid via the category `serverId` populated by the category
            //    sync that always runs before this group.
            val aliasesByProductId = productAliasDao.getAllAliasesOnce()
                .groupBy { it.productId }
                .mapValues { (_, aliases) -> aliases.map { it.aliasName } }

            for (local in pushItems) {
                val serverCategoryId = local.categoryId
                    ?.let { categoryDao.getCategoryById(it)?.serverId }
                    ?.takeIf { it.isNotBlank() }
                val created = apiClient.createProduct(
                    url, user, pass,
                    NextcloudProductCreateRequest(
                        name = local.name,
                        categoryId = serverCategoryId,
                        barcode = local.barcode.trim().takeIf { it.isNotEmpty() },
                        aliases = aliasesByProductId[local.id].orEmpty(),
                        isFavorite = local.isFavorite,
                        isSubscription = local.isSubscription,
                        isIncome = local.isIncome
                    )
                ).getOrThrow()
                created.id?.let { productDao.updateServerId(local.id, it) }
            }

            true
        }
    }
}
