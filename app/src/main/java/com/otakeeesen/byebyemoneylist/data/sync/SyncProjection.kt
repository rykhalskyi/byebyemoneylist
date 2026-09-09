package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.util.toServerColorHex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.math.round

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

    // ---- Store (name + address + category links are the synced fields) ----------

    @Serializable
    private data class StoreProjection(
        val name: String,
        val address: String?,
        val categoryServerIds: List<String>
    )

    /**
     * @param categoryServerIds server ids of the local store's linked categories,
     *   resolved via the localId→serverId map built during sync; unresolvable
     *   (not yet synced) local categories are omitted.
     */
    fun storeLocal(
        store: StoreEntity,
        categoryServerIds: List<String> = emptyList()
    ): String =
        json.encodeToString(
            StoreProjection.serializer(),
            StoreProjection(
                name = store.name,
                address = normalizeAddress(store.address),
                categoryServerIds = normalizeIds(categoryServerIds)
            )
        )

    fun storeServer(store: NextcloudStoreDto): String =
        json.encodeToString(
            StoreProjection.serializer(),
            StoreProjection(
                name = store.name,
                address = normalizeAddress(store.address),
                categoryServerIds = normalizeIds(store.categoryIds)
            )
        )

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

    // ---- Shopping List (header + items digest) ------------------------------------

    /**
     * Canonical digest of one syncable list item. `isChecked` is deliberately **not**
     * part of the digest: the server's item-create endpoint cannot set it (only item
     * update can), so a full-replace push can never convey a local check state.
     * Including it would make every locally-checked list diverge forever.
     */
    @Serializable
    data class ShoppingListItemDigest(
        val productServerId: String,
        val quantity: Double,
        val price: Double?,
        val discount: Double?,
        val customName: String?,
        val position: Int
    )

    @Serializable
    private data class ShoppingListProjection(
        val name: String,
        val storeServerId: String?,
        val categoryServerIds: List<String>,
        val purchaseDate: Long?,
        val isFinished: Boolean,
        val finalTotal: Double?,
        val position: Int,
        val isRecurring: Boolean,
        val recurringPeriod: String,
        val isForwardEmpty: Boolean,
        val isSubscription: Boolean,
        val isIncome: Boolean,
        val items: List<ShoppingListItemDigest>
    )

    /**
     * @param storeServerId server id of the local list's store, resolved via the
     *   localId→serverId map; null when absent / not yet synced.
     * @param categoryServerIds server ids of the local list's linked categories
     *   (sorted, deduped); unresolvable local categories are omitted.
     * @param items the list's syncable items, each digested with its product's
     *   server id (see [shoppingListItemDigest]).
     */
    fun shoppingListLocal(
        list: ShoppingListEntity,
        storeServerId: String?,
        categoryServerIds: List<String>,
        items: List<ShoppingListItemDigest>
    ): String =
        json.encodeToString(
            ShoppingListProjection.serializer(),
            ShoppingListProjection(
                name = list.name,
                storeServerId = storeServerId?.takeIf { it.isNotBlank() },
                categoryServerIds = normalizeIds(categoryServerIds),
                purchaseDate = list.purchaseDate,
                isFinished = list.isFinished,
                finalTotal = list.finalTotal,
                position = list.position,
                isRecurring = list.isRecurring,
                recurringPeriod = list.recurringPeriod,
                isForwardEmpty = list.isForwardEmpty,
                isSubscription = list.isSubscription,
                isIncome = list.isIncome,
                items = normalizeItems(items)
            )
        )

    fun shoppingListServer(
        server: NextcloudListDto,
        items: List<ShoppingListItemDigest>
    ): String =
        json.encodeToString(
            ShoppingListProjection.serializer(),
            ShoppingListProjection(
                name = server.name,
                storeServerId = server.storeId?.takeIf { it.isNotBlank() },
                categoryServerIds = normalizeIds(server.categoryIds),
                purchaseDate = server.purchaseDate
                    ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) },
                isFinished = server.isFinished,
                finalTotal = server.finalTotal,
                position = server.position,
                isRecurring = server.isRecurring,
                recurringPeriod = server.recurringPeriod,
                isForwardEmpty = server.isForwardEmpty,
                isSubscription = server.isSubscription,
                isIncome = server.isIncome,
                items = normalizeItems(items)
            )
        )

    /**
     * Canonical form of one syncable list item. Quantity/price/discount are rounded
     * to two decimals to mirror the server's own rounding on create/update; the
     * custom name is trimmed and capped at the server's 255-char limit.
     */
    fun shoppingListItemDigest(
        productServerId: String,
        quantity: Double,
        price: Double?,
        discount: Double?,
        customName: String?,
        position: Int
    ): ShoppingListItemDigest = ShoppingListItemDigest(
        productServerId = productServerId.trim(),
        quantity = roundToTwo(quantity),
        price = price?.let { roundToTwo(it) },
        discount = discount?.let { roundToTwo(it) },
        customName = customName?.trim()?.takeIf { it.isNotEmpty() }?.take(255),
        position = position
    )

    private fun roundToTwo(value: Double): Double = round(value * 100.0) / 100.0

    private fun normalizeItems(items: List<ShoppingListItemDigest>): List<ShoppingListItemDigest> =
        items.sortedWith(
            compareBy(
                { it.productServerId },
                { it.quantity },
                { it.price ?: Double.NaN },
                { it.discount ?: Double.NaN },
                { it.customName.orEmpty() },
                { it.position }
            )
        )

    private fun normalizeBarcode(barcode: String?): String? =
        barcode?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeAddress(address: String?): String? =
        address?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeIds(ids: List<String>): List<String> =
        ids.map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .distinct()

    private fun normalizeAliases(aliases: List<String>): List<String> =
        aliases.map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .distinct()
}
