---
created: 2026-08-10
type: plan
tags: [dashboard, category-emojis, issue-50, ux]
related:
  - "../specs/01-dashboard-ux-improvements.md"
  - "../plans/01-dashboard-ux-improvements.md"
---

# 2. Category Emojis (Issue #50) — Implementation Plan

Focused implementation plan for **Issue #50 — Dashboard UX: Emoji icons for categories**.
This is Task 1 of the [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md) epic (see the epic-level
[plans/01-dashboard-ux-improvements](../plans/01-dashboard-ux-improvements.md) for the full three-task plan). This page tracks
the emoji feature end-to-end.

## Summary

Allow assigning an emoji to any category. Emoji **supplements** the existing color
system (shown alongside colored background), is optional, and is stored as a
nullable `TEXT` on `CategoryEntity` (max 2 chars).

## Key Decisions

- **Emoji, not Material Icons** — no extra dependency (`data/CategoryEmoji.kt`).
- **Supplement colors** — emoji + colored background, never replaces color.
- Curated list of ~300 emojis grouped into 10 localized categories (Food, Transport,
  Home, Health, Shopping, Entertainment, Money, People, Nature, Other).
- Group labels are localized via `strings.xml` (EN/DE/UK) using `nameResId`.
- Default categories (`createDefaultCategories`) get seeded emojis for instant value.

## Step-by-Step Implementation

### Phase 1 — Data model + migration
1. `data/local/entity/CategoryEntity.kt` — add `val emoji: String? = null`.
2. `data/local/AppDatabase.kt` — version **20→21**; `MIGRATION_20_TO_21`:
   ```sql
   ALTER TABLE categories ADD COLUMN emoji TEXT;
   ```
   Registered in `.addMigrations(...)`. Room re-exports `schemas/.../21.json`.
3. `data/local/dao/CategoryDao.kt` — no change needed: entity-based `@Insert`/`@Update`
   persist the new column automatically.

### Phase 2 — Save/load plumbing
4. `data/CategoryEmoji.kt` (new) — `object` with `Group(nameResId, emojis)` + `GROUPS` + `ALL_EMOJIS`.
5. `ui/components/category/CategoryDialog.kt` — `onSave` signature extended with
   `emoji: String?`; new `EmojiPicker` composable below `ColorPicker`:
   preview Surface + "Clear emoji" button, `TabRow` of groups, fixed-height
   `LazyVerticalGrid(columns = 8)` of emoji cards.
6. `ui/viewmodel/CatalogViewModel.kt` — thread emoji through `saveCategory`,
   `showEditCategoryDialog`, and `CategoryUiModel` mapping.
7. `ui/model/CategoryUiModel.kt` — add `emoji: String? = null`.
8. `ui/components/catalog/CatalogScreen.kt` — pass emoji in the `CategoryEntity`
   conversions used by `CategoryDialog` and `StoreScreen`.

### Phase 3 — Display emoji alongside color
9. `ui/components/category/CategoryPickerSheet.kt` — picker list items show emoji
   inside the colored circle; `CategoryChipsField` chips label as `"{emoji} {name}"`.
   (This also covers ProductScreen, StoreScreen, CreateShoppingListDialog usages.)
10. `ui/components/catalog/CatalogScreen.kt` — `EntityListItem` gains `emoji` param;
    rendered before the title in `CategoryListTab`.
11. `ui/components/shoppinglist/ShoppingListCard.kt` — list categories' emojis shown
    before the card title.
12. `ui/components/dashboard/widgets/CategoryWidgetCard.kt` — category emoji replaces
    `Icons.Default.Category` when present; `WidgetData.CategorySpending` gains
    `categoryEmoji`, populated via `DashboardRepository.CategorySpendingData` and
    `DashboardViewModel`.
13. `data/local/repository/CategoryRepository.kt` — `createDefaultCategories` seeds
    emojis for default parent/child categories (`DefaultChild` replaces the
    `Pair<Int, String>` child tuple).
14. `res/values|values-de|values-uk/strings.xml` — `select_category_emoji`,
    `clear_emoji`, and 10 `emoji_group_*` labels.

### Phase 4 — Verification
- `./gradlew compileDebugKotlin`, `./gradlew test`, `./gradlew assembleDebug`
- `./gradlew lint` — only pre-existing warnings in touched files; no new issues.
- Room schema `21.json` exported with `emoji TEXT` (nullable).
- Manual smoke: create/edit category with emoji; render in catalog list, chips,
  shopping list card, dashboard category widget; v20→v21 upgrade keeps `emoji = NULL`.

## Deviations from Issue #50 File List

- `CategoriesTab.kt` **does not exist** — the category list is `CategoryListTab`
  inside `ui/components/catalog/CatalogScreen.kt`.
- `CategoryChipsField` lives inside `CategoryPickerSheet.kt`, not its own file.
- Dashboard emoji lives in `CategoryWidgetCard.kt` (DashboardScreen renders widgets
  generically).

## Testing Checklist

- [ ] `./gradlew test` — all unit tests pass (DashboardViewModelTest updated for `CategorySpendingData`).
- [ ] `./gradlew assembleDebug` — APK builds.
- [ ] Migration v20→v21 on device/emulator; existing categories keep `emoji = NULL`.
- [ ] Manual: create category → pick emoji → save → verify persistence on reopen.
- [ ] Manual: emoji renders in catalog, category chips, shopping list card, dashboard widget.

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Emoji rendering gaps on old Android fonts | Low | Common emojis only; test on API 29 |
| `CategoryDialog` grid nested scrolling | Low | Fixed-height `LazyVerticalGrid` (no nested LazyColumn) |
| Existing positional `CategoryEntity(...)` calls (tests) | None | `emoji` has a default value |

## Estimated Effort
~3.5 hours (1h DB/plumbing, 1.5h UI, 1h tests/verification).

## Related
- Epic spec: [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md)
- Epic plan: [plans/01-dashboard-ux-improvements](../plans/01-dashboard-ux-improvements.md)
- Epic research: [research/01-dashboard-epic-analysis](../research/01-dashboard-epic-analysis.md)
- GitHub issue: rykhalskyi/byebyemoneylist#50

## Updates
- [2026-08-10]: Created as focused plan for Issue #50, cross-linked to the Dashboard UX epic pages.
