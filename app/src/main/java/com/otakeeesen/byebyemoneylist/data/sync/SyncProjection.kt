package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.util.toServerColorHex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Canonical projections of the *shared, synced* fields for each entity type,
 * normalised so that a local row and its server row with the same content produce
 * the exact same JSON string. References are expressed in the server domain
 * (`serverId`s). Change detection = compare SHA-256 of these projections against
 * the last-synced base snapshot.
 */
object SyncProjection {

    private val json = Json { encodeDefaults = true }

    /** SHA-256 hex digest of a canonical projection JSON string. */
    fun hash(canonicalJson: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ---- Store (server is name-only) -------------------------------------------

    @Serializable
    private data class StoreProjection(val name: String)

    fun storeLocal(store: StoreEntity): String =
        json.encodeToString(StoreProjection.serializer(), StoreProjection(name = store.name))

    fun storeServer(store: NextcloudStoreDto): String =
        json.encodeToString(StoreProjection.serializer(), StoreProjection(name = store.name))

    // ---- Category ---------------------------------------------------------------

    @Serializable
    private data class CategoryProjection(
        val name: String,
        val income: Boolean,
        val colorHex: String?,
        val emoji: String?,
        val parentServerId: String?
    )

    /**
     * @param parentServerId server id of the local category's parent, resolved via the
     *   localId→serverId map built during sync; null if the parent is not yet synced.
     */
    fun categoryLocal(category: CategoryEntity, parentServerId: String?): String =
        json.encodeToString(
            CategoryProjection.serializer(),
            CategoryProjection(
                name = category.name,
                income = category.isIncome,
                colorHex = toServerColorHex(category.color),
                emoji = category.emoji,
                parentServerId = parentServerId?.takeIf { it.isNotBlank() }
            )
        )

    fun categoryServer(category: NextcloudCategoryDto): String =
        json.encodeToString(
            CategoryProjection.serializer(),
            CategoryProjection(
                name = category.name,
                income = category.income,
                colorHex = toServerColorHex(category.color),
                emoji = category.emoji,
                parentServerId = category.parentId?.takeIf { it.isNotBlank() }
            )
        )

    // ---- Product ----------------------------------------------------------------

    @Serializable
    private data class ProductProjection(
        val name: String,
        val barcode: String?,
        val categoryServerId: String?,
        val aliases: List<String>,
        val isFavorite: Boolean,
        val isSubscription: Boolean,
        val isIncome: Boolean
    )

    /**
     * @param categoryServerId server id of the local product's category, resolved via the
     *   localId→serverId map; null if the category is not yet synced.
     * @param aliases local alias names for this product.
     */
    fun productLocal(
        product: ProductEntity,
        categoryServerId: String?,
        aliases: List<String>
    ): String =
        json.encodeToString(
            ProductProjection.serializer(),
            ProductProjection(
                name = product.name,
                barcode = normalizeBarcode(product.barcode),
                categoryServerId = categoryServerId?.takeIf { it.isNotBlank() },
                aliases = normalizeAliases(aliases),
                isFavorite = product.isFavorite,
                isSubscription = product.isSubscription,
                isIncome = product.isIncome
            )
        )

    fun productServer(product: NextcloudProductDto): String =
        json.encodeToString(
            ProductProjection.serializer(),
            ProductProjection(
                name = product.name,
                barcode = normalizeBarcode(product.barcode),
                categoryServerId = product.categoryId?.takeIf { it.isNotBlank() },
                aliases = normalizeAliases(product.aliases),
                isFavorite = product.isFavorite,
                isSubscription = product.isSubscription,
                isIncome = product.isIncome
            )
        )

    private fun normalizeBarcode(barcode: String?): String? =
        barcode?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeAliases(aliases: List<String>): List<String> =
        aliases.map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .distinct()
}
