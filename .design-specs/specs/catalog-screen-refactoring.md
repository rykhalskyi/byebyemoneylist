# CatalogScreen Refactoring Technical Plan

## Overview

**File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/CatalogScreen.kt`  
**Current Size**: 412 lines  
**Problem**: Single monolithic screen handling 3 distinct entity types (Categories, Stores, Products) with mixed concerns  
**Goal**: Split into smaller, focused components with clear separation of concerns

---

## Current Architecture Analysis

### CatalogScreen.kt (412 lines)
- Main scaffold with AppBar and FAB
- Tab navigation (3 tabs)
- Search functionality (search mode toggle)
- 3 inline tab composables: CategoryListTab, StoreListTab, ProductListTab
- 3 dialogs: CategoryDialog, StoreDialog, ProductDialog
- Delete confirmation dialog
- Snackbar/undo handling
- Shared EntityListItem component

### CatalogViewModel.kt (338 lines)
- Single ViewModel managing all 3 entity types
- CRUD operations for each entity
- Search filtering across all entities
- Dialog state management
- Delete confirmation and undo logic

### Related Files
- `CategoryDialog.kt` (137 lines) - standalone
- `StoreDialog.kt` (144 lines) - standalone
- `ProductDialog.kt` (183 lines) - standalone

---

## Refactoring Strategy

### Phase 1: Extract Tab Components (Separation of UI)

Create independent, reusable tab components that encapsulate their own UI state:

1. **CategoriesTab.kt** (~120 lines)
   - Contains: CategoryListTab + CategoryDialog
   - Manages: category dialog visibility, editing state
   - Receives: categories list, callbacks for edit/delete/save
   - Exposes: onEditCategory, onDeleteCategory, onSaveCategory

2. **StoresTab.kt** (~100 lines)
   - Contains: StoreListTab + StoreDialog
   - Manages: store dialog visibility, editing state
   - Receives: stores list, categories list, callbacks
   - Exposes: onEditStore, onDeleteStore, onSaveStore

3. **ProductsTab.kt** (~130 lines)
   - Contains: ProductListTab + ProductDialog
   - Manages: product dialog visibility, editing state
   - Receives: products list, categories list, callbacks
   - Exposes: onEditProduct, onDeleteProduct, onSaveProduct

**Benefits**:
- Each tab is independently testable
- Can reuse tabs in other screens if needed
- Clear ownership of dialog state
- Reduced cognitive load per file

---

### Phase 2: Extract Tab Navigation Container

Create `CatalogTabs.kt` (~80 lines) that:
- Holds the `SecondaryTabRow` with 3 tabs
- Manages `selectedTab` state (Int)
- Contains the FAB with conditional actions based on selected tab
- Delegates content to the active tab component
- Receives: all entity lists, ViewModel callbacks
- Exposes: onTabSelected

**Benefits**:
- Tab navigation logic isolated
- FAB behavior centralized
- CatalogScreen becomes much simpler

---

### Phase 3: Simplify CatalogScreen

Refactor `CatalogScreen.kt` to ~100 lines:
- Keep `Scaffold` with `CenterAlignedTopAppBar`
- Manage search state (`searchActive`, `searchQuery`)
- Show/hide search AppBar based on `searchActive`
- Delegate main content to `CatalogTabs` component
- Handle snackbar/undo at top level (shared concern)
- Show delete confirmation dialog

**Benefits**:
- Single responsibility: app shell + search + global snackbar
- Easy to understand top-level flow
- All complexity moved to child components

---

### Phase 4: Extract Shared Components

Move these from CatalogScreen to separate files:

1. **EntityListItem.kt** (~50 lines)
   - Already a private composable in CatalogScreen
   - Extract to standalone file in `ui/components/`
   - Reusable by all tabs

2. **EmptyState.kt** (~30 lines)
   - Already a private composable in CatalogScreen
   - Extract to standalone file
   - Reusable across the app

**Benefits**:
- Better reusability
- Easier to test in isolation
- Consistent empty states

---

### Phase 5: ViewModel Considerations

**Recommendation: Keep Single ViewModel (Option A)**

**Why**:
- Already well-organized with clear separation of concerns
- All data operations are repository-based and independent
- Search filtering logic is shared and already works well
- Splitting would require significant state coordination
- Lower risk of breaking existing functionality

**Minor Improvements**:
- Extract search filtering logic to a separate `CatalogSearchFilter` class
- Add extension functions or helper methods for tab-specific operations
- Keep as-is for now, refactor only if needed later

---

## Detailed Implementation Steps

### Step 1: Extract EntityListItem & EmptyState
1. Create `EntityListItem.kt` with the current implementation
2. Create `EmptyState.kt` with the current implementation
3. Update imports in CatalogScreen to use new files
4. Run tests to ensure no regressions

### Step 2: Create CategoriesTab
1. Create `CategoriesTab.kt` in `ui/components/`
2. Move `CategoryListTab` and `CategoryDialog` from CatalogScreen
3. Add internal state for dialog visibility and editing item
4. Expose callbacks: `onEdit`, `onDelete`, `onSave`
5. Remove CategoryDialog from CatalogScreen

### Step 3: Create StoresTab
1. Create `StoresTab.kt`
2. Move `StoreListTab` and `StoreDialog`
3. Add internal state for dialog and editing
4. Expose callbacks: `onEdit`, `onDelete`, `onSave`
5. Remove StoreDialog from CatalogScreen

### Step 4: Create ProductsTab
1. Create `ProductsTab.kt`
2. Move `ProductListTab` and `ProductDialog`
3. Add internal state for dialog and editing
4. Expose callbacks: `onEdit`, `onDelete`, `onSave`
5. Remove ProductDialog from CatalogScreen

### Step 5: Create CatalogTabs
1. Create `CatalogTabs.kt`
2. Implement tab row with `SecondaryTabRow`
3. Add `selectedTab` state
4. Implement FAB with conditional actions
5. Use `when(selectedTab)` to show appropriate tab component
6. Pass all required data and callbacks to tab components

### Step 6: Refactor CatalogScreen
1. Remove all tab-specific code (CategoryListTab, StoreListTab, ProductListTab)
2. Remove all dialog code (CategoryDialog, StoreDialog, ProductDialog)
3. Keep only: Scaffold, AppBar (with search), CatalogTabs, Snackbar, DeleteConfirm
4. Pass ViewModel state and callbacks to `CatalogTabs`
5. Keep snackbar/undo handling at this level

### Step 7: Testing & Validation
1. Run all existing tests
2. Manually test each tab's CRUD operations
3. Test search functionality
4. Test delete/undo flow
5. Verify no regressions

---

## File Structure After Refactoring

```
app/src/main/java/com/otakeeesen/byebyemoneylist/ui/
├── components/
│   ├── CatalogScreen.kt          (~100 lines) - main scaffold
│   ├── CatalogTabs.kt            (~80 lines)  - tab navigation + FAB
│   ├── CategoriesTab.kt          (~120 lines) - category tab + dialog
│   ├── StoresTab.kt              (~100 lines) - store tab + dialog
│   ├── ProductsTab.kt            (~130 lines) - product tab + dialog
│   ├── CategoryDialog.kt         (existing, 137 lines)
│   ├── StoreDialog.kt            (existing, 144 lines)
│   ├── ProductDialog.kt          (existing, 183 lines)
│   ├── EntityListItem.kt         (new, ~50 lines)
│   └── EmptyState.kt             (new, ~30 lines)
└── viewmodel/
    └── CatalogViewModel.kt       (unchanged, 338 lines)
