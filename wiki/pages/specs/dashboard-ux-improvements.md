---
created: 2026-08-09
type: spec
tags: [dashboard, quick-purchase, category-icons, default-product, ux, epic]
related: [[plans/dashboard-ux-improvements]] [[research/dashboard-epic-analysis]]
---

# Dashboard UX Improvements — Feature Spec

## Epic Summary
Improve the Dashboard UX with three interconnected features: category emojis,
default products per category, and a streamlined Quick Purchase flow with
dedicated category-selection screen.

## Requirements

### R1: Category Emoji Icons
- User can assign an emoji to any category when creating or editing
- Emoji picker shows curated, categorized emoji list
- Emoji is displayed alongside the category color in all UI locations
- Emoji is optional — existing categories keep their current display

### R2: Default Product per Category
- User can designate one product per category as the "default"
- Default products use naming format `"Quick-{CategoryName}"` (e.g. "Quick-Groceries")
- Default products are hidden from the product catalog
- Default products have no independent price history
- Only one default product per category is allowed
- Default product is used in Quick Purchase to enable product-level analytics

### R3: Streamlined Quick Purchase Flow
- Dashboard Quick Purchase widget opens a dedicated full-screen flow
- Step 1: Enter purchase amount and optional store name
- Step 2: Select category from a hierarchical 3-column grid of emoji+color buttons
- Income categories are excluded from the grid
- On category tap: purchase is created immediately; user returns to dashboard
- List name is auto-generated: `"Purchase {date}"` with counter for same-day purchases
- Store auto-creation follows existing logic (confirmation dialog for new stores)

### R4: Backward Compatibility
- Existing categories without emoji display normally (emoji is optional)
- Existing products without isDefault flag default to `false`
- Existing Quick Purchase widget behavior is replaced by the new flow
- No data loss; single DB migration handles both new columns
- Widget deletion (long-press) already works — no changes needed

## Scope

### In Scope
- Category emoji field + emoji picker
- Product isDefault flag + catalog filtering
- Default product management in CategoryDialog
- New QuickPurchaseScreen with two-step flow
- PurchaseListNameGenerator
- Dashboard widget navigation update

### Out of Scope
- Material Icons (emoji only)
- Multiple default products per category
- Quick Purchase with receipt scanning (future iteration)
- Editing/changing emoji after purchase association
- Default product for income categories

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

### Default Product Naming
- Format: `"Quick-{CategoryName}"`
- Examples: "Quick-Groceries", "Quick-Pharmacy", "Quick-Restaurants"
- Auto-created on first use; editable in category dialog

## Constraints
- Single DB migration (v20→v21) combining both column additions
- Emoji stored as plain TEXT (SQLite supports Unicode natively, max 2 chars)
- No new Room entities or tables
- Reuses existing `ShoppingListRepository.processPurchase()` infrastructure
- Reuses existing widget add/delete system (already implemented)
