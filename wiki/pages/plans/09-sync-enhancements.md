---
created: 2026-09-06
type: plan
tags: [nextcloud, sync, ui, store, shopping-list, enhancement, tickets-4-6]
related:
  - "../specs/02-nextcloud-sync-redesign.md"
  - "../plans/07-sync-delta-git-model.md"
  - "../plans/08-shopping-list-sync.md"
  - "../research/04-sync-delta-git-model.md"
---

# 9. Sync Enhancements (Tickets 4–6 follow-ups) — Implementation Plan

Follow-up work on the completed git-style delta sync (tickets 4–6 in
[plans/07](../plans/07-sync-delta-git-model.md)) and the Phase-3 shopping-list
mirror ([plans/08](../plans/08-shopping-list-sync.md)). Three independent
improvements, listed by increasing scope:

- **Ticket A** — Pending-update hints on the settings hub + clearer "updated" labels.
- **Ticket B** — Sync store **category** and **address** (server + client).
- **Ticket C** — Git-style sync for **shopping lists** + a `ShoppingListSyncScreen`.

A/B/C are independent and can be planned/implemented separately. A is client-only,
B requires server work in `~/Source/byebyemoneylist-ns` (migration `Version1007`),
C is the largest and needs its own design decisions before coding.

---

## Ticket A — Pending-update hints in the sync settings hub

**Problem.** The group rows on
`ui/components/settings/NextcloudSyncSettingsScreen.kt` show only
`matched · upload · download`
(`nextcloud_sync_row_counts`), and the Shopping Lists row is informational only.
There is no signal that a group holds pending one-sided changes or unresolved
conflicts until the user opens the sub-screen.

**Goal.**

1. On each group row show a **visual hint** (colored badge/dot + text) when the
   group has pending updates (`LOCAL_CHANGED` / `SERVER_CHANGED`, selected) and
   when it has unresolved conflicts — mirroring the sub-screen badges
   ([plans/07](../plans/07-sync-delta-git-model.md), tickets 5–6).
2. Sharpen the "updated" labels: the matched headers
   (`category_sync_matched`, `store_sync_matched`, `product_sync_matched`) and
   the row counts should surface the number of updates/conflicts, not hide them
   behind a generic matched count.

**Files / changes (client only).**

- `data/sync/model/SyncPlan.kt` — extend `SyncGroupCounts` with
  `updates: Int = 0`, `conflicts: Int = 0` (from editor matched candidates
  selected & changed + unresolved conflicts).
- `ui/components/settings/NextcloudSyncViewModel.kt` — compute the new counts
  in `SyncGroupEditorState.counts()` (matched update candidates selected +
  unresolved conflict count, cf. `unresolvedConflictCount()`).
- `ui/components/settings/NextcloudSyncSettingsScreen.kt` — `SyncGroupRow` gains
  an optional badge area (e.g. small `Surface` chip "N update(s)" /
  "N conflict(s)" in `tertiary`/`errorContainer` colors); render from the new
  counts. Shopping Lists row unchanged (Ticket C makes it actionable).
- `ui/components/settings/SyncPlanScreen.kt` + the three wrapper screens —
  matched/conflict headers can embed the counts already available (minor string
  change: `%1$d updates` etc.).
- `app/src/main/res/values{,–de,–uk}/strings.xml` — e.g.
  `nextcloud_sync_row_updates` / `nextcloud_sync_row_conflicts`.

**Tests.** Pure count computation on a synthetic `SyncGroupEditorState`
(matched with mixed `contentState`/selection + unresolved conflicts); JVM.

---

## Ticket B — Sync store category + address

**Current state.**

- Server: `bbml_stores` has only `id / owner / name`
  (`lib/Entity/StoreEntity.php`, `lib/Migration/Version1000Date20260826.php`);
  `StoreController` create/update/serialize handle **name only**
  (`~/Source/byebyemoneylist-ns/lib/Controller/StoreController.php`). No
  store↔category link exists (categories are owner-global, cf. research above).
