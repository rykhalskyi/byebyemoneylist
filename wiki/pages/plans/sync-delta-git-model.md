---
created: 2026-09-04
type: plan
tags: [nextcloud, sync, delta, conflict, content-hash, git-model, epic]
related: [[specs/nextcloud-sync-redesign]] [[plans/nextcloud-sync-redesign]] [[research/sync-delta-git-model]]
---

# Sync Delta Model (git-inspired) — Implementation Plan

Phase 2 of the NextCloud sync epic (see [[plans/nextcloud-sync-redesign]]).
Builds on the generic framework from Tickets 1–3 and turns it from a
**create-only reconciliation** into a **git-like delta sync**: matched items are
no longer no-ops — their content is diffed against the last-synced snapshot and
propagated as `PUT`/pull updates, with explicit conflict resolution. Full
rationale and the content-hash design are in
[[research/sync-delta-git-model]].

Implementation order is **4 → 5 → 6**. Prerequisite: Tickets 1–3 of the parent
epic are complete (`serverId` present on categories, stores **and** products —
products land in Ticket 3).

## Architecture

Extend the existing framework rather than replace it:

```
data/sync/model/SyncPlan.kt          → + SyncContentState, SyncConflict; new buckets
data/sync/model/SyncStateEntity.kt   → new entity (sync_state table)
data/sync/SyncStateDao.kt            → new DAO (get/upsert/delete/backfill)
data/sync/SyncProjection.kt          → canonical projection + SHA-256 hasher per type
data/sync/SyncStateResolver.kt       → 3-way compare → annotate plan with states
data/sync/SyncRepository.kt          → executeSyncPlan gains update/conflict lists
ui/components/settings/SyncPlanScreen.kt → modified badges + "Conflicts" section
```

`SyncCoordinator` and ordering (categories→stores→products) are unchanged; the
per-type repositories only gain `PUT` methods and pull-update-by-id logic.

### Core data model

```kotlin
enum class SyncContentState { IN_SYNC, LOCAL_CHANGED, SERVER_CHANGED, CONFLICT }

data class SyncMatch<Local, Server>(
    val local: Local,
    val server: Server,
    val reason: String,
    val contentState: SyncContentState = SyncContentState.IN_SYNC,
)

data class SyncConflict<Local, Server>(
    val match: SyncMatch<Local, Server>,
    val resolvedTo: SyncContentState? = null, // null = unresolved; LOCAL_CHANGED/SERVER_CHANGED once picked
)

data class SyncPlan<Local, Server>(
    val matched: List<SyncMatch<Local, Server>>,   // now includes content state
    val toPushToServer: List<Local>,               // unlinked local (create)
    val toPullToClient: List<Server>,              // unlinked server (create)
    val toUpdateServer: List<Local>,               // LOCAL_CHANGED → PUT
    val toUpdateLocal: List<Server>,               // SERVER_CHANGED → pull/overwrite
    val conflicts: List<SyncConflict<Local, Server>>,
)
```

The update/conflict buckets are derivable from `matched` (filter by
`contentState`); they are kept explicit to mirror the existing push/pull
buckets.

### `sync_state` table

```
sync_state (
    entityType   TEXT    NOT NULL,  -- 'category' | 'store' | 'product'
    localId      INTEGER NOT NULL,
    serverId     TEXT    NOT NULL,
    baseSnapshot TEXT    NOT NULL,  -- canonical JSON of last-synced projection
    lastSyncAt   INTEGER NOT NULL,
    PRIMARY KEY (entityType, localId)
)
CREATE INDEX index_sync_state_serverId ON sync_state (entityType, serverId)
```

`baseSnapshot` is the canonical projection (not a bare hash) so future
field-level merge is possible; hashes are derived from it.

## Ticket Breakdown

### Ticket 4 — Sync-state foundation + change detection

**Migration 25→26**
- `CREATE TABLE sync_state` + index (schema above). Export `26.json`.

**New files**
- `data/sync/model/SyncStateEntity.kt` — Room entity.
- `data/sync/SyncStateDao.kt` — `get(entityType, localId)`,
  `getAll(entityType)`, `upsert`, `delete(entityType, localId)`,
  `deleteByServerId(entityType, serverId)`.
- `data/sync/SyncProjection.kt` — per-type canonical projection builders
  (category/store/product) + `sha256(canonicalJson)`; JSON serialized with
  stable key order.
- `data/sync/SyncStateResolver.kt` — 3-way compare; given a matched pair + the
  serverId map, return the `SyncContentState` (incl. the "converged edit" →
  `IN_SYNC` case).

**Model changes**
- `SyncPlan.kt`: add `SyncContentState`, `SyncConflict`, new buckets, and
  `contentState` on `SyncMatch`.

**Wiring into `generateSyncPlan`**
- After the matcher produces a plan, resolve each matched pair's state via
  `SyncStateResolver` and annotate/fill buckets.
- **Backfill rule** (one-time, for rows already linked pre-upgrade): a matched
  pair with `serverId` but **no** `sync_state` row is baselined as `IN_SYNC` —
  `baseSnapshot` = current *local* projection. This avoids a spurious
  "push everything" on the first run after upgrade. Documented as
  "rebase-on-local" assumption.
- Fresh matches (name/LLM/manual, no persisted link): linking only — set
  `baseSnapshot` = shared projection, no update emitted (matches current
  behaviour).

**Tests**
- `SyncProjectionTest` — canonical JSON stable across local/server forms;
  hash equal when unchanged, differs on rename/recolour.
