---
created: 2026-09-03
type: spec
tags: [nextcloud, sync, categories, stores, products, epic]
related:
  - "../plans/06-nextcloud-sync-redesign.md"
  - "../research/03-nextcloud-sync-redesign.md"
---

# 2. NextCloud Sync Redesign — Feature Spec

## Epic Summary

Complete the Nextcloud synchronisation implementation. Today only Categories
sync is implemented (`NextcloudSyncSettingsScreen` → `CategorySyncScreen`). This
epic:

1. Redesigns the Nextcloud sync settings screen into a single hub that drives all
   sync groups.
2. Adds Store and Product synchronisation alongside the existing Category sync.
3. Makes the match results user-editable (unmatch / re-match) and gives the user
   select-all / deselect-all control over what is uploaded and downloaded.

Source idea: `wiki/pages/research/nextcloud-sync-redesign.md` (raw design notes).

## Requirements

### R1: Settings screen redesign
- Rename button **"Sync Categories Now"** → **"Sync Now"**.
- Align the "Sync Now" button to the right of the row (with "Test Connection"
  on the left).
- Move the **"Use LLM"** checkbox up from `CategorySyncScreen` to
  `NextcloudSyncSettingsScreen` (it becomes a global setting for all groups).
- Under the buttons, show a list of grouped sync parts: **Categories**, **Stores**,
  **Products**.
- Each list item shows, for its group, how many elements are **matched**, how
  many need to be **uploaded to server**, and how many need to be **downloaded
  to client**.
- Tapping a list item opens that group's dedicated sync screen.

### R2: Per-group sync screens
- A screen per group: Categories (existing `CategorySyncScreen`, reworked),
  Stores (new), Products (new).
- On each screen there is **no "Use LLM"** checkbox and **no "Generate Sync Plan"**
  button (plan generation is triggered centrally from the settings screen).
- Upload and Download sections each have **Select all / Deselect all** controls.
- Each **matched** and **unmatched** item is editable:
  - a match can be **deleted** (unlinked), returning both elements to the
    unmatched pools;
  - a new match can be created by selecting an unmatched local element and an
    unmatched server element — selection is restricted to unmatched elements
    only.
- A **"Confirm and sync"** button returns to the settings screen and syncs all
  groups (not only the one being edited).

### R3: Store synchronisation
- Stores are matched the same way as products: **flat / non-hierarchical**,
  by name.
- Server store model contains only `id` + `name` (no logo / address /
  receipt name) — see research page.

### R4: Product synchronisation
- Products are matched flat / non-hierarchical.
- **Barcode is a strong match field** when present.
- Fallback matching: exact name, then aliases, then fuzzy name match.
- Product `categoryId` maps to a server category UUID, so products must be
  synchronised **after** categories (categories must have their `serverId`
  populated first).

### R5: Ordering and execution
- A single "Sync Now" action fetches server data and builds plans for all three
  groups, populating the counts on the settings screen.
- "Confirm and sync" executes all groups in order **Categories → Stores →
  Products**, then returns to the settings screen with the result.

## Design Decisions

- **Generic sync framework + thin per-type configs.** Categories, Stores and
  Products share one plan/matcher/editor abstraction; each type supplies a thin
  config (DAOs, matcher, DTOs, display labels, and — categories only —
  hierarchy handling). See the plan page.
- **Plan generation is centralised** on the settings screen ("Sync Now"), rather
  than per sub-screen. Sub-screens only edit and confirm.
- **"Use LLM" is global** and applies to all groups (see open questions for
  store/product LLM matching scope).
- **Server endpoints already exist** in `~/Source/byebyemoneylist-ns` for stores
  and products (see research page). Stores/products have **no batch endpoint**
  yet — a decision (add batch, or sequential single-create) is tracked in the
  plan.

## Scope

### In Scope
- `NextcloudSyncSettingsScreen` redesign (button, LLM checkbox, grouped list).
- `CategorySyncScreen` rework (remove LLM/generate, select-all/deselect-all,
  editable matches, "Confirm and sync").
- New `StoreSyncScreen` + store sync repository/matcher/DTO.
- New `ProductSyncScreen` + product sync repository/matcher/DTO (barcode strong
  match).
- DB migration adding `serverId` to `stores` and `products`.
- New client API methods for stores/products.
- Generic sync framework (plan, matcher, repository interface, coordinator,
  shared editor UI).
- Shared `NextcloudSyncViewModel` scoped to the settings navigation graph.

### Out of Scope
- Store logo / address / receipt name round-tripping (server model is name-only).
- Product pictures round-tripping.
- Automatic/background sync (the existing `ListSyncEngine` file-folder sync is
  unrelated to this epic).
- Server-side batch endpoints for stores/products (tracked as optional follow-up).

## Constraints

- Single DB migration (24 → 25) adding `serverId` to `stores` and `products`;
  export new Room schema JSON under `app/schemas/`.
- Products must sync after categories; the coordinator enforces ordering.
- Existing category sync behaviour and its tests
  (`MultiLanguageCategoryMatcherTest`, `CategoryHierarchyPushTest`) must stay
  green after the refactor.
- No data loss; `serverId` defaults to `NULL` for existing rows.

## UI/UX

- Settings screen: server config (unchanged), "Use LLM" row, Test Connection
  (left) + "Sync Now" (right), grouped list of Categories/Stores/Products with
  `matched · upload · download` counts, and a "Confirm and sync" action.
- Group screen: matched section (editable), upload section, download section,
  each collapsible with Select all / Deselect all; "Confirm and sync" button at
  the bottom.
