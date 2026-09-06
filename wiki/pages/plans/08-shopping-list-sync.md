---
created: 2026-09-05
type: plan
tags: [nextcloud, sync, shopping-lists, items, serverId, mirror, epic]
related:
  - "../specs/02-nextcloud-sync-redesign.md"
  - "../plans/06-nextcloud-sync-redesign.md"
  - "../research/03-nextcloud-sync-redesign.md"
---

# 8. Shopping List Sync — Implementation Plan

Plan for adding shopping lists (+ items) to the Nextcloud sync. This is the
**Phase 3** of the sync epic defined in [plans/06-nextcloud-sync-redesign](../plans/06-nextcloud-sync-redesign.md)
(tickets 1–3: categories → stores → products) and runs **after** those groups.

## Overview

Unlike categories/stores/products, shopping lists have **no match routine**.
A list's identity is not matched by name/content (names repeat, e.g.
`Purchase 05.09.26`) — it is linked to its server twin purely by a persisted
`serverId`. "Unmatch/re-match" is not applicable. The sync is a **mirror**:

- **Bidirectional creation** — a local list with no `serverId` is created on the
  server; a server list with no local `serverId` is created on the client.
- **Client-authoritative updates** — a linked list (local `serverId` set) is the
  source of truth; edits are pushed up via `PUT`.
- **Delete propagation** — deleting a local list issues `DELETE` to the server.
  The `serverId` is captured at delete time in a pending-delete queue; the sync
  drains it. Item removals are handled by full-replace.

## Architecture

A dedicated `ShoppingListsSyncRepository` — **not** `SyncRepository<Local,Server>`,
which is match-based. It runs **after** `SyncCoordinator.executeAll()` finishes
categories→stores→products, because a list references `storeId` / `categoryId` /
`productId` by their **server UUIDs** (populated in tickets 1–3).

```
server lists ──fetch──►  map by id
local lists  ──read───►  map by serverId (null = new)
  ├─ server id ∉ local serverIds          → PULL: create local list + items (map back via serverId)
  ├─ local serverId == null               → PUSH: create server list + items, store serverId
  ├─ local serverId != null (dirty)       → PUSH: update server list (client wins), replace items
  └─ pending deletes (queued serverIds)   → DELETE on server, clear queue
```

**Item mirroring = full replace.** A list's items have no independent identity on
the server. On push the server item set is replaced with the local item set
(delete missing, upsert the rest). This avoids per-item `serverId`s and per-item
tombstones; the cost is server item-UUID churn on every edit (mitigated by an
optional items batch endpoint).

**Change detection** uses a server `updated_at` timestamp (added in Ticket 1).
The server stamps `updated_at` on create/update; the client stores the last-synced
value and pushes a linked list when local `lastModifiedAt` is newer. The same
`updated_at` is also exposed on items for future conflict handling and web-app
consistency.

## Ticket Breakdown

Implementation order **1 → 2 → 3** (ticket 3 depends on the server surface from
ticket 1 and the client data layer from ticket 2).

### Ticket 1 — Server: full list/item mirror support (`~/Source/byebyemoneylist-ns`)

**Migration** `Version1005Date2026…`:
- New junction table `bbml_list_categories` (`id`, `list_id`, `category_id`) —
  the client has many categories per list; the server currently has a single
  `category_id`.
- `bbml_lists`: add `position INT NOT NULL DEFAULT 0`, `purchase_date DATETIME`,
  `is_finished BOOLEAN NOT NULL DEFAULT false`, `updated_at DATETIME`
  (`final_total`, `created_at`, recurring flags already exist). `is_finished` is
  the authoritative mirror of the client `isFinished`; `status` is kept for
  web-app lifecycle compatibility.
- `bbml_list_items`: add `position INT NOT NULL DEFAULT 0`,
  `discount DECIMAL(12,2)`, `custom_name STRING`, `updated_at DATETIME`
  (`price`, `quantity`, `is_checked`, `status`, `created_at` already exist).

**`ListController`**:
- Add `PUT /api/lists/{id}` (update name, storeId, categoryIds, position,
  purchaseDate, finalTotal, isFinished, recurring flags); stamp `updated_at`.
- Add `DELETE /api/lists/{id}`.
- Serialize and accept `categoryIds[]`, `position`, `purchaseDate`, `isFinished`,
  `finalTotal`, `createDate`, `updatedAt` (stamp `updated_at` on create too).

**`ListItemController`**:
- Add `position`, `discount`, `customName` to create/update/serialize; stamp
  `updated_at`.
- Optional `POST /api/lists/{id}/items/batch` for full-replace efficiency
  (default v1: sequential create/delete).

**Mappers** — `ListMapper`/`ListItemMapper`: read/write list-category join;
delete items by list id for full-replace.

### Ticket 2 — Client: data layer + mirror repository

- **Migration 26→27**: `ALTER TABLE shopping_lists ADD COLUMN serverId TEXT`
  (items use full-replace → no item `serverId`).