- `SyncStateResolverTest` — the full 5-case table (incl. converged-edit).
- `MigrationTest` — 25→26 creates `sync_state`.

### Ticket 5 — Update propagation (push + pull)

**API client**
- `NextcloudApiClient`: add `updateCategory(id, ...)`,
  `updateStore(id, name)`, `updateProduct(id, ...)` (PUT). Reuse existing DTOs.

**DAOs / repositories**
- Pull-update helpers: e.g. `CategoryDao.updateFromServer(...)` /
  `StoreDao.updateNameFromServer(...)` / `ProductDao.updateFromServer(...)` that
  overwrite **shared fields only** and leave local-only fields intact.
- `executeSyncPlan` (per type) gains handling:
  1. existing linked-pair `serverId` persistence (unchanged),
  2. create-only pull/push (unchanged),
  3. `toUpdateServer`: `PUT` each, then upsert `sync_state` (base = new
     projection),
  4. `toUpdateLocal`: overwrite shared fields from DTO, upsert `sync_state`.
- **Category ordering**: parents before children for updates, because a parent
  `parentId` move must land before/independently of children; reuse the
  hierarchy ordering approach from `buildHierarchicalPushDtos`.

**UI**
- `SyncPlanScreen` matched rows show a state badge (`IN_SYNC` /
  `LOCAL_CHANGED` / `SERVER_CHANGED` / `CONFLICT`); `LOCAL_CHANGED` and
  `SERVER_CHANGED` items are selectable (checkbox) to include in the sync.
- Modified items keep their "unlink"/"re-match" affordances.

**Tests**
- `CategoryUpdatePushTest` — rename/colour change → `PUT` called, base updated.
- `StoreUpdatePullTest` — remote rename overwrites name, preserves
  `logoPath`/`address`/`receiptName`.
- `ProductUpdatePushTest` — barcode/aliases change → `PUT`, categoryId mapped.

### Ticket 6 — Conflict resolution + asymmetric merge rules

**Conflict detection**
- `CONFLICT` items (both sides changed differently vs base) are excluded from
  auto-push/pull and surfaced in a new **"Conflicts"** section in
  `SyncPlanScreen`.

**Resolution UX**
- Per-row pick: **"Use local"** (mark `resolvedTo = LOCAL_CHANGED` → pushes on
  confirm) or **"Use server"** (`resolvedTo = SERVER_CHANGED` → pulls).
- Resolved conflicts feed back into `toUpdateServer`/`toUpdateLocal` at
  execution time.

**Asymmetric field rules** (finalize here)
- **Store** — name-only sync: push `PUT name`; pull overwrites `name` only.
- **Category** — colour normalised via `toServerColorHex`/`toLocalColorHex`;
  `parentId` expressed as `serverId` in projection and `PUT`.
- **Product** — `aliases[]` ↔ `product_aliases`; server set wins on
  push/pull; `storeId` best-effort (documented non-round-trip).

**Execution**
- On confirm, unresolved conflicts block that group (or are skipped) with a
  warning; resolved conflicts run as their chosen side.
- After resolution, `sync_state` is advanced so the conflict clears.

**Tests**
- `ConflictResolutionTest` — both-changed → conflict; pick-local → push;
  pick-server → pull; base advanced.

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Backfill "rebase-on-local" masks a real remote edit made before upgrade | Medium | Document as accepted one-time assumption; manual re-match available; revisit if reported |
| Colour/parentId projection mismatch → false "changed" (update storm) | High | Canonical projection is unit-tested; projection normalises both sides to server form |
| Pull overwrites local-only fields (store logo etc.) | High | Pull-update helpers touch shared fields only; test asserts local-only fields survive |
| Product `aliases` storeId lost on round-trip | Low | Documented; server aliases treated as canonical shared set |
| Category `parentId` move ordering | Medium | Parents-before-children ordering reused from existing hierarchical push |
| No server `updatedAt` → can't detect server-side edits between fetches | — | Solved by design: content hash vs base snapshot, no timestamps needed |

## Open Questions

1. **Unresolved conflicts on confirm** — block the group, or skip with warning?
   (default: skip + warning, keep the rest running)
2. **Converged-edit auto-advance** — treat identical-both-changed as `IN_SYNC`
   automatically, or surface it for review? (default: auto-advance)
3. **Delete propagation** — deferred; follow-up ticket once server
   soft-delete/`status` semantics are confirmed.

## Testing Checklist

- [ ] `./gradlew compileDebugKotlin` — clean
- [ ] `./gradlew test --tests "*SyncProjectionTest"` — green
- [ ] `./gradlew test --tests "*SyncStateResolverTest"` — green
- [ ] `./gradlew test --tests "*CategoryUpdatePushTest"` — green
- [ ] `./gradlew test --tests "*StoreUpdatePullTest"` — green
- [ ] `./gradlew test --tests "*ProductUpdatePushTest"` — green
- [ ] `./gradlew test --tests "*ConflictResolutionTest"` — green
- [ ] `./gradlew assembleDebug` — APK builds
- [ ] MigrationTest covers 25→26 (`sync_state`)
- [ ] Existing `MultiLanguageCategoryMatcherTest` / `CategoryHierarchyPushTest`
      still green
- [ ] UI: matched rows show state badges; "Conflicts" section appears only when
      both sides changed

## Related

- Research: [[research/sync-delta-git-model]]
- Parent epic: [[specs/nextcloud-sync-redesign]] · [[plans/nextcloud-sync-redesign]] · [[research/nextcloud-sync-redesign]]
