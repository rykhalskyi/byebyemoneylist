---
created: 2026-09-22
type: plan
tags: [shopping-list, placeholders, reconciliation, llm, sync]
related:
  - "../specs/03-placeholder-list-items.md"
  - "10-placeholder-list-items.md"
---

# 11. Placeholder Reconciliation — Implementation Plan (Ticket 2)

## Scope
Ticket 2 (P3) of [../specs/03-placeholder-list-items.md](../specs/03-placeholder-list-items.md):
**silent automatic reconciliation** of free-text list items against a purchase, with
optional per-item correction. No review dialog.

When finishing a purchase, open free-text rows are matched to the purchase's items
(LLM-first, deterministic fallback) and applied without user interaction. Ambiguous
rows are left as **Not bought**. The user can later tap a row to re-link or unlink it.

## Design Decisions
- **No dialog.** Auto-apply the best match; unmatched → `matchState = NOT_BOUGHT`.
- **LLM-first**, fallback to `ProductMatcher` when there is no active profile / the
  call fails / low confidence.
- **Conservative**: apply only unique matches above confidence `0.5`; otherwise not
  bought (never guess wrong).
- **One-to-one**: a purchase item is claimed by at most one list row.
- **Display**: matched row keeps the typed text (`customName`) as the title and shows
  the product name next to it; not-bought rows are muted.
- **Manual total-only purchase**: use the checked state — checked free-text rows stay
  as bought (no product link), unchecked become `NOT_BOUGHT`.
- **Correction**: tap a finished-list row → "Matched with" dropdown of the list's
  product-backed rows + "Not bought" (re-link / unlink only).

## Steps

### 1. LLM text seam
- Add `interface TextCompletion { suspend fun complete(system, user): String? }`
  in `data/agent`; `AgentManager` implements it (delegates to `generateText`).
- `ByeByeMoneyApplication` exposes a lazy `agentManager`; `ShoppingListViewModel`
  factory passes it as `TextCompletion?`.

### 2. `PlaceholderReconciler` (new, `data/agent`)
- Input: list of (itemId, name, quantity) + list of (index, name).
- LLM prompt with strict JSON schema `[{listItem, purchasedItem, confidence}]`.
- Parse; drop low-confidence / duplicate claims; fallback `ProductMatcher` on names.
- Returns `Map<Long, Int>` (itemId → purchase index); absent = not bought.

### 3. Repository (`ShoppingListRepository.processPurchase`)
- New param `reconciliations: Map<Long, Int> = emptyMap()`.
- Don't delete placeholders in the finish cleanup (`it.isPlaceholder` excluded).
- For claimed purchase items: update the existing free-text row
  (`productId`, price, discount, quantity, `isPlaceholder = false`,
  `matchState = MATCHED`, keep `customName`) instead of inserting a new row; mark
  the purchase index consumed.
- Unclaimed free-text rows: `matchState = NOT_BOUGHT` (or keep if
  `isChecked` on a manual no-item purchase).

### 4. ViewModel orchestration (`ShoppingListViewModel`)
- `processPurchase`: if `listId != null`, load open free-text rows and run
  `PlaceholderReconciler` against `items`; pass the map to the repository. Run
  inside the existing coroutine; failures degrade to the fallback.
- `relinkItem(itemId, toProductId: Long?, matchState: String?)`: re-link/unlink a row
  (copies price/discount from the chosen row when re-linking; keeps `customName`).

### 5. Display
- `ShoppingListItemWithProduct.linkedProductName` (= `p.name`) added to both DAO
  joins; `PurchaseItem.linkedProductName`.
- `ShoppingListCard`: title = `item.name` (typed text); product name shown next to it
  when `customName != null && productId > 0`; `NOT_BOUGHT` rows muted/struck.

### 6. Correction UI
- `EditPurchaseItemDialog`: optional "Matched with" section for finished lists —
  dropdown of candidate rows + "Not bought".
- `ShoppingListsScreen`: pass the list's product-backed rows as candidates and wire
  `onChangeMatch` → `viewModel.relinkItem`.

## Files
- `data/agent/TextCompletion.kt` (new)
- `data/agent/AgentManager.kt`
- `data/agent/PlaceholderReconciler.kt` (new)
- `ByeByeMoneyApplication.kt`
- `data/local/dao/ShoppingListDao.kt`
- `data/PurchaseItem.kt`
- `data/local/repository/ShoppingListRepository.kt`
- `ui/viewmodel/ShoppingListViewModel.kt`
- `ui/viewmodel/ExportViewModel.kt`, `data/SpendingCalculator.kt` (mapper)
- `ui/components/shoppinglist/ShoppingListCard.kt`
- `ui/components/product/EditPurchaseItemDialog.kt`
- `ui/components/shoppinglist/ShoppingListsScreen.kt`
- `res/values*/strings.xml`
- tests: `PlaceholderReconcilerTest`, repository reconciliation tests

## Risks
- Silent wrong matches → mitigated by the 0.5 threshold + unique-only rule; user can
  correct via the item dialog.
- LLM cost/latency on each purchase that has free-text rows → only runs when such
  rows exist; falls back offline.
- Sync: not-bought rows stay client-local; matched rows sync normally.

## Outcome
Implemented 2026-09-22.

- **Text LLM seam**: `TextCompletion` interface; `AgentManager` implements it;
  `ByeByeMoneyApplication.agentManager` lazily constructed; injected into
  `ShoppingListViewModel`.
- **`PlaceholderReconciler`**: LLM-first with strict JSON (`listItem`/`purchasedItem`/
  `confidence`), unique + threshold 0.5, `ProductMatcher` fallback; returns
  `Map<itemId, purchaseIndex>` (absent = not bought).
- **Repository**: `processPurchase(reconciliations)`; claimed purchase items update the
  free-text row in place (keep `customName`, `isPlaceholder=false`,
  `matchState=MATCHED`); unmatched free-text rows → `NOT_BOUGHT`; unchecked placeholders
  no longer deleted; manual total-only uses the checked state.
- **ViewModel/repo**: `processPurchase` runs `reconcilePlaceholders` before committing;
  `relinkItem(itemId, toItemId?)` re-links/unlinks. Re-linking absorbs the chosen
  product row (copies productId/price/discount, keeps the typed text, then **deletes**
  the separate product row) so the product is not listed twice.
- **Display**: `linkedProductName` added to the DAO projection + `PurchaseItem`;
  `ShoppingListCard` shows `typed → product` and strikes through not-bought rows.
- **Correction UI**: `EditPurchaseItemDialog` gains a "Matched with" dropdown (list's
  product rows + "Not bought") for finished free-text rows; wired from
  `ShoppingListsScreen`.
- **Tests**: `PlaceholderReconcilerTest` (LLM, low-confidence, fallback, one-to-one),
  `PlaceholderReconciliationTest` (manual not-bought, matched row, unmatched row).
  `testDebugUnitTest` + `compileDebugAndroidTestKotlin` green; no new lint errors
  (lint still reports the same 41 pre-existing errors in untouched files).
