# Tasks - Category Colors

- [ ] 1. Fix duplicate code compilation error in ShoppingListViewModel
  - **File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/viewmodel/ShoppingListViewModel.kt`
  - **Description**: Remove duplicated code block inside init{} that causes compilation failure
  - **Leverage**: lines 146-157 duplicate 131-142
  - **Prompt**:
    - **Role**: Android developer
    - **Task**: Remove the duplicate block of code inside the init block (lines 146-157)
    - **Restrictions**: Do not change any other logic
    - **Success Criteria**: File compiles without errors

- [ ] 2. Fix color parsing in CategoryDialog ColorPicker
  - **File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/CategoryDialog.kt`
  - **Description**: Color() constructor expects ARGB int, but toIntOrNull(16) on "#FF6B6B" returns null because of the # prefix, and even without # it would be 0x00FF6B6B (transparent). Need proper hex parsing.
  - **Leverage**: `CategoryColors.kt` for color format reference
  - **Prompt**:
    - **Role**: Android developer
    - **Task**: Fix the ColorPicker Composable so it correctly parses hex color strings like "#FF6B6B" into Compose Color objects
    - **Restrictions**: Use Android's Color.parseColor() or strip # and prepend FF for alpha. Do not change the API of ColorPicker
    - **Success Criteria**: Color circles display correct colors

- [ ] 3. Fix color parsing in ShoppingListCard
  - **File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListCard.kt`
  - **Description**: Same parsing bug as CategoryDialog - categoryColor string is not correctly converted to Compose Color
  - **Leverage**: Same fix pattern as Task 2
  - **Prompt**:
    - **Role**: Android developer
    - **Task**: Fix the category color indicator in ShoppingListCard to correctly parse the hex color string
    - **Restrictions**: Do not change the layout or behavior, only the color parsing logic
    - **Success Criteria**: Category color line displays correctly on cards

- [ ] 4. Add database migration for category color column
  - **File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/AppDatabase.kt`
  - **Description**: Add migration from version 3 to 4 to add `color` column to `categories` table with default value, and update database version
  - **Leverage**: Existing MIGRATION_2_TO_3 pattern
  - **Prompt**:
    - **Role**: Android developer
    - **Task**: Add Room migration 3→4 that adds `color TEXT NOT NULL DEFAULT '#FF6B6B'` column to categories table, update version to 4, register migration
    - **Restrictions**: Do not change existing migrations. Use CategoryColors.DEFAULT_COLOR value as default.
    - **Success Criteria**: Database version is 4, migration is registered

- [ ] 5. Update CategoryRepository.getOrCreate to include default color
  - **File**: `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/repository/CategoryRepository.kt`
  - **Description**: When creating a new category, explicitly set the default color instead of relying on the entity default (which may not apply for existing DB rows without the column)
  - **Leverage**: `CategoryColors.DEFAULT_COLOR`
  - **Prompt**:
    - **Role**: Android developer
    - **Task**: Update getOrCreate() to pass color = CategoryColors.DEFAULT_COLOR when creating a new CategoryEntity
    - **Restrictions**: Minimal change - only the insertCategory call
    - **Success Criteria**: New categories always have a color set
