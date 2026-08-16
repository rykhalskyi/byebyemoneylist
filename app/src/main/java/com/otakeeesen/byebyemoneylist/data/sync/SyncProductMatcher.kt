package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository

data class MatchedSyncItem(
    val item: SyncItemDto,
    val productId: Long,
)

class SyncProductMatcher(
    private val database: AppDatabase,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
) {
    private var idCounter = 0L

    suspend fun matchItems(items: List<SyncItemDto>, storeId: Long?): List<MatchedSyncItem> {
        val localProducts = database.productDao().getAllProductsOnce()
        val result = mutableListOf<MatchedSyncItem>()
        for (item in items) {
            result.add(matchSingleItem(item, storeId, localProducts))
        }
        return result
    }

    private suspend fun matchSingleItem(
        item: SyncItemDto,
        storeId: Long?,
        localProducts: List<ProductEntity>,
    ): MatchedSyncItem {
        val name = item.name

        val aliasMatch = database.productAliasDao().findBestMatch(name, storeId)
        if (aliasMatch != null) {
            return MatchedSyncItem(item, aliasMatch.productId)
        }

        for (alias in item.aliases) {
            val incomingAliasMatch = database.productAliasDao().findBestMatch(alias, storeId)
            if (incomingAliasMatch != null) {
                database.productAliasDao().insertAlias(
                    ProductAliasEntity(
                        id = nextId(),
                        productId = incomingAliasMatch.productId,
                        aliasName = name,
                        storeId = storeId
                    )
                )
                return MatchedSyncItem(item, incomingAliasMatch.productId)
            }
        }

        val exactMatch = localProducts.find { it.name.equals(name, ignoreCase = true) }
        if (exactMatch != null) {
            database.productAliasDao().insertAlias(
                ProductAliasEntity(
                    id = nextId(),
                    productId = exactMatch.id,
                    aliasName = name,
                    storeId = storeId
                )
            )
            return MatchedSyncItem(item, exactMatch.id)
        }

        val generalCategoryId = categoryRepository.getOrCreate("General")
        val newPid = productRepository.createProduct(
            name = name,
            categoryId = generalCategoryId,
            status = "added"
        )
        database.productAliasDao().insertAlias(
            ProductAliasEntity(
                id = nextId(),
                productId = newPid,
                aliasName = name,
                storeId = storeId
            )
        )
        return MatchedSyncItem(item, newPid)
    }

    private fun nextId(): Long {
        idCounter++
        return (System.currentTimeMillis() shl 20) or (idCounter and 0xFFFFF)
    }
}
