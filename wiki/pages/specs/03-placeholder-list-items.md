---
created: 2026-09-22
type: spec
tags: [shopping-list, placeholders, llm, scan-list, purchase, analog, epic]
related:
  - "../plans/10-placeholder-list-items.md"
---

# 3. Placeholder Shopping-List Items — Feature Spec

## Epic Summary
A list in the **New** state may hold *placeholder* items — free-text names such as
"Milk", "Bread", "Cheese" that are not backed by a catalog product. At purchase
time the app reconciles placeholders against the actually-bought products, replacing
matches with real products and marking misses as **not bought**. Users can also
scan a handwritten paper list to seed placeholders, mix placeholders with concrete
catalog products, and flag when an **analog** product was bought instead.

This decouples fast, paper-like list entry from strict catalog bookkeeping while
keeping purchases, prices, and analytics catalog-driven.

## Goals
- Let a user type list items as plain text without silently creating catalog products.
- Support creating a New list from a photo of a handwritten list (names + quantities).
- Reconcile placeholders with a receipt/purchase at finish time, primarily via LLM.
- Clearly show which placeholders were **not bought**.
- Let a list mix placeholders and concrete catalog products.
- Mark when a bought product is an **analog** of a listed product.

## Non-Goals
- Server-side / Nextcloud changes in P1 (placeholders are client-local until resolved).
- Prices on placeholders (they contribute 0 until matched).
- Shared-list semantics for placeholders.
- Replacing the existing catalog product CRUD or merge flows.

## Background (current behavior)
- `ShoppingListItemEntity.productId` is a non-null `Long` with **no foreign key**
  (`data/local/entity/ShoppingListEntity.kt:62-73`); `0L` is the established
  "not a real product" sentinel used for coupons (`ShoppingListRepository.kt:87`).
- `customName` already overrides the displayed name via
  `COALESCE(sli.customName, p.name)` (`ShoppingListDao.kt:90`), and when both are
  null the item shows as `"Unknown"` (`ShoppingListViewModel.kt:230`).
- Free-text entry today **always materializes a catalog product**
  (`AddProductViewModel.kt:207-249`), and receipt imports already match names via
  aliases + `ProductMatcher` fuzzy logic, auto-creating products with
  `status = "added"` when nothing matches (`ShoppingListRepository.kt:86-114`).
- A text-only LLM path already exists (`AgentManager.generateText`,
  `data/agent/AgentManager.kt:285`) alongside the vision scanners.
- `ProductAnalogCrossRef` exists (`data/local/entity/ProductAnalogCrossRef.kt`) but
  is only maintained as a side effect of product merge — there is no analog UI.

## Item Model

### Placeholder flag (do not overload `0L`)
`0L` already means **coupon**. Introduce an explicit discriminator instead of
inferring from `productId`:
- Add `isPlaceholder: Boolean` (or an `itemKind` enum) to
  `ShoppingListItemEntity` and `PurchaseItem`, set independently of `productId`.
- `customName` holds the placeholder text and remains the display override.
- New Room migration **29 → 30** (`AppDatabase.kt:45,63`); bump `version` to 30 and
  export `30.json`. Simple `ALTER TABLE ... ADD COLUMN` style.

### Match state
Add a persisted state for the outcome of reconciliation on a finished list:
- `MATCHED` — placeholder resolved to a real `productId`.
- `NOT_BOUGHT` — placeholder survived to a finished list without a purchase match.
- (unset / null) — still an open placeholder on a New list.
This can live as a nullable `matchState` string column added in the same migration;
avoid a new table.

### Quantities
Placeholders carry `quantity` like any item. No `price`/`discount` until matched.

## List Lifecycle
- A list stays in the **New** state while it contains open placeholders.
- Manual list creation with no receipt scan keeps all items exactly as entered
  (placeholders and catalog items alike) — no automatic resolution.