- `ShoppingListEntity`: add `serverId: String?`; `ShoppingListDao`:
  `getByServerId`, `updateServerId` (mirror `StoreDao` pattern).
- New DTOs: `NextcloudListDto`, `NextcloudListItemDto`,
  `NextcloudListCreateRequest`, `NextcloudListUpdateRequest`,
  `NextcloudListItemCreateRequest`.
- `NextcloudApiClient`: `fetchLists`, `createList`, `updateList`, `deleteList`,
  `createListItem` (+ optional `batch`), `updateListItem`, `deleteListItem`.
- `ShoppingListsSyncRepository`: `sync()` implementing the mirror above; resolves
  `storeId`→store.serverId, `categoryIds`→category.serverId, item
  `productId`→product.serverId. Items whose referenced product/store/category
  lacks a `serverId` are **skipped and counted** (can't reference a not-yet-synced
  entity).
- New pending-delete queue `SyncPendingDeleteEntity` (`entity`, `serverId`) +
  DAO: the repository's delete path enqueues the list's `serverId` before
  removing the row; `sync()` issues `DELETE` for queued ids and clears them.

### Ticket 3 — Client: integration + UI

- `NextcloudSyncViewModel`: add a 4th "Shopping Lists" group (no editor state —
  only `listCount`, `skipped`, `error`); in `confirmAndSync` run
  `ShoppingListsSyncRepository.sync()` **after** the coordinator's three groups.
- `NextcloudSyncSettingsScreen`: add a "Shopping Lists" row (count + status,
  no sub-screen — there is nothing to match/edit).

## Server-side notes (`~/Source/byebyemoneylist-ns`)

- Current list surface is minimal: `GET/POST /api/lists`, item
  `GET/POST/PUT/DELETE /api/lists/{id}/items`. Lists lack `PUT`/`DELETE`; items
  lack `position`/`discount`/`customName`; lists have a single `categoryId`.
- List `status` is the web-app lifecycle field (server sets `'new'` on create).
  The client `isFinished` is mirrored by a new explicit `is_finished` column;
  `status` is kept for web-app compatibility.
- Lists reference server UUIDs for `storeId`/`categoryId`/`productId` — they must
  already exist server-side before a list/item is pushed.
- Server has **no `updated_at`** on lists/items today; Ticket 1 adds it and
  stamps it on create/update.

## File Manifest

| # | File | Action |
|---|------|--------|
| 1 | `lib/Migration/Version1005Date2026*.php` | Create |
| 2 | `lib/Entity/ListEntity.php`, `ListItemEntity.php` | Modify |
| 3 | `lib/Controller/ListController.php`, `ListItemController.php` | Modify |
| 4 | `lib/Db/ListMapper.php`, `ListItemMapper.php` | Modify |
| 5 | `app/.../entity/ShoppingListEntity.kt` | Modify (serverId) |
| 6 | `app/.../dao/ShoppingListDao.kt` | Modify |
| 7 | `app/.../AppDatabase.kt` | Modify (migration 26→27) |
| 8 | `app/.../data/sync/NextcloudListDto.kt`, `NextcloudListItemDto.kt` | Create |
| 9 | `app/.../data/sync/NextcloudApiClient.kt` | Modify (list methods) |
| 10 | `app/.../data/sync/ShoppingListsSyncRepository.kt` | Create |
| 11 | `app/.../entity/SyncPendingDeleteEntity.kt` + `dao/SyncPendingDeleteDao.kt` | Create |
| 12 | `app/.../settings/NextcloudSyncViewModel.kt` | Modify |
| 13 | `app/.../settings/NextcloudSyncSettingsScreen.kt` | Modify |
| 14 | `app/schemas/...AppDatabase/27.json` | Create (export) |
| 15 | `res/values/strings.xml` (+ de/uk) | Modify |

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| List references a store/category/product without `serverId` | High | Skip + count; run lists strictly after groups sync |
| Deleted local list not propagated (serverId lost before delete) | High | Pending-delete queue captures `serverId` at delete time; drain on sync |
| Item full-replace churns server UUIDs / many round-trips | Medium | Optional items batch endpoint |
| `status` vs `isFinished` drift | Medium | Add explicit `is_finished` column; `status` kept only for web-app lifecycle |
| `updated_at` missing on server | Medium | Add + stamp on create/update in both list and item tables |
| Multi-category lists: server web view shows one vs many | Low | Server stores all via junction; display is web-app concern |

## Open Questions

1. **`status` lifecycle values** — keep web-app `status` (`new`/`finished`/…)
   alongside the new `is_finished` flag, or retire `status` on lists entirely?
2. **Server `createDate`** — should the client's `createDate` be preserved on
   create (server currently sets `now`), or is server `createdAt` authoritative?

## Decisions

- [2026-09-05]: **No match routine** — linkage by `serverId` only.
- [2026-09-05]: **Live mirror** (client-authoritative updates) + **bidirectional
  creation**.
