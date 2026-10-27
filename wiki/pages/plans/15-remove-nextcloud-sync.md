---
created: 2026-10-27
type: plan
status: implemented
tags: [nextcloud, sync, removal, cleanup, room, migration]
related:
  - "../specs/02-nextcloud-sync-redesign.md"
  - "../plans/06-nextcloud-sync-redesign.md"
  - "../plans/07-sync-delta-git-model.md"
  - "../plans/08-shopping-list-sync.md"
  - "../plans/09-sync-enhancements.md"
summary: Remove the Nextcloud OCS sync feature end-to-end (UI, API client, matchers, repositories, sync_state/pending-delete bookkeeping, serverId columns, build flag, strings, tests) and keep the separate folder-based list sharing untouched.
---

# 15. Remove Nextcloud Sync — Implementation Plan

Removes the experimental Nextcloud OCS mirror introduced by plans 06–09. Goal: no
Nextcloud configuration UI, no OCS client, no matchers/repositories, no `serverId`
or `sync_state` bookkeeping, and a smaller database — while preserving all local
app behaviour and the **separate** folder-based list sharing feature.

## Decisions

- **D1 — Scope = Nextcloud OCS sync only.** `ListSyncEngine`, `SyncFolderRepository`,
  `SyncDto`, `SyncProductMatcher`, and the `shopping_lists` columns `isShared`,
  `syncId`, `lastSyncTimestamp`, `lastModifiedAt` back the unrelated "share lists to
  a folder" feature and **stay**. Do not delete anything under that flow.
- **D2 — Full schema removal, not dormant columns.** Drop `serverId` from
  `categories`, `stores`, `products`, `shopping_lists`; drop the `sync_state` and
  `sync_pending_deletes` tables. Room version `30 → 31`.
- **D3 — No server-side work.** The Nextcloud app/server is untouched; only this
  client stops talking to it. Server data is not deleted.
- **D4 — Keep shared dependencies.** `okhttp`, `kotlinx.serialization`, and
  `encryptedPrefs` are used by the LLM/scanner/share features and remain.
- **D5 — Keep `util/ProductMatcher`.** Used by catalog product matching, not only
  sync.
- **D6 — Orphaned prefs are acceptable.** `nextcloud_url`, `nextcloud_username`, and
  `nextcloud_password` may remain on existing installs; removing the accessor code
  is enough. Optional one-time cleanup is a follow-up.

## Inventory

### Delete — UI

- `ui/components/settings/NextcloudSyncSettingsScreen.kt`
- `ui/components/settings/NextcloudSyncViewModel.kt`
- `ui/components/settings/CategorySyncScreen.kt`
- `ui/components/settings/StoreSyncScreen.kt`
- `ui/components/settings/ProductSyncScreen.kt`
- `ui/components/settings/ShoppingListSyncScreen.kt`
- `ui/components/settings/SyncPlanScreen.kt`

### Delete — data/API

- `data/sync/NextcloudApiClient.kt`
- `data/sync/NextcloudCategoryDto.kt`
- `data/sync/NextcloudStoreDto.kt`
- `data/sync/NextcloudProductDto.kt`
- `data/sync/NextcloudListDto.kt`
- `data/sync/NextcloudSyncDates.kt`
- `data/sync/CategorySyncRepository.kt`
- `data/sync/StoreSyncRepository.kt`
- `data/sync/ProductSyncRepository.kt`
- `data/sync/ShoppingListsSyncRepository.kt`
- `data/sync/MultiLanguageCategoryMatcher.kt`
- `data/sync/StoreSyncMatcher.kt`
- `data/sync/ProductSyncMatcher.kt`
- `data/sync/SyncMatcher.kt`
- `data/sync/SyncRepository.kt`
- `data/sync/SyncCoordinator.kt`
- `data/sync/SyncProjection.kt`
- `data/sync/SyncStateResolver.kt`
- `data/sync/SyncStateDao.kt`
- `data/sync/model/SyncPlan.kt`
- `data/sync/model/SyncStateEntity.kt`
- `data/local/dao/SyncPendingDeleteDao.kt`
- `data/local/entity/SyncPendingDeleteEntity.kt`