- Finishing with a purchase (receipt scan or manual total) triggers reconciliation:
  - matched → replace with the resolved real `productId` (+ name/price from the
    receipt), clear the placeholder flag;
  - unmatched → set `NOT_BOUGHT` and keep it visible in a "Not bought" section;
  - a manual total-only purchase (no scanned items) leaves placeholders untouched.
- Once resolved, items are ordinary catalog-backed items and follow the normal
  (existing) sync path.

## Flows

### F1 — Add a free-text placeholder manually
- In the per-list Add flow (`AddProductScreen`, `AddProductViewModel.kt:207`), when
  the query does not match a product, offer **"Add as placeholder"** instead of
  always creating a product.
- Keep "Create new product" available as the explicit alternative.
- Placeholder is stored with `isPlaceholder = true`, `customName = text`,
  `productId = 0L` (placeholder sentinel) and is editable (name + quantity).

### F2 — Create a New list by scanning a paper list
- New action "Scan list" from the Shopping Lists screen.
- Reuse the vision LLM pipeline (`CompositeScanner`, `ScannerModels.kt`) with a
  **new list-extraction prompt/schema** returning only names + quantities (no store,
  no prices, no total).
- Result is shown in a review dialog (reuse the `ReceiptReviewDialog` pattern) and,
  on confirm, becomes a New list of placeholders.
- Existing camera/permission plumbing is reused; image EXIF stripping applies.

### F3 — Add a concrete catalog product to a list
- Unchanged behavior: an existing product is linked by `productId` and tracked
  through purchase as today (`AddProductViewModel.kt:182-205`).
- Placeholders and catalog items coexist in the same list.

### F4 — Receipt/purchase reconciliation (LLM-first, no dialog)
- On finishing a New list with a scanned receipt, take:
  - the list's open placeholders (text + quantity), and
  - the purchase's scanned items (names, quantity, price, resolved/created product).
- Match via the LLM (`AgentManager.generateText`, `AgentManager.kt:285`) with a
  dedicated system instruction and a strict JSON schema, e.g.:
  `[{ "listItem": "Milk", "purchasedItem": "Whole Milk 3.5%", "confidence": 0.0-1.0 }]`,
  with a deterministic `ProductMatcher` fallback.
- **No review dialog:** apply matches automatically (unique + confidence ≥ 0.5).
  Ambiguous placeholders stay **not bought** rather than risk a wrong link.
- A matched placeholder becomes a real item (product resolved/created, price from the
  purchase) keeping the typed text as its name, shown as `Milk → Whole Milk 3.5%`;
  unmatched become `NOT_BOUGHT`.
- The user can tap any finished-list row to re-link it to another bought product or
  mark it "not bought" (per-item correction, on demand).
- Manual total-only purchase (no scanned items): use the checked state — checked rows
  stay as bought, unchecked become `NOT_BOUGHT`.

### F5 — Analog marking
- A bought product that matches no placeholder/catalog item is a candidate analog.
- Offer to link it via the existing `ProductAnalogCrossRef`
  (`data/local/entity/ProductAnalogCrossRef.kt`), i.e. "Bought *Analog X* instead of
  *Listed Y*?" — new link UI (currently none exists).
- Analog link points the bought product at the listed product; the listed item is
  treated as satisfied (not "not bought") and the analog relation is recorded.

## Matching Design (LLM-first)
- **Primary:** LLM semantic match of placeholder text ↔ purchase items via the text
  path `AgentManager.generateText` (`AgentManager.kt:285`), using the currently
  selected `LlmProfile` (`data/LlmProfile.kt`).
- **Fallback:** deterministic `ProductMatcher` (`util/ProductMatcher.kt:19`) and
  product aliases when:
  - no LLM profile is configured, or
  - the LLM is unreachable, or
  - the LLM returns low confidence for a given pair.
  This keeps the feature usable offline and without an API key.
