---
created: 2026-09-03
type: plan
tags: [nextcloud, sync, categories, stores, products, epic]
related:
  - "../specs/02-nextcloud-sync-redesign.md"
  - "../research/03-nextcloud-sync-redesign.md"
---

# 6. NextCloud Sync Redesign — Implementation Plan

Plan for the epic defined in [specs/02-nextcloud-sync-redesign](../specs/02-nextcloud-sync-redesign.md). Split into three
tickets; implementation order is **1 → 2 → 3** (tickets 2 and 3 depend on the
framework introduced in ticket 1, and products depend on category `serverId`s).

## Architecture

A generic sync layer parameterised over entity type, with thin per-type configs:

```
data/sync/model/SyncPlan.kt        → SyncPlan<Local,Server> + SyncMatch<Local,Server>(local, server, reason)
data/sync/SyncMatcher.kt           → interface SyncMatcher<Local,Server> { buildPlan(local, server) }
data/sync/SyncRepository.kt        → interface SyncRepository<Local,Server> { fetchLocal/fetchServer/execute }
data/sync/SyncCoordinator.kt       → orchestrates all 3 groups, enforces categories→stores→products order
ui/components/settings/SyncPlanScreen.kt → shared Compose editor (matched/edit, select-all, push/pull lists)
```

Per-type configs implement the interfaces:
- `CategorySyncPart` — hierarchical; wraps existing `MultiLanguageCategoryMatcher`
  (kept as the `SyncMatcher` impl) and `CategorySyncRepository`.
- `StoreSyncPart` — flat, name matching.
- `ProductSyncPart` — flat, barcode-strong matching + category-id mapping.

`CategorySyncScreen`/`StoreSyncScreen`/`ProductSyncScreen` become thin wrappers
around `SyncPlanScreen`, supplying labels, item renderers, and the per-type part.

## Ticket Breakdown

### Ticket 1 — Settings redesign + category refactor + framework foundation

**`NextcloudSyncSettingsScreen.kt`**
- Rename "Sync Categories Now" → **"Sync Now"**; align right (Test Connection
  left).
- Add global **"Use LLM"** checkbox.
- Add grouped list (Categories / Stores / Products), each row showing
  `matched · upload · download` counts from `NextcloudSyncViewModel`.
  **Ticket 1 shows only the live Categories row; Stores and Products render as
  disabled placeholders (zero counts) and become live in Tickets 2/3.**
- Plan generation is **user-triggered only**: "Sync Now" fetches server data and
  builds the plan for all groups, filling the counts. Sub-screens never
  auto-generate; opening a group screen shows whatever plan is in the shared VM
  (empty/stale → empty state prompting the user to run "Sync Now" first).
- Add **"Confirm and sync"** action → `SyncCoordinator.executeAll()`.

**`CategorySyncScreen.kt`**
- Remove "Use LLM" checkbox and "Generate Sync Plan" button.
- Add **Select all / Deselect all** to Upload and Download sections.
- Make matched items editable (unlink a match; re-match only from unmatched).
- Rename button to **"Confirm and sync"**.

**Match editing UX (confirmed design)**
- **Unlink**: each matched row has an unlink icon → the local item returns to
  the Upload pool and the server item to the Download pool.
- **Re-match (Option A, per-row picker)**: every unmatched row (both Upload and
  Download) has a **"Match…"** action opening a searchable picker of unmatched
  items from the *opposite* side (client ⇄ server). Selecting one forms a pair
  and removes both from their pools.
- **Unlinked items with a persisted `serverId`** (from an earlier sync) are
  **excluded from Upload/Download selection** — they can only be re-matched.
  Prevents duplicate server entries on re-push. *(Superseded by Ticket 1.1:
  unlinked items are selectable again — see below.)*

**Navigation / ViewModel**
- `MainScreen.kt:243-253` currently registers Nextcloud settings and Category
  sync as flat sibling destinations that construct their repositories inline.
  Ticket 1 restructures this into a **nested nav graph** with
  `NextcloudSyncSettings` as parent (Settings → Nextcloud → group rows → group
  screen) so `NextcloudSyncViewModel` can be scoped to the settings nav entry
  and shared by all sub-screens.

**Framework**
- Extract `SyncPlan`/`SyncMatch`, `SyncMatcher`, `SyncRepository`,
  `SyncCoordinator`, shared `SyncPlanScreen` + section/select-all helpers.
