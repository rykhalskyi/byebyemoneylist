package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.PriceDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductAliasDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.dao.SyncPendingDeleteDao
import com.otakeeesen.byebyemoneylist.data.local.entity.PENDING_DELETE_ENTITY_PRODUCT
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProductSyncRepository(
    private val productDao: ProductDao,
    private val productAliasDao: ProductAliasDao,
    private val categoryDao: CategoryDao,
    private val storeDao: StoreDao,
    private val priceDao: PriceDao,
    private val syncStateDao: SyncStateDao,
    private val preferencesManager: PreferencesManager,
    private val pendingDeleteDao: SyncPendingDeleteDao? = null,
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
            val plan = matcher.buildPlan(localProducts, serverProducts, aliasesByProductId)

            val categoryServerIdById = categoryDao.getAllCategoriesOnce()
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                .toMap()

            val existingStates = syncStateDao.getAll(SyncStateEntity.TYPE_PRODUCT)
                .associateBy { it.localId }
            val annotation = SyncStateResolver.annotate(
                matched = plan.matched,
                existingByLocalId = existingStates,
                entityType = SyncStateEntity.TYPE_PRODUCT,
                localId = { it.id },
                serverId = { it.id },
                toLocalJson = { product ->
                    SyncProjection.productLocal(
                        product = product,
                        categoryServerId = product.categoryId?.let { categoryServerIdById[it] },
                        aliases = aliasesByProductId[product.id].orEmpty()
                    )
                },
                toServerJson = { SyncProjection.productServer(it) }
            )
            plan.copy(
                matched = annotation.matches,
                toUpdateServer = annotation.matches
                    .filter { it.contentState == SyncContentState.LOCAL_CHANGED }
                    .map { it.local },
                toUpdateLocal = annotation.matches
                    .filter { it.contentState == SyncContentState.SERVER_CHANGED }
                    .map { it.server },
                conflicts = annotation.matches
                    .filter { it.contentState == SyncContentState.CONFLICT }
                    .map { SyncConflict(it) }
            )
        }
    }

    override suspend fun executeSyncPlan(
        plan: SyncPlan<ProductEntity, NextcloudProductDto>,
        pushItems: List<ProductEntity>,
        pullItems: List<NextcloudProductDto>,
        linkedPairs: List<Pair<ProductEntity, NextcloudProductDto>>,
        updateToServer: List<ProductEntity>,
        updateToLocal: List<NextcloudProductDto>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            val now = System.currentTimeMillis()

            // 0. Drain pending deletes for products
            if (pendingDeleteDao != null) {
                for (pending in pendingDeleteDao.getAllByEntity(PENDING_DELETE_ENTITY_PRODUCT)) {
                    apiClient.deleteProduct(url, user, pass, pending.serverId).getOrThrow()
                    pendingDeleteDao.deleteById(pending.id)
                }
            }

            val aliasesByProductId = productAliasDao.getAllAliasesOnce()
                .groupBy { it.productId }
                .mapValues { (_, aliases) -> aliases.map { it.aliasName } }
            val categoryServerIdById = categoryDao.getAllCategoriesOnce()
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                .toMap()

            // 1. Persist the matched server ids locally (matched by barcode/name or manually) and baseline sync state.
            for ((local, server) in linkedPairs) {
                if (server.id != null) {
                    productDao.updateServerId(local.id, server.id)
                    syncStateDao.upsert(
                        SyncStateEntity(
                            entityType = SyncStateEntity.TYPE_PRODUCT,
                            localId = local.id,
                            serverId = server.id,
                            baseSnapshot = SyncProjection.productLocal(
                                product = local,
                                categoryServerId = local.categoryId?.let { categoryServerIdById[it] },
                                aliases = aliasesByProductId[local.id].orEmpty()
                            ),
                            lastSyncAt = now
                        )
                    )
                }
            }

            // 2. Download missing products from Server -> Client DB using autoincrement primary keys.
            for (serverProduct in pullItems) {
                val serverId = serverProduct.id ?: continue
                if (productDao.getByServerId(serverId) != null) continue

                val localCategoryId = serverProduct.categoryId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { categoryDao.getByServerId(it)?.id }

                val newId = productDao.insertProduct(
                    ProductEntity(
                        id = 0L,
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
                                productId = newId,
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
            for (local in pushItems) {
                val serverCategoryId = local.categoryId
                    ?.let { categoryDao.getCategoryById(it)?.serverId }
                    ?.takeIf { it.isNotBlank() }
                val created = apiClient.createProduct(
                    url, user, pass,
                    buildProductSyncRequest(
                        local = local,
                        categoryServerId = serverCategoryId,
                        aliases = aliasesByProductId[local.id].orEmpty()
                    )
                ).getOrThrow()
                created.id?.let { productDao.updateServerId(local.id, it) }
            }

            // 4. Push pending local edits (name / barcode / category / aliases / flags) as PUTs.
            val categoryServerIdByLocalId = categoryDao.getAllCategoriesOnce()
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                .toMap()
            for (local in updateToServer) {
                val serverId = local.serverId?.takeIf { it.isNotBlank() } ?: continue
                val serverCategoryId = local.categoryId?.let { categoryServerIdByLocalId[it] }
                apiClient.updateProduct(
                    url, user, pass, serverId,
                    buildProductSyncRequest(
                        local = local,
                        categoryServerId = serverCategoryId,
                        aliases = aliasesByProductId[local.id].orEmpty()
                    )
                ).getOrThrow()
                syncStateDao.upsert(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_PRODUCT,
                        localId = local.id,
                        serverId = serverId,
                        baseSnapshot = SyncProjection.productLocal(
                            product = local,
                            categoryServerId = serverCategoryId,
                            aliases = aliasesByProductId[local.id].orEmpty()
                        ),
                        lastSyncAt = now
                    )
                )
            }

            // 5. Pull pending remote edits: overwrite shared fields only (name / barcode /
            //    category / aliases / flags); picturePath, status and changedAt stay local.
            //    Server aliases are the canonical shared set → full replace.
            for (dto in updateToLocal) {
                val serverId = dto.id ?: continue
                val localProduct = productDao.getByServerId(serverId) ?: continue
                val localCategoryId = dto.categoryId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { categoryDao.getByServerId(it)?.id }
                productDao.updateFromServer(
                    id = localProduct.id,
                    name = dto.name,
                    barcode = dto.barcode ?: "",
                    categoryId = localCategoryId,
                    isFavorite = dto.isFavorite,
                    isSubscription = dto.isSubscription,
                    isIncome = dto.isIncome
                )
                // Server aliases are the canonical shared set → full replace. The local
                // `storeId` scope is not round-trippable, so it is preserved best-effort:
                // an alias name that already existed locally keeps its store mapping.
                val existingStoreIdByAlias = productAliasDao.getAliasesByProductId(localProduct.id)
                    .mapNotNull { alias ->
                        alias.aliasName.trim().lowercase()
                            .takeIf { it.isNotEmpty() }
                            ?.let { it to alias.storeId }
                    }
                    .toMap()
                productAliasDao.deleteByProductId(localProduct.id)
                dto.aliases
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { alias ->
                        productAliasDao.insertAlias(
                            ProductAliasEntity(
                                id = 0,
                                productId = localProduct.id,
                                aliasName = alias,
                                storeId = existingStoreIdByAlias[alias.lowercase()]
                            )
                        )
                    }
                syncStateDao.upsert(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_PRODUCT,
                        localId = localProduct.id,
                        serverId = serverId,
                        baseSnapshot = SyncProjection.productServer(dto),
                        lastSyncAt = now
                    )
                )
            }

            // 6. Push product price records (Android → server, idempotent). The server
            //    keys each record by (product, store) so a re-push updates instead of
            //    duplicating. Only prices whose product and (if any) store are already
            //    synced can be represented; the rest are skipped this round.
            pushPrices(url, user, pass)

            true
        }
    }

    /**
     * Pushes the local price history for synced products to the server in a single
     * batch. Products/stores without a `serverId` yet (or price records with no date)
     * are skipped and picked up on a later run once their refs are synced.
     */
    private suspend fun pushPrices(url: String, user: String, pass: String) {
        val localProductServerIdByLocalId = productDao.getAllProductsOnce()
            .mapNotNull { p -> p.serverId?.takeIf { it.isNotBlank() }?.let { p.id to it } }
            .toMap()
        if (localProductServerIdByLocalId.isEmpty()) return
        val localStoreServerIdByLocalId = storeDao.getAllStoresOnce()
            .mapNotNull { s -> s.serverId?.takeIf { it.isNotBlank() }?.let { s.id to it } }
            .toMap()

        val requests = priceDao.getAllPricesOnce().mapNotNull { price ->
            val serverProductId = localProductServerIdByLocalId[price.productId] ?: return@mapNotNull null
            if (price.storeId != null && localStoreServerIdByLocalId[price.storeId] == null) {
                return@mapNotNull null
            }
            val date = NextcloudSyncDates.formatEpochToIso(price.date) ?: return@mapNotNull null
            NextcloudProductPriceCreateRequest(
                productId = serverProductId,
                storeId = price.storeId?.let { localStoreServerIdByLocalId[it] },
                value = price.value,
                date = date
            )
        }
        if (requests.isNotEmpty()) {
            apiClient.upsertProductPrices(url, user, pass, requests).getOrThrow()
        }
    }
}

/**
 * Builds the full product payload shared by create (POST) and update (PUT) pushes.
 * The barcode is trimmed and dropped when blank so an empty local value clears the
 * server field (server treats blank/null as no barcode).
 */
internal fun buildProductSyncRequest(
    local: ProductEntity,
    categoryServerId: String?,
    aliases: List<String>
): NextcloudProductCreateRequest = NextcloudProductCreateRequest(
    name = local.name,
    categoryId = categoryServerId?.takeIf { it.isNotBlank() },
    barcode = local.barcode.trim().takeIf { it.isNotEmpty() },
    aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() },
    isFavorite = local.isFavorite,
    isSubscription = local.isSubscription,
    isIncome = local.isIncome
)