### Keep — folder-based sharing (do not touch)

- `data/sync/ListSyncEngine.kt`
- `data/sync/SyncFolderRepository.kt`
- `data/sync/SyncDto.kt`
- `data/sync/SyncProductMatcher.kt`
- `ShoppingListEntity`: `isShared`, `syncId`, `lastSyncTimestamp`, `lastModifiedAt`
- `ShoppingListDao`: `getShoppingListBySyncId`, `updateSyncTimestamp`,
  `updateModifiedAt`, `markAsShared`, `markAsUnshared`, `getSharedListsSync`
- `ByeByeMoneyApplication.syncFolderRepository` / `listSyncEngine`
- `CLOUD_SHARE_ENABLED`

> After deletion the `data/sync` package contains only share files. Renaming it to
> `data/share` is a nice-to-have follow-up, not part of this plan.

### Delete — tests

JVM (`app/src/test/.../data/sync/` unless noted):

- `CategoryHierarchyPushTest.kt`
- `CategoryUpdatePushTest.kt`
- `ConflictResolutionTest.kt`
- `MultiLanguageCategoryMatcherTest.kt`
- `NextcloudSyncDatesTest.kt`
- `ProductSyncMatcherTest.kt`
- `ProductUpdatePushTest.kt`
- `PricePushRequestsTest.kt`
- `ShoppingListProjectionTest.kt`
- `ShoppingListSyncPlanTest.kt`
- `StoreSyncMatcherTest.kt`
- `StoreUpdatePullTest.kt`
- `StoreUpdatePushTest.kt`
- `SyncProjectionTest.kt`
- `SyncStateResolverTest.kt`
- `ui/components/settings/SyncGroupCountsTest.kt`

Instrumented:

- `app/src/androidTest/.../SyncUpdateDaoTest.kt`

Keep `ProductMatcherTest`, `AgentManagerTest`, `AgentQueryExecutorTest`,
`MigrationTest` (modified), and all other unrelated tests.

### Modify

- `data/local/AppDatabase.kt`
- `data/local/entity/{CategoryEntity,StoreEntity,ProductEntity,ShoppingListEntity}.kt`
- `data/local/dao/{CategoryDao,StoreDao,ProductDao,ShoppingListDao,PriceDao}.kt`
- `data/local/repository/{CategoryRepository,StoreRepository,ProductRepository,ShoppingListRepository}.kt`
- `data/local/PreferencesManager.kt`
- `ui/components/main/MainScreen.kt`
- `ui/navigation/Screen.kt`
- `ui/components/settings/SettingsScreen.kt`
- `app/build.gradle.kts`
- `app/src/main/res/values{,-de,-uk}/strings.xml`
- `app/src/androidTest/.../MigrationTest.kt`
- `README.md`
- `local.properties` (gitignored; remove the flag locally)

---

## Phase 1 — Remove the UI and entry points

1. Delete the seven UI files listed above.
2. `MainScreen.kt`: remove the whole `navigation(NEXTCLOUD_SYNC_GRAPH_ROUTE) { … }`
   block and the imports for `NEXTCLOUD_SYNC_GRAPH_ROUTE`,
   `NextcloudSyncSettingsScreen`, `NextcloudSyncViewModel`, `CategorySyncScreen`,
   `StoreSyncScreen`, `ProductSyncScreen`, `ShoppingListSyncScreen`. Drop the
   `onNavigateToNextcloudSyncSettings` argument from the `SettingsScreen(...)` call.
3. `SettingsScreen.kt`: remove the `onNavigateToNextcloudSyncSettings` parameter and
   the `if (BuildConfig.NEXTCLOUD_SYNC_ENABLED) { … }` list item (`SettingsScreen.kt:157-166`).