```

**Total Lines**: ~1200 lines across 10 files (avg 120 lines/file)

---

## Data Flow

```
CatalogScreen
├── Manages: searchActive, searchQuery, snackbarHostState
├── Delegates to: CatalogTabs
│   ├── Manages: selectedTab
│   ├── Contains: CategoriesTab | StoresTab | ProductsTab
│   │   ├── Manages: dialog state, editing item
│   │   ├── Displays: EntityListItem list
│   │   ├── Shows: respective Dialog
│   │   └── Calls: ViewModel callbacks
│   └── FAB: conditional based on selectedTab
└── Shows: Delete confirmation dialog, Snackbar
```

---

## Migration Checklist

- [ ] Extract EntityListItem.kt
- [ ] Extract EmptyState.kt
- [ ] Create CategoriesTab.kt
- [ ] Create StoresTab.kt
- [ ] Create ProductsTab.kt
- [ ] Create CatalogTabs.kt
- [ ] Refactor CatalogScreen.kt
- [ ] Update all imports
- [ ] Run lint checks
- [ ] Run unit tests
- [ ] Manual UI testing
- [ ] Code review

---

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing UI behavior | High | Test after each extraction step |
| State management issues | Medium | Keep ViewModel as single source of truth |
| Dialog state duplication | Medium | Ensure each tab manages its own dialog state correctly |
| Search filtering breaks | High | Verify search works across all tabs after refactoring |
| Undo functionality breaks | Medium | Keep snackbar/undo at CatalogScreen level, test thoroughly |

---

## Success Criteria

1. All files < 200 lines (except dialogs which are already separate)
2. CatalogScreen.kt reduced from 412 to ~100 lines
3. No behavioral changes - all existing functionality preserved
4. All existing tests pass
5. Code is more modular and testable
6. New components can be reused independently

---

## Alternative Considered: Split ViewModels

**Approach**: Create CategoryViewModel, StoreViewModel, ProductViewModel  
**Rejected Because**:
- Requires significant state coordination between ViewModels
- Search filtering becomes more complex (need to aggregate)
- More files and boilerplate
- Higher risk of breaking existing functionality
- Current ViewModel is already well-structured

**Future Consideration**: If any tab becomes significantly more complex, extract its ViewModel separately.

---

## Estimated Effort

- Extraction of shared components: 30 minutes
- Creating tab components: 2 hours
- Creating CatalogTabs: 45 minutes
- Refactoring CatalogScreen: 45 minutes
- Testing and validation: 1 hour
- **Total**: ~4.5 hours

---

## Notes

- All dialogs (CategoryDialog, StoreDialog, ProductDialog) are already separate files and can remain unchanged
- EntityListItem and EmptyState are currently private to CatalogScreen - they need to be made `internal` or `public`
- The ColorPicker inside CategoryDialog can stay as is (it's already a separate composable)
- No changes to ViewModel are required for this refactoring
- Maintain backward compatibility - no API changes to ViewModel