- Client: `stores` table already has `address`, `logoPath`, `receiptName`
  (local-only) and `store_category_cross_ref` (many-to-many local).
  Store sync canonical projection is `{ name }` only
  (`data/sync/SyncProjection.kt` → `storeLocal`/`storeServer`); pull/create
  already maps the local cross-ref but push/pull-update never send it.

**Goal.** Promote `address` and the category links to **shared, synced fields**
(name stays the identity/matcher key). `logoPath` / `receiptName` remain
local-only.

### Server (`byebyemoneylist-ns`)

1. Migration **`Version1007Date…`** (`lib/Migration/`): add `bbml_stores.address`
   `STRING(255) NULL`; create junction `bbml_store_categories`
   (`id STRING(36) PK`, `store_id STRING(36)` + index, `category_id STRING(36)`
   + index) — mirror `bbml_list_categories` from `Version1006Date20260905.php`.
2. `lib/Entity/StoreEntity.php` + `lib/Db/StoreMapper.php` — add `address`,
   `categoryIds` fetch (`findCategoryIdsByStoreIds`, analogous to
   `ListMapper::findCategoryIdsByListIds`) and
   `replaceCategoriesByStoreId` (delete + re-insert junction rows).
3. `lib/Controller/StoreController.php`:
   - `create(string $name, ?string $address = null, array $categoryIds = [])`
     and `update(..., ?string $address = null, array $categoryIds = [])` —
     store address, validate category ownership (`findByIdAndOwner`, 422 like
     `ProductController`), write junction in a transaction.
   - `index`/`serializeStore` include `address` and `categoryIds`.
   - `destroy` also clears `bbml_store_categories` rows.
4. OpenAPI docs + optional web UI (`src/services/…`) updated.

### Client

1. **DTOs / API**: `NextcloudStoreDto` gains `address: String?`,
   `categoryIds: List<String>`; `NextcloudStoreCreateRequest` gains the same;
   `NextcloudApiClient.createStore/updateStore` send full state
   (`requestJson`, `encodeDefaults=true` — already the pattern).
2. **Canonical projection** (`data/sync/SyncProjection.kt`): store becomes
   `{ name, address, categoryServerIds }` (sorted, blank-normalized) on **both**
   sides. Local category ids are resolved to server ids via the category
   `serverId` map; unresolvable → omitted (like product `categoryServerId`).
3. **DAO**: pull/update helpers to write shared fields only — set `name`,
   `address`, and replace `store_category_cross_ref` (existing
   `deleteCategoriesForStore` / `insertStoreCategoryCrossRef`); never touch
   `logoPath`/`receiptName`.
4. **Repos** (`data/sync/StoreSyncRepository.kt`):
   - create/pull-create: map server `categoryIds` → local categories and write
     cross-refs (currently pulled with `logoPath=null` only);
   - push-update (`toUpdateServer`): PUT `name+address+categoryIds`; base =
     local projection;
   - pull-update (`toUpdateLocal`): overwrite `name`+`address`+categories, base =
     server projection.
5. **Base-snapshot invalidation.** Existing `sync_state` rows for stores hold
   `{name}` JSON; a changed canonical shape would make every store look
   "changed on both sides" → spurious conflicts. Mitigation (pick one):
   - Client DB migration **28→29** that `DELETE FROM sync_state WHERE
     entityType = 'store'` (bookkeeping-only table) → next generate re-baselines
     `IN_SYNC` via the documented rebase-on-local backfill
     ([plans/07](../plans/07-sync-delta-git-model.md)); **recommended**.
   - or a projection-format version column.
6. Matcher (`StoreSyncMatcher`) unchanged — matching stays name-based.