4. `Screen.kt`: remove `NEXTCLOUD_SYNC_GRAPH_ROUTE`, the five sync `object`s
   (`NextcloudSyncSettings`, `CategorySync`, `StoreSync`, `ProductSync`,
   `ShoppingListSync`), and the now-unused `Icons.Default.Sync` import.
5. `app/build.gradle.kts`: remove `nextcloudSyncEnabled` and the
   `NEXTCLOUD_SYNC_ENABLED` `buildConfigField`.
6. Remove the `NEXTCLOUD_SYNC_ENABLED` line from `README.md` and `local.properties`.
7. Remove sync-only strings from all three locales:
   - all keys prefixed `nextcloud_`, `category_sync_`, `store_sync_`,
     `product_sync_`, `shopping_list_sync_` (including the
     `nextcloud_sync_row_updates` / `nextcloud_sync_row_conflicts` plurals and the
     unused `nextcloud_sync_shopping_lists_not_synced/_counts/_error`),
   - `cd_unlink_match`, `cd_collapse_section`, `cd_expand_section`.
   - **Keep** shared keys `categories`, `stores`, `products`, `search`, `cd_go_back`.

**Checkpoint:** `./gradlew assembleDebug` — should fail only on the still-present
data layer, confirming the UI cut is complete.

## Phase 2 — Remove the data/API layer

1. Delete the data/API and model files listed under "Delete — data/API".
2. `ByeByeMoneyApplication.kt` should need no change; verify it still resolves only
   `ListSyncEngine`, `SyncFolderRepository`, `SyncProductMatcher`.
3. Verify nothing outside the deleted files references
   `NextcloudApiClient`, `Nextcloud*Dto`, `SyncPlan`, `SyncContentState`,
   `SyncMatch`, `SyncConflict`, `NextcloudSyncDates`, `SyncProjection`,
   `SyncStateResolver`, `SyncStateDao`, `SyncStateEntity`, `SyncPendingDelete*`.

## Phase 3 — Remove schema and local bookkeeping

### 3.1 Entities (`serverId` removal)

- `CategoryEntity`: drop `val serverId: String? = null`.
- `StoreEntity`: drop `serverId`.
- `ProductEntity`: drop `serverId` from the data class and from the no-arg
  constructor (`ProductEntity.kt:27`).
- `ShoppingListEntity`: drop `serverId`; keep the share fields.

### 3.2 DAOs

Remove methods that existed only for the mirror:

- `CategoryDao`: `getByServerId`, `updateServerId`, `updateFromServer`.
- `StoreDao`: `getByServerId`, `updateServerId`, `updateSharedFromServer`,
  `getAllStoreCategoryCrossRefsOnce`.
- `ProductDao`: `getByServerId`, `updateServerId`, `updateFromServer`.
- `ShoppingListDao`: `getByServerId`, `updateServerId`, `getItemsForListsSync`
  (only consumer is the deleted `ShoppingListsSyncRepository`).
- `PriceDao`: `getPricesChangedSince` (only consumer is the deleted price push).

Delete `SyncPendingDeleteDao.kt` and `SyncStateDao.kt`.

### 3.3 Local repositories

- `CategoryRepository.deleteCategory`: remove the pending-delete insert and the
  `syncStateDao().delete(...)` call (`CategoryRepository.kt:51-59`); keep the
  actual delete.
- `StoreRepository.deleteStore`: same (`StoreRepository.kt:89-97`).
- `ProductRepository.deleteProduct`: same (`ProductRepository.kt:94-102`).
- `ShoppingListRepository.deleteShoppingList`: remove the pending-delete block
  (`ShoppingListRepository.kt:519-527`); keep the delete.
- `ShoppingListRepository.getItemsForListsSync`: remove (only used by the deleted
  Nextcloud shopping-list repository).
- Remove now-unused imports (`SyncPendingDeleteEntity`, `PENDING_DELETE_ENTITY_*`,
  `SyncStateEntity`, `PENDING_DELETE_ENTITY_SHOPPING_LIST`).