- [2026-09-05]: **Item full-replace** (no per-item `serverId`).
- [2026-09-05]: **Delete propagation** — pending-delete queue captures `serverId`
  at delete time; sync issues `DELETE`.
- [2026-09-05]: **Explicit flags + `updated_at` on server** — add `is_finished`
  and `updated_at` (lists + items) so the web/server carries the client's flags
  unchanged; change detection uses server `updated_at`.

## Testing Checklist

- [ ] `./gradlew test` + `MigrationTest` covers 26→27
- [ ] Server: PHP acceptance for list CRUD + list-categories + item fields
- [ ] Pull: new server list appears locally with store/categories/items mapped
- [ ] Push: new local list created on server; `serverId` stored
- [ ] Update: renaming/checking/finishing a linked list updates server
- [ ] Ordering: list sync runs after categories/stores/products
- [ ] Deleted local list is deleted on server (pending-delete queue drained)
- [ ] List item referencing an unsynced product is skipped + reported
- [ ] `is_finished` and `updated_at` are serialized both directions

## Related

- Spec: [specs/02-nextcloud-sync-redesign](../specs/02-nextcloud-sync-redesign.md)
- Epic plan (tickets 1–3): [plans/06-nextcloud-sync-redesign](../plans/06-nextcloud-sync-redesign.md)
- Research: [research/03-nextcloud-sync-redesign](../research/03-nextcloud-sync-redesign.md)
- Phase 2 delta model (tickets 4–6): [plans/07-sync-delta-git-model](../plans/07-sync-delta-git-model.md)

## Updates

- [2026-09-05]: **Ticket 1 (server) implemented** in
  `~/Source/byebyemoneylist-ns` — migration is `Version1006Date20260905` (the
  manifest's `1005` number was already taken by the category-status migration),
  `bbml_list_categories` junction + `position`/`purchase_date`/`is_finished`/
  `updated_at` (lists) and `position`/`discount`/`custom_name`/`updated_at`
  (items); `PUT`/`DELETE /api/lists/{id}`, `categoryIds[]`/dates/`isFinished`/
  `createDate` accepted & serialized both ways; item endpoints gain
  `position`/`discount`/`customName`; `is_finished` mirrors into web `status`
  (`new ⇄ finished`); category delete also clears junction rows. Verified with
  unit tests + live OCS calls on nextcloud.local. See server ticket
  `wiki/pages/tickets/ticket-09-list-mirror-api.md`.
- [2026-09-05]: **Ticket 2 (client data layer + mirror repository)
  implemented** — migration **26→27** adds `shopping_lists.serverId` (plain
  `ALTER`, no item column) and creates the `sync_pending_deletes` table;
  `ShoppingListEntity` gains `serverId`, DAO mirrors the `StoreDao` pattern
  (`getByServerId`/`updateServerId`) plus `getShoppingListIdByItemId`;
  `NextcloudListDto`/`NextcloudListItemDto` + create/update requests;
  `NextcloudApiClient` list/item CRUD (OCS + direct parse, `encodeDefaults`
  full-state push, 404-as-success on deletes); new `ShoppingListsSyncRepository`
  mirror + `ShoppingListsSyncResult` counts; pending-delete queue is enqueued in
  `ShoppingListRepository.deleteShoppingList` before the row is removed.
  **Dirty detection**: per decision, `ShoppingListRepository` mutation paths
  bump `lastModifiedAt`; the mirror pushes a linked list when `lastModifiedAt`
  is newer than the fetched server `updated_at`, and re-anchors it to the
  server `updated_at` after each pull/push. Pull skips items whose product has
  no local twin; push skips items whose product/quantity can't be referenced
  server-side — both counted in `skippedItems`. Schema exported to
  `app/schemas/…/27.json`; `MigrationTest.migrate26To27` +
  `NextcloudSyncDatesTest` added.
- [2026-09-05]: **Ticket 3 (client integration + UI) implemented** — 4th
  "Shopping Lists" group added to the sync hub. `NextcloudSyncUiState` gains
  `shoppingLists: ShoppingListsSyncUiState` (no editor state — `hasSynced`,
  `listCount`, `skipped`, `error`); the ViewModel builds a
  `ShoppingListsSyncRepository` and `confirmAndSync` runs its `sync()` **after**
  the coordinator's Categories → Stores → Products groups, folding the mirror
  result into the group/global success + error handling.
  `NextcloudSyncSettingsScreen` shows a read-only "Shopping Lists" row
  (`ShoppingListsGroupRow`, no sub-screen/onClick) reporting count +
  skipped/error after a sync; strings added in en/de/uk.
- [2026-09-05]: **Sync Now also runs the mirror** — `syncNow()` executes
  `ShoppingListsSyncRepository.sync()` (in addition to plan generation), so the
  Shopping Lists row updates immediately instead of only after "Confirm and
  sync" (which still runs it after the three groups). Added a **deferred-create
  guard**: a local list whose items all reference products without a `serverId`
  is *not* created on the server (no `serverId` stored) and is retried on the
  next run — prevents pushing an empty list before the product sync has run.