**Tests.** `SyncProjectionTest` store cases (address + sorted category ids;
blank address == null); `StoreUpdatePullTest` extended (pull overwrites
name/address/categories, preserves logo/receiptName);
`SyncUpdateDaoTest` store case (cross-ref replacement); JVM. Instrumented
server round-trip optional against a dev instance.

---

## Ticket C — Git-style shopping-list sync + `ShoppingListSyncScreen`

**Current state.** Shopping lists sync is a **mirror** (Phase 3,
[plans/08](../plans/08-shopping-list-sync.md)):
`ShoppingListsSyncRepository.sync()` is a single auto pass — pull unknown
server lists, push `serverId == null` lists, push linked lists only when
`local.lastModifiedAt > server.updatedAt` (`shouldPushLinkedList`), items are
**full-replaced** per push, deletes flow one-way (local delete →
`sync_pending_deletes` → `DELETE`). It never touches `sync_state`, has no
plan/selection, and is surfaced only as a read-only settings row
(`ShoppingListsSyncUiState` + `ShoppingListsGroupRow`). Server side is a plain
REST mirror (`Version1006`: list/item CRUD, `updated_at`, `is_finished`,
list–category junction, hard DELETE) — no git/etag/merge primitives.

**Goal.** Bring shopping lists into the same user-visible model as
categories/stores/products:

- a per-list sync **state** (`IN_SYNC` / `LOCAL_CHANGED` / `SERVER_CHANGED` /
  `CONFLICT`), no longer "auto-synced silently";
- a new **`ShoppingListSyncScreen`** (new `Screen.ShoppingListSync` route,
  clickable Shopping Lists row) where the user reviews states and confirms
  push/pull/conflict-resolution per list — mirroring `SyncPlanScreen` for the
  match-based groups (tickets 5–6 UX).

### Design decisions to finalize (open questions)

1. **Identity**: serverId-linked, no matcher — matches only where a local list
   is unknown (pull-create) or `serverId == null` (push-create). No
   "unmatched pools", no re-match picker. A dedicated editor state (not the
   generic `SyncMatch` editor) or a restricted reuse?
2. **Change detection**: extend the **content-hash + base snapshot** model to
   lists (`entityType='shopping_list'`): canonical projection of the **list
   header** (name, storeServerId, categoryServerIds sorted, purchaseDate,
   isFinished, flags, position?) **plus** a canonical **items** digest
   (sorted list of `{productId, quantity, isChecked, price, discount,
   customName}`). Replace `lastModifiedAt > updatedAt` heuristic; drop reliance
   on server timestamps. Alternatively keep timestamps but expose them in UI —
   loses the converged-edit case.
3. **Item-level merge**: items are currently full-replaced (client wins, server
   set wins on pull). For git-style:
   - push = full-replace of the whole item set (already implemented) —
     acceptable asymmetry; document;
   - pull = reconcile item set (insert/update/delete differences) or full
     replace of the local item set from server on `SERVER_CHANGED`;
   - conflict resolution still needs an item-merge policy (default: whole-list
     "use local"/"use server" like tickets 5–6, not per-item).
4. **Remote deletions**: server hard-deletes lists; client needs a policy —
   currently none (local rows survive). Options: treat a missing server list as
   delete-pending local (adds delete direction), or leave to a tombstone
   follow-up (open question #3 of
   [plans/07](../plans/07-sync-delta-git-model.md)).
5. **`isFinished` / recurring forwarding / subscriptions**: server treats
   `is_finished`/`updated_at` as authoritative; local recurring forwarding
   writes bypass `markModified`. Needs explicit rules so forwarding doesn't
   produce spurious conflicts (cf. `checkAndForwardRecurringLists`).

### Suggested implementation order (still inside Ticket C)

- **C1 — foundations**: extend `sync_state` usage to `shopping_list`
  (`SyncProjection` list builders incl. items digest; reuse `SyncStateDao`),
  plan generation states from fetched server lists + local lists + base rows
  (pull-create/push-create buckets kept); no execution change yet.