### 3.4 `AppDatabase.kt`

1. Remove `SyncPendingDeleteEntity::class` and `SyncStateEntity::class` from
   `entities`.
2. Remove `syncPendingDeleteDao()` and `syncStateDao()`.
3. Bump `version = 30` → `31`.
4. Add `MIGRATION_30_TO_31` and register it in `addMigrations(...)`.

**Migration 30 → 31** (drop `serverId` columns + sync tables, preserving data and
constraints):

```sql
CREATE TABLE `categories_new` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `color` TEXT NOT NULL,
  `parentId` INTEGER,
  `isIncome` INTEGER NOT NULL,
  `emoji` TEXT
);
INSERT INTO `categories_new` (`id`,`name`,`color`,`parentId`,`isIncome`,`emoji`)
SELECT `id`,`name`,`color`,`parentId`,`isIncome`,`emoji` FROM `categories`;
DROP TABLE `categories`;
ALTER TABLE `categories_new` RENAME TO `categories`;

CREATE TABLE `stores_new` (
  `id` INTEGER NOT NULL,
  `name` TEXT NOT NULL,
  `logoPath` TEXT,
  `address` TEXT,
  `receiptName` TEXT,
  PRIMARY KEY(`id`)
);
INSERT INTO `stores_new` (`id`,`name`,`logoPath`,`address`,`receiptName`)
SELECT `id`,`name`,`logoPath`,`address`,`receiptName` FROM `stores`;
DROP TABLE `stores`;
ALTER TABLE `stores_new` RENAME TO `stores`;

CREATE TABLE `products_new` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `barcode` TEXT NOT NULL,
  `picturePath` TEXT,
  `categoryId` INTEGER,
  `status` TEXT NOT NULL,
  `changedAt` INTEGER NOT NULL,
  `isSubscription` INTEGER NOT NULL,
  `isFavorite` INTEGER NOT NULL,
  `isIncome` INTEGER NOT NULL
);
INSERT INTO `products_new`
  (`id`,`name`,`barcode`,`picturePath`,`categoryId`,`status`,`changedAt`,
   `isSubscription`,`isFavorite`,`isIncome`)
SELECT
  `id`,`name`,`barcode`,`picturePath`,`categoryId`,`status`,`changedAt`,
  `isSubscription`,`isFavorite`,`isIncome`
FROM `products`;
DROP TABLE `products`;
ALTER TABLE `products_new` RENAME TO `products`;

CREATE TABLE `shopping_lists_new` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL,
  `createDate` INTEGER NOT NULL,
  `purchaseDate` INTEGER,
  `storeId` INTEGER,
  `isFinished` INTEGER NOT NULL,
  `finalTotal` REAL,
  `position` INTEGER NOT NULL,
  `isRecurring` INTEGER NOT NULL,
  `recurringPeriod` TEXT NOT NULL,
  `isForwardEmpty` INTEGER NOT NULL,
  `isSubscription` INTEGER NOT NULL,
  `isIncome` INTEGER NOT NULL,
  `kind` TEXT,
  `isActive` INTEGER NOT NULL,
  `isShared` INTEGER NOT NULL,
  `syncId` TEXT,
  `lastSyncTimestamp` INTEGER NOT NULL,
  `lastModifiedAt` INTEGER NOT NULL,
  FOREIGN KEY(`storeId`) REFERENCES `stores`(`id`)
    ON UPDATE NO ACTION ON DELETE CASCADE
);
INSERT INTO `shopping_lists_new`
  (`id`,`name`,`createDate`,`purchaseDate`,`storeId`,`isFinished`,`finalTotal`,
   `position`,`isRecurring`,`recurringPeriod`,`isForwardEmpty`,`isSubscription`,
   `isIncome`,`kind`,`isActive`,`isShared`,`syncId`,`lastSyncTimestamp`,
   `lastModifiedAt`)
SELECT
  `id`,`name`,`createDate`,`purchaseDate`,`storeId`,`isFinished`,`finalTotal`,
  `position`,`isRecurring`,`recurringPeriod`,`isForwardEmpty`,`isSubscription`,
  `isIncome`,`kind`,`isActive`,`isShared`,`syncId`,`lastSyncTimestamp`,
  `lastModifiedAt`
FROM `shopping_lists`;
DROP TABLE `shopping_lists`;
ALTER TABLE `shopping_lists_new` RENAME TO `shopping_lists`;

DROP TABLE IF EXISTS `sync_state`;
DROP TABLE IF EXISTS `sync_pending_deletes`;
```

