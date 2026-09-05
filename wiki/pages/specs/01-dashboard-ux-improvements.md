---
created: 2026-08-09
type: spec
tags: [dashboard, quick-purchase, category-icons, default-product, ux, epic]
related:
  - "../plans/01-dashboard-ux-improvements.md"
  - "../research/01-dashboard-epic-analysis.md"
---

# 1. Dashboard UX Improvements — Feature Spec

## Epic Summary
Improve the Dashboard UX with three interconnected features: category emojis,
empty lists with a mandatory category, and a streamlined Quick Purchase flow with
dedicated category-selection screen.

## Requirements

### R1: Category Emoji Icons
- User can assign an emoji to any category when creating or editing
- Emoji picker shows curated, categorized emoji list
- Emoji is displayed alongside the category color in all UI locations
- Emoji is optional — existing categories keep their current display

### R2: Empty List with Category (supersedes Default Product)
- Manual purchase creates an **empty list** — no items are added
- Category is a **mandatory field** on a list
- A list with no items is restricted to **one category**
- Bug fix: manual purchase must not insert the phantom item with
  `productId = 0L` (`ShoppingListRepository.kt:74-76`); the list must be empty
- In analytics, during products-expenses statistics computation, an empty list
  yields a **virtual product "Quick Purchase"** carrying the list's category
  (total = list `finalTotal`)

### R3: Streamlined Quick Purchase Flow
- Dashboard Quick Purchase widget opens a dedicated full-screen flow
- Step 1: Enter purchase amount and optional store name
- Step 2: Select category from a hierarchical 3-column grid of emoji+color buttons
- Income categories are excluded from the grid
- On category tap: an empty purchase list (name, category, store, price) is
  created immediately; user returns to dashboard
- List name is auto-generated: `"Purchase {date}"` with counter for same-day purchases
- Store auto-creation follows existing logic (confirmation dialog for new stores)

### R4: Backward Compatibility
- Existing categories without emoji display normally (emoji is optional)
- Existing lists and products are unaffected (Task 2.a adds no product fields)
- Existing empty finished lists gain the virtual "Quick Purchase" product in analytics automatically
- Existing Quick Purchase widget behavior is replaced by the new flow
- No data loss; single DB migration adds the emoji column only
- Widget deletion (long-press) already works — no changes needed

## Scope

### In Scope
- Category emoji field + emoji picker
- Empty list creation for manual/Quick Purchase (mandatory category, one category while empty)
- Fix for phantom item bug (`productId = 0L`) in `ShoppingListRepository.processPurchase()`
- Virtual "Quick Purchase" product for empty lists in analytics
- New QuickPurchaseScreen with two-step flow
- PurchaseListNameGenerator
- Dashboard widget navigation update

### Out of Scope
- Material Icons (emoji only)
- Default product per category (concept cancelled)
- Multiple categories on an empty list (enforced: one while empty)
- Quick Purchase with receipt scanning (future iteration)
- Editing/changing emoji after purchase association
- Virtual product for income categories

## UI/UX Design

### Emoji Picker
- Dialog with categorized tabs (Food, Transport, Home, Health, Shopping,
  Entertainment, Money, People, Nature, Other)
- Each emoji displayed as a large tappable button
- Selected emoji highlighted
- "Clear" button to remove emoji

### Category Selection Grid (Quick Purchase)
- Full-screen with "Select Category" title
- 3-column grid of category cards
- Each card shows: emoji + colored background + category name
- Hierarchical display: parent categories as section headers,
  children in 3-column grid below each parent
- Tapping a category instantly creates the purchase

### Virtual Product in Analytics
- Empty lists (manual purchase, Quick Purchase) appear in products-expenses
  stats as a virtual product named `"Quick Purchase"`
- Its category is the list's category (`ShoppingListCategoryCrossRef`)
- Its total is the list's `finalTotal`
- Created on-the-fly during analytics computation — not stored in the database

## Constraints
- Single DB migration (v20→v21) adding only the emoji column
- Emoji stored as plain TEXT (SQLite supports Unicode natively, max 2 chars)
- No new Room entities or tables (no `isDefault`, no virtual product storage)
- Reuses existing `ShoppingListRepository.processPurchase()` infrastructure
- Reuses existing widget add/delete system (already implemented)

## Updates
- [2026-08-10]: Task 2 (default product per category) cancelled. Replaced by Task 2.a — empty list with mandatory category, single category while empty, phantom-item (`productId = 0L`) bug fix, and virtual "Quick Purchase" product in analytics for empty lists. Migration reduced to emoji-only.