- New `NextcloudSyncViewModel` (scoped to the settings nav entry) holding three
  plans, the LLM flag, and per-group selections.
- Migrate `MultiLanguageCategoryMatcher` to `SyncMatcher<CategoryEntity,
  NextcloudCategoryDto>` without changing behaviour.

### Ticket 1.1 - follow-up

**`CategorySyncScreen.kt`**
- When user deletes match, unmatched client server pair must appear in download/upload sections.
- User can re-match them or upload/download with creation of new categories with new ids 
- This princip must be also applied to Stores and Products anftr their implementation

**Decision [2026-09-04]:** Ticket 1.1 **reverses** the Ticket-1 restriction that unlinked
items with a persisted `serverId` are excluded from Upload/Download (re-match only). Unlinked
items are now ordinary, selectable unmatched candidates in both pools — re-syncing one creates
a new category with a new id on the destination side. Implemented by removing `canSync` from
`SyncCandidate` (model), `unlinkMatch` (VM) and the shared `SyncPlanScreen` so Stores/Products
inherit the behaviour automatically.

### Ticket 2 — Store synchronisation

- Migration 24→25: `ALTER TABLE stores ADD COLUMN serverId TEXT` (+ schema JSON).
- `StoreEntity`: add `serverId: String?`.
- `StoreDao`: `getByServerId`, `updateServerId`.
- `NextcloudStoreDto` (`id`, `name`); `NextcloudApiClient.fetchStores` /
  `createStore` (sequential single-create, unless server batch endpoint added).
- `StoreSyncMatcher` (case-insensitive name, no hierarchy), `StoreSyncRepository`,
  `StoreSyncPart`.
- `StoreSyncScreen` wrapper; route in `Screen.kt` + `MainScreen.kt`.

### Ticket 3 — Product synchronisation

- Migration 24→25: `ALTER TABLE products ADD COLUMN serverId TEXT` (+ schema JSON).
- `ProductEntity`: add `serverId: String?`.
- `ProductDao`: `getByServerId`, `updateServerId`.
- `NextcloudProductDto` (`id`, `name`, `barcode`, `categoryId`, `aliases`,
  `isFavorite`, `status`, `isSubscription`, `isIncome`);
  `NextcloudApiClient.fetchProducts(type=all)` / `createProduct`.
- `ProductSyncMatcher`: barcode exact first (strong), then name/alias exact, then
  fuzzy (`ProductMatcher`); non-hierarchical.
- `ProductSyncRepository`: map local `categoryId` ↔ server `categoryId` via
  category `serverId`; map `product_aliases` ↔ server `aliases[]`.
- `ProductSyncScreen` wrapper; route in `Screen.kt` + `MainScreen.kt`.

## Server-side notes (`~/Source/byebyemoneylist-ns`)

- Stores: `GET/POST/PUT/DELETE /api/stores` — fields `id`+`name` only.
- Products: `GET/POST/PUT/DELETE /api/products` — fields include `barcode`,
  `categoryId`, `aliases[]`, `isSubscription`, `isIncome`.
- No `batch` endpoints for stores/products (categories has one). Options:
  sequential single-create (no server change) **or** add `POST /api/stores/batch`
  and `POST /api/products/batch` to mirror categories. Default: sequential;
  batch tracked as optional follow-up.

## File Manifest