Notes:
- Rebuild tables rather than `ALTER TABLE … DROP COLUMN` (minSdk 29 SQLite may be
  older than 3.35). This follows the existing rebuild pattern (`MIGRATION_9_TO_10`,
  `MIGRATION_17_TO_18`, `MIGRATION_22_TO_23`).
- `AUTOINCREMENT`, primary keys, and the `shopping_lists → stores` FK must be
  preserved exactly; Room schema validation will catch mistakes.
- Old migrations 23→30 stay as-is (they run on legacy DBs before 30→31).
- Keep `app/schemas/…/30.json`; building generates `31.json`.

## Phase 4 — Preferences

In `PreferencesManager.kt` remove:

- `getNextcloudUrl` / `setNextcloudUrl`
- `getNextcloudUsername` / `setNextcloudUsername`
- `getNextcloudPassword` / `setNextcloudPassword`
- `getLastPriceSyncAt` / `setLastPriceSyncAt`

Keep `encryptedPrefs` (LLM profiles use it). Do not remove `androidx-security-crypto`.

## Phase 5 — Tests

1. Delete the sync test files listed above.
2. Add `MigrationTest.migrate30To31`:
   - create DB at version 30;
   - insert a category/store/product/list (including `serverId` values), one
     `sync_state` row, and one `sync_pending_deletes` row;
   - `runMigrationsAndValidate(TEST_DB, 31, true, AppDatabase.MIGRATION_30_TO_31)`;
   - assert rows were preserved, `serverId` is absent from all four tables, and
     `sync_state` / `sync_pending_deletes` no longer exist.
3. Keep the existing 23→24 … 29→30 migration tests (they remain historically
   valid).

## Phase 6 — Verification

Run, in order:

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew lint
```

With an emulator/device available:

```bash
./gradlew connectedAndroidTest
```

Manual checks:

- Upgrade an existing v30 install: categories, stores, products, and lists survive;
  no crash on first open.
- Fresh install: Settings has no Nextcloud entry; app works offline.
- Folder sharing (`CLOUD_SHARE_ENABLED`) still works: sharing a list writes/reads
  `.bbl.json` and `ListSyncEngine` still starts from `MainActivity`.
- No `Nextcloud` string or route remains (`grep -ri nextcloud app/src` should only
  hit nothing; docs/wiki history is fine).

## Risks & rollback

- **Room migration is the main risk** (table rebuilds, FK, AUTOINCREMENT). Covered
  by `migrate30To31` + `connectedAndroidTest`; rollback is reverting the commit and
  restoring version 30 (old data untouched until a 31 DB is written).
- **Feature loss is intentional**: local items lose their server link, pending
  deletes/base snapshots are dropped, and users can no longer sync to Nextcloud.
  No local user content is deleted.
- **Share feature regression** is possible if imports are removed too broadly;
  Phase 6 manual share check guards this.

## Out of scope / optional follow-ups

- Remove the legacy folder-based sharing too (`ListSyncEngine`, `SyncFolderRepository`,
  `SyncDto`, `SyncProductMatcher`, `isShared`/`syncId` columns) if the share feature
  is also being retired.
- Move the remaining share files from `data/sync` to `data/share`.
- One-time cleanup of orphaned `nextcloud_*` SharedPreferences keys.
- Update `wiki/pages/specs/02-nextcloud-sync-redesign.md`, plans 06–09, and
  research 03–04 with a "superseded" note. Keep them as history.