- **C2 — execution**: convert the mirror's automatic push/pull into
  `executeSyncPlan`-style selected lists; resolved `LOCAL_CHANGED` →
  `pushUpdateList`, `SERVER_CHANGED` → pull/reconcile; advance base. Defer
  remote-delete handling (open question).
- **C3 — UI**: `Screen.ShoppingListSync` route in the
  `NEXTCLOUD_SYNC_GRAPH_ROUTE` graph (`ui/navigation/Screen.kt`,
  `MainScreen.kt`); make the Shopping Lists row clickable
  (`NextcloudSyncSettingsScreen` gains `onOpenShoppingLists`); new
  `ShoppingListSyncScreen` listing lists with state badges + "Use local/Use
  server" for conflicts; a `ShoppingListSyncEditorState`/VM layer mirroring
  `SyncGroupEditorState` but without match-pools. Reuse strings pattern from
  tickets 5–6 (badges, conflict actions) — many already exist.

**Key files (client).**
`data/sync/ShoppingListsSyncRepository.kt`,
`data/sync/SyncProjection.kt`, `data/sync/model/SyncStateEntity.kt`
(`TYPE_SHOPPING_LIST`),
`data/local/entity/ShoppingListEntity.kt`,
`ui/components/settings/NextcloudSyncViewModel.kt`,
`NextcloudSyncSettingsScreen.kt`, new `ShoppingListSyncScreen.kt`,
`ui/navigation/Screen.kt`, `ui/components/main/MainScreen.kt`,
strings en/de/uk. Possibly a client DB migration if list base invalidation is
needed at cutover (delete `shopping_list` sync_state rows once).

**Tests.** `ShoppingListProjectionTest` (header + items digest stable across
pull/push, sorted items order-insensitive, quantities/flags change hash);
`ShoppingListStateResolver` annotation incl. conflict + converged cases;
mirror regression (`ShoppingListsSyncResult` counters still sane after refactor
to selected lists). Instrumented DAO tests for item reconcile helper.

---

## Shared risk table

| Risk | Impact | Mitigation |
|---|---|---|
| Projection shape change (stores; later lists) re-flags old base snapshots | High | Client DB migration clearing that `entityType`'s `sync_state` rows → clean rebase-on-local once; unit-test the migration |
| Ticket C item-set hashing is expensive/incorrect on large lists | Medium | Hash from the same canonical sort used for pulls; test order-independence; keep full-replace execution so hashing is advisory |
| Recurring forward / `isFinished` writes race the sync state | Medium | Explicit rules + dirty-marking audit in `ShoppingListRepository` (sub-ticket C1) |
| Store category round-trip semantics diverge (server global vs client store-scoped) | Medium | Server junction mirrors `list_categories`; ownership validated; client keeps local-only `logoPath`/`receiptName` |
| Remote list deletion policy undefined | Medium | Explicit open question before C2; no silent data loss either way |

## Testing checklist (once a ticket is implemented)

- [ ] `./gradlew compileDebugKotlin` clean
- [ ] `./gradlew testDebugUnitTest` green (new projection/resolver/editor-state tests per ticket)
- [ ] `./gradlew assembleDebug` builds
- [ ] Instrumented (`SyncUpdateDaoTest`, `MigrationTest`) updated + run on device/CI
- [ ] Server: `Version1007` migration runs; store create/update round-trip address + categories via OCS (Ticket B)

## Related

- Delta model: [plans/07-sync-delta-git-model](../plans/07-sync-delta-git-model.md) · [research/04-sync-delta-git-model](../research/04-sync-delta-git-model.md)
- Shopping-list mirror: [plans/08-shopping-list-sync](../plans/08-shopping-list-sync.md)
- Epic spec: [specs/02-nextcloud-sync-redesign](../specs/02-nextcloud-sync-redesign.md) · [research/03-nextcloud-sync-redesign](../research/03-nextcloud-sync-redesign.md)