- Matching targets **that purchase's scanned items**, not the whole catalog.
- Only unique matches above a confidence threshold are applied, so nondeterministic
  LLM output cannot silently corrupt a list; ambiguous rows become `NOT_BOUGHT` and
  the user can correct any row afterwards (F4).
- Guard against duplicate product creation: reuse the existing
  auto-create-with-alias path and dedupe within the reconciliation loop.

## Sync
- Placeholders are **client-local** and are **not** pushed to Nextcloud while open.
  Rationale: the sync layer assumes a resolvable product —
  `SyncProjection`'s `ShoppingListItemDigest` requires a non-null `productServerId`
  (`data/sync/SyncProjection.kt:166`) and `ListSyncEngine.kt:77` pushes
  `customName ?: productName`.
- Once a placeholder is resolved to a real product (F4/F5), it becomes an ordinary
  synced item and follows the existing shopping-list mirror path.
- `NOT_BOUGHT` unresolved placeholders are also local-only in P1.
- Changing the sync DTO/schema/server to carry product-less text items is explicitly
  out of scope for P1 (see Open Questions).

## UI/UX
- Placeholder rows render like normal items but visually distinguished (e.g. dashed
  border / muted style) and editable as free text.
- Add screen gains "Add as placeholder" alongside "Create new product".
- Shopping Lists screen gains a "Scan list" action.
- Finished lists with misses show a **"Not bought"** section.
- Reconciliation is silent; tapping a row opens a per-item "Matched with" picker
  (bought products + "Not bought") for correction.
- Analog suggestion prompt when a scanned product matches no list item.

## Totals & Analytics
- `itemsTotal` already sums only checked items, so unwon/placeholder items contribute
  0 (`data/ShoppingList.kt:27`); unmatched placeholders must **not** inflate spending.
- `NOT_BOUGHT` items must be excluded from purchase totals and from analytics product
  aggregation.
- Matched items behave exactly like catalog items (price from the receipt/catalog).

## Constraints
- Single Room migration 29 → 30 (adds `isPlaceholder`/`itemKind` + `matchState`);
  no new tables.
- Reuse existing scanner, `ProductMatcher`, `AgentManager.generateText`,
  `ProductAnalogCrossRef`, and receipt-review UI patterns.
- No Nextcloud/server changes in P1.
- LLM calls must be optional and degrade to the deterministic fallback.

## Phasing
- **P1 — Placeholders in New lists:** model + migration, display/edit, manual
  add-as-placeholder, totals exclusion. Local-only.
- **P2 — Scan paper list:** new LLM list prompt/schema + review + create New list of
  placeholders.
- **P3 — Reconciliation:** silent LLM-first matching + deterministic fallback,
  `NOT_BOUGHT` state, per-item re-link correction.
- **P4 — Analog marking:** detect unmatched bought products, link via
  `ProductAnalogCrossRef`, new link UI.

## Open Questions / Risks
- **LLM cost & latency** for matching on every purchase; consider batching and only
  calling when open placeholders exist.
- **Nondeterminism / duplicate products**: mitigated by the confidence threshold +
  unique-only rule (ambiguous → not bought) and the per-item correction.
- **Analog false positives**: require the human suggestion prompt; never auto-link.
- **Sync story for unresolved placeholders**: optional future work to sync
  product-less text items across clients (server DTO + junction changes).
- **Scanner reuse for paper lists**: confirm the vision prompt can reliably separate
  list items from receipt lines and omit prices.

## Updates
- [2026-09-22]: P1 implemented (see plans/10). Add-product free text defaults to
  adding the item directly; "placeholder" is never shown to the user.
- [2026-09-22]: P3 redesigned — no review dialog. Reconciliation is silent
  (LLM-first, unique + confidence ≥ 0.5), ambiguous rows become `NOT_BOUGHT`, and the
  user corrects on demand by tapping a row (per-item "Matched with" picker). Matched
  rows show the typed text plus the product name. Implemented (see plans/11).
