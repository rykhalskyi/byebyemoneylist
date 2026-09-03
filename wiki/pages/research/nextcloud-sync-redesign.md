---
created: 2026-09-03
type: research
tags: [nextcloud, sync, categories, stores, products, epic]
related: [[specs/nextcloud-sync-redesign]] [[plans/nextcloud-sync-redesign]]
---

# NextCloud Sync Redesign — Research

## Topic

Research the current Nextcloud sync implementation and the server-side API to
plan the epic (see [[specs/nextcloud-sync-redesign]]) that completes
synchronisation by adding Store and Product sync alongside the existing Category
sync.

## Raw design notes (original idea)

> - Settings screen: align "SyncCategoriesNow" right; rename to "Sync Now".
> - Add list under buttons; each item = grouped sync part (categories, stores, products).
> - Each item shows matched / upload / download counts.
> - Click item → group screen (categories → `CategorySyncScreen`).
> - On `CategorySyncScreen`: remove "Use LLM" (moves up to settings), remove
>   "Generate sync plan"; add "deselect all" to upload/download groups.
> - Matched/unmatched items editable: delete match, re-match only from unmatched.
> - Add similar screens for Stores and Products (flat, matched the same way).
> - Products with barcode → strong match field.
> - "Confirm and sync" goes to settings screen and syncs all groups.

## Context

### Client-side current state (`~/Source/byebyemoneylist`)

- `NextcloudSyncSettingsScreen.kt` — server URL/user/password config, "Test
  Connection", and a single "Sync Categories Now" button that navigates to the
  category sync route (`MainScreen.kt:243-248`).
- `CategorySyncScreen.kt` — full working category sync UI:
  - "Use LLM" checkbox (line 144), "Generate Sync Plan" button (line 363),
  - matched / upload / download collapsible sections with per-item checkboxes
    (no select-all / deselect-all),
  - read-only matched list (line 213),
  - "Confirm & Sync" executes `executeSyncPlan` (line 314) and pops back.
- `CategorySyncRepository.kt` — `generateSyncPlan` (fetch server + local,
  `MultiLanguageCategoryMatcher.buildSyncPlan`, optional LLM pass) and
  `executeSyncPlan` (save serverId, pull, hierarchical batch push via
  `buildHierarchicalPushDtos`).
- `MultiLanguageCategoryMatcher.kt` — exact (serverId / name) + child-overlap +
  LLM matching; `CategorySyncPlan(matched, toPushToServer, toPullToClient)`.
- `NextcloudApiClient.kt` — only category endpoints: `fetchCategories`,
  `createCategoryBatch`.
- Entities: `CategoryEntity` has `serverId`; **`StoreEntity` and `ProductEntity`
  do not**.
- `StoreEntity`: `id, name, logoPath, address, receiptName` (no serverId).
- `ProductEntity`: `id, name, barcode, picturePath, categoryId(local), status,
  changedAt, isSubscription, isFavorite, isIncome` (no serverId).
- `AppDatabase` version 24; `MIGRATION_23_TO_24` added `categories.serverId`.
- Existing tests: `MultiLanguageCategoryMatcherTest`,
  `CategoryHierarchyPushTest` (unit), `MigrationTest` (instrumented).

### Server-side state (`~/Source/byebyemoneylist-ns`)

Controllers already exist and expose these OCS API routes:

| Group | Routes | Serialized fields |
|-------|--------|-------------------|
| Categories | `GET/POST/PUT/DELETE /api/categories`, `POST /api/categories/batch`, `POST /api/categories/{id}/confirm`, `POST /api/categories/confirm-all` | `id, name, color, emoji, parentId, income, status` |
| Stores | `GET/POST/PUT/DELETE /api/stores` | `id, name` only |
| Products | `GET/POST/PUT/DELETE /api/products` (`?type=normal\|subscriptions\|income\|all`) | `id, name, barcode, categoryId, aliases[], isFavorite, status, isSubscription, isIncome` |

Key facts:
- Stores/products have **no batch endpoint** (categories does).
- Server store model has **no logo/address/receiptName**.
- Product `categoryId` references a server category UUID → products must sync
  **after** categories.

## Findings

1. **Endpoints already exist** — the client only needs new DTOs, API methods,
   matchers, repositories and screens; no new server routes are required (batch
   endpoints optional).
2. **`serverId` is missing** on stores and products → a single Room migration
   (24 → 25) is required.
3. **Ordering is mandatory**: categories → stores → products, because products
   map `categoryId` to category `serverId`s.
4. **Matching strategy** differs: categories are hierarchical (existing matcher);
   stores/products are flat. Products use barcode as a strong key.
5. **UI is largely reusable**: the three group screens share matched/edit,
   select-all/deselect-all, and push/pull structure → a generic `SyncPlanScreen`
   is justified.

## Conclusions

- Adopt a **generic sync framework + thin per-type configs** (see
  [[plans/nextcloud-sync-redesign]]).
- Plan generation centralised on the settings screen ("Sync Now"); sub-screens
  only edit and confirm.
- Split implementation into 3 tickets: (1) settings redesign + category refactor
  + framework, (2) stores, (3) products.
- Recommended action: implement client-side only first; add server batch
  endpoints for stores/products as an optional follow-up.

## Recommended Actions

1. Add `serverId` to `StoreEntity`/`ProductEntity` + migration 24→25.
2. Build the generic sync framework and refactor categories onto it.
3. Implement stores, then products.
4. Decide on store logo/address/receiptName handling (name-only sync).
5. Optionally add `POST /api/stores/batch` and `POST /api/products/batch` server-side.
