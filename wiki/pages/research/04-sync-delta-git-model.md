---
created: 2026-09-04
type: research
tags: [nextcloud, sync, delta, conflict, content-hash, git-model, epic]
related:
  - "../specs/02-nextcloud-sync-redesign.md"
  - "../plans/06-nextcloud-sync-redesign.md"
  - "../plans/07-sync-delta-git-model.md"
  - "../research/03-nextcloud-sync-redesign.md"
---

# 4. Sync Delta Model (git-inspired) — Research

## Topic

Rethink the Nextcloud sync/matching logic as a **git-like flow**: server is the
remote ("origin"), the local DB is the working tree, and sync is a *diff over
reconciled identity* rather than a one-shot create/create reconciliation. The
concrete problem this solves: **local edits to an already-synced entity are
currently never propagated** — e.g. a category renamed or recoloured locally
shows as "matched" and is silently ignored by the next sync.

## Context

### The gap in the current model

`SyncPlan` has exactly three buckets — `matched`, `toPushToServer`,
`toPullToClient` (`data/sync/model/SyncPlan.kt`). `executeSyncPlan` treats every
**matched** pair as terminal: the only action is persisting `serverId`
(`CategorySyncRepository.kt:79-83`, `StoreSyncRepository.kt:50-52`). There is no
notion of "this entity changed since it was last synced", so:

- a local rename/recolour of a matched category → **never pushed** (no `PUT`);
- a remote rename → **never pulled** (pull only *creates* missing rows);
- both changed → **silently stays divergent** (no conflict, no resolution).

In git terms, the current model only tracks *untracked local files* and *files
new on the remote*; it has no *modified*, no *deleted*, no *conflict* state.

### Why the server can't timestamp this

`~/Source/byebyemoneylist-ns` categories/stores/products have **no `updatedAt` /
`modified` / revision column** (verified in `lib/Entity/*.php`; categories only
carry a `status` string like `pending_review`/`confirmed`). So "remote changed
since my last sync" cannot be derived from server timestamps. The client must
track change itself — which is exactly what git does: **content, not clocks**.

### What the server *does* already support

All three groups expose full CRUD: `PUT` update and `DELETE` exist
(`CategoryController.php:405` `update`, `:470` `destroy`; stores/products per
the API table in [research/03-nextcloud-sync-redesign](../research/03-nextcloud-sync-redesign.md)). The client just never
calls them. So propagating edits requires **no server work** for the core flow.

## Findings

### 1. The git mapping

| Git concept | This app | Status |
|---|---|---|
| remote / origin | Nextcloud API | ✅ |
| blob identity | `serverId` on local entities | ✅ (categories+stores done; products pending in Ticket 3) |
| rename detection | fuzzy/name/LLM matcher | ✅ — but only needed for *unlinked* items |
| untracked local / new on remote | `toPushToServer` / `toPullToClient` | ✅ |
| modified (local) | — | ❌ |
| modified (remote) | — | ❌ |
| conflict (both modified) | — | ❌ |
| deletes | — | ❌ (defer) |
| commits / branches / history | — | not wanted |

### 2. The key simplification: two phases

Once a pair is linked by `serverId`, renames and recolours **do not unlink it** —
identity is stable. The fuzzy/LLM matcher already prefers `serverId` first
(`MultiLanguageCategoryMatcher.kt:68`). Therefore:

- **Phase 1 (reconciliation)** — the existing matcher — establishes *identity*
  (which local ↔ which server, which are create-only). It runs on unlinked
  items and is one-time per pair.
- **Phase 2 (delta)** — for every linked pair, compare content against the
  last-synced snapshot and emit *update* or *conflict* actions.

This is git's "rebase once, then fast-forward": after the first sync, subsequent
syncs are pure deltas, no fuzzy matching re-runs.

### 3. Change detection: content hash + base snapshot (clock-free)

Store, per linked pair, the **last-synced content** as a canonical JSON
snapshot. "Local changed" = hash(local projection) ≠ hash(base). "Remote
changed" = hash(server projection) ≠ hash(base). Both → conflict.

Because both sides are projected into the **same canonical domain** (shared
fields only, references expressed as `serverId`s), equality is meaningful
across the schema boundary.

Canonical projections (shared fields, normalized):

- **Category**: `{ name, income, colorHex(server format), emoji, parentServerId }`
- **Store**: `{ name }` (server is name-only)
- **Product**: `{ name, barcode, categoryServerId, aliases[], isFavorite, status, isSubscription, isIncome }`

`parentServerId` / `categoryServerId` are resolved via the serverId map built
during sync (the same mechanism `buildHierarchicalPushDtos` already uses).

### 4. The 3-way comparison

For a matched pair with a stored base snapshot, compare `localHash` and
`serverHash` against `baseHash`:

| localHash vs base | serverHash vs base | state |
|---|---|---|
| equal | equal | `IN_SYNC` |
| changed | equal | `LOCAL_CHANGED` → push `PUT` |
| equal | changed | `SERVER_CHANGED` → pull/overwrite local |
| changed | changed, different | `CONFLICT` → user resolves |
| changed | changed, same | `IN_SYNC` (converged edit — both made the same change; just advance base) |

The "converged edit" case is a real win over timestamp approaches: two devices
making the *same* rename don't produce a spurious conflict.

### 5. Asymmetric schema is a first-class concern

Git assumes both sides of a file are identical in form. Here they are not, so
merge direction must be explicit per field:

- **Store**: server is `name`-only. Push → `PUT name`. Pull → overwrite
  `local.name` only; `logoPath`/`address`/`receiptName` are local-only and must
  never be clobbered.
- **Category**: `color` hex is converted (`toServerColorHex`/`toLocalColorHex`).
  The *projection* is normalized to server form; local-only form is only a
  display concern.
- **Product**: `aliases[]` on server vs the store-scoped `product_aliases`
  table locally — `storeId` is not round-trippable. Treat server aliases as the
  shared set (push/pull replace); `storeId` is preserved best-effort.

### 6. Deletes are deliberately deferred

Server categories/products carry `status` + confirm flows (soft/trash
semantics) and `DELETE` is destructive; the client has no tombstone concept.
Propagating deletes safely needs a separate design (local soft-delete or
explicit "remove from server" action). **Out of scope for tickets 4–6.**

## Conclusions

- The existing `SyncMatcher` / `SyncRepository` / `SyncCoordinator` framework is
  the right shape and survives; what changes is `SyncPlan` (new buckets) + a new
  `sync_state` table + a canonical-projection hasher, plus `PUT`/pull-update
  execution.
- Do **not** build git internals (commits, DAG, history, branches). Borrow the
  *mental model*: remote + working tree, delta over identity, explicit conflict
  resolution.
- No server timestamps needed; content hash + base snapshot is the correct,
  clock-free change detector and enables the "converged edit" nicety.
- The core problem ("update server data when I have local changes") is solved by
  the *smallest* change: stop treating `matched` as a no-op and emit update
  actions — not by redesigning the architecture.

## Recommended Actions

1. Add a `sync_state` table + `SyncStateDao` (Ticket 4).
2. Add canonical projection + hashing per type (Ticket 4).
3. Extend `SyncPlan`/`SyncMatch` with content state + update/conflict buckets
   (Ticket 4).
4. Add client `PUT` methods + local pull-update and execute update propagation
   (Ticket 5).
5. Add conflict-resolution UI + asymmetric field merge rules (Ticket 6).
6. Defer delete propagation; document as a follow-up.

See [plans/07-sync-delta-git-model](../plans/07-sync-delta-git-model.md) for the ticket breakdown.