| # | File | Action |
|---|------|--------|
| 1 | `data/sync/model/SyncPlan.kt` | Create |
| 2 | `data/sync/SyncMatcher.kt` | Create |
| 3 | `data/sync/SyncRepository.kt` | Create |
| 4 | `data/sync/SyncCoordinator.kt` | Create |
| 5 | `ui/components/settings/SyncPlanScreen.kt` | Create |
| 6 | `ui/components/settings/NextcloudSyncViewModel.kt` | Create |
| 7 | `ui/components/settings/NextcloudSyncSettingsScreen.kt` | Modify |
| 8 | `ui/components/settings/CategorySyncScreen.kt` | Modify |
| 9 | `data/sync/CategorySyncRepository.kt` | Modify (adopt interface) |
| 10 | `data/sync/MultiLanguageCategoryMatcher.kt` | Modify (adopt interface) |
| 11 | `data/sync/NextcloudStoreDto.kt` | Create |
| 12 | `data/sync/NextcloudProductDto.kt` | Create |
| 13 | `data/sync/StoreSyncRepository.kt` + `StoreSyncMatcher.kt` | Create |
| 14 | `data/sync/ProductSyncRepository.kt` + `ProductSyncMatcher.kt` | Create |
| 15 | `data/sync/NextcloudApiClient.kt` | Modify (store/product methods) |
| 16 | `ui/components/settings/StoreSyncScreen.kt` | Create |
| 17 | `ui/components/settings/ProductSyncScreen.kt` | Create |
| 18 | `ui/navigation/Screen.kt` | Modify (routes) |
| 19 | `ui/components/main/MainScreen.kt` | Modify (navigation) |
| 20 | `data/local/entity/StoreEntity.kt`, `ProductEntity.kt` | Modify (serverId) |
| 21 | `data/local/dao/StoreDao.kt`, `ProductDao.kt` | Modify |
| 22 | `data/local/AppDatabase.kt` | Modify (migration 24→25) |
| 23 | `app/schemas/...AppDatabase/25.json` | Create (export) |
| 24 | `res/values/strings.xml` (+ de/uk) | Modify |

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Ordering: products reference category server UUIDs | High | Coordinator hard-codes categories→stores→products; products resolve categoryId only after category push |
| Store sync loses logo/address/receiptName (server is name-only) | Medium | Documented; pull creates `logoPath=null`; no local data is deleted on push |
| Generic framework over-abstraction complicates Compose UI | Medium | Shared `SyncPlanScreen` keyed by a descriptor object with lambdas, not deep type hierarchies |
| Refactor breaks existing category sync tests | High | Keep `MultiLanguageCategoryMatcher`/`buildHierarchicalPushDtos` behaviour identical; run unit tests after migration |
| No server batch endpoints → many round-trips | Low | Sequential create acceptable for v1; batch tracked as follow-up |
| LLM matching for stores/products not specified | Medium | Default: reuse name-based LLM prompt; can be gated to categories only initially |

## Open Questions

1. **"Confirm and sync" location** — **[decided]** sub-screens save edits to the
   shared VM and pop back; the settings screen's "Confirm and sync" executes all
   groups. (Rejected: each sub-screen triggering the full sync directly.)
2. **Store logo/address/receiptName** — name-only sync acceptable? (default: yes)
3. **LLM scope for stores/products** — categories-only vs all groups.
4. **Re-match picker cardinality** — a local item pairing with a server item that
   is already matched must be filtered out (picker lists only unmatched items).
   Confirmed; no further decision needed.
5. **[Decided] Match editing UX** — Option A per-row "Match…" searchable picker;
   unlink via icon on matched rows; unlinked items with persisted `serverId` are
   excluded from sync and can only be re-matched.

## Testing Checklist

- [ ] `./gradlew compileDebugKotlin` — clean
- [ ] `./gradlew test --tests "*MultiLanguageCategoryMatcherTest"` — green
- [ ] `./gradlew test --tests "*CategoryHierarchyPushTest"` — green
- [ ] `./gradlew assembleDebug` — APK builds
- [ ] MigrationTest covers 24→25 (stores + products `serverId`)
- [ ] Settings: "Sync Now" populates all three counts; Stores/Products rows are
      disabled placeholders in Ticket 1
- [ ] Category screen: select-all/deselect-all, unlink match, re-match only from
      unmatched
- [ ] Unlinked item with persisted `serverId` re-appears in Upload/Download as an ordinary
      selectable candidate and can be re-matched or re-synced (new entry with new id) [Ticket 1.1]
- [ ] Store screen: name match, push/pull
- [ ] Product screen: barcode strong match, category-id mapping after category sync
- [ ] "Confirm and sync" runs categories→stores→products and returns to settings

## Related

- Spec: [specs/02-nextcloud-sync-redesign](../specs/02-nextcloud-sync-redesign.md)
- Research: [research/03-nextcloud-sync-redesign](../research/03-nextcloud-sync-redesign.md)
- Phase 2 (delta sync, tickets 4–6): [plans/07-sync-delta-git-model](../plans/07-sync-delta-git-model.md)

## Updates

- [2026-09-04]: Added Phase 2 as a separate plan — [plans/07-sync-delta-git-model](../plans/07-sync-delta-git-model.md)
  (tickets 4–6). It extends, not replaces, this plan: `SyncPlan`/`SyncMatcher`/
  `SyncRepository` survive; the delta work adds change detection, update
  propagation, and conflict resolution on top. Deletes remain deferred.
