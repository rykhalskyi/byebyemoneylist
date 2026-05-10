abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun priceDao(): PriceDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun productAnalogCrossRefDao(): ProductAnalogCrossRefDao
}