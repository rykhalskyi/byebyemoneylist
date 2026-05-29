# Category Colors Feature Specification

## Overview
This document outlines the implementation plan for adding color marking to categories in the ByeByeMoney shopping list application. Users will be able to assign one of 7 predefined colors to categories, and these colors will be displayed as a colored line on top of shopping list cards when a category is attached.

## Requirements
1. Users can assign one of 7 predefined colors to categories when creating or editing
2. Shopping list cards display a colored line at the top when associated with a category
3. Color selection is intuitive and visually consistent
4. Backward compatibility with existing category data

## Data Model Changes

### CategoryEntity
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/entity/CategoryEntity.kt`

Add a color field to the CategoryEntity data class:
```kotlin
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val color: String // New field for category color
)
```

### Predefined Colors
Define 7 predefined colors:
1. Red - #FF6B6B
2. Blue - #4D9DE0
3. Green - #2D936C
4. Yellow - #FFD93D
5. Purple - #9B5DE5
6. Orange - #FF9F1C
7. Teal - #00C896

## UI Components

### CategoryDialog
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/CategoryDialog.kt`

Update the dialog to include:
- Color picker with 7 predefined color options
- Visual feedback for selected color
- Validation for color selection

### ShoppingListCard
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListCard.kt`

Update the component to:
- Display a colored line at the top of the card when a category is attached
- Show category color in the list view
- Maintain existing functionality

## Implementation Steps

### Phase 1: Data Model Updates
1. Add color field to CategoryEntity
2. Add color field to ShoppingListEntity (if needed)
3. Create color constants

### Phase 2: UI Updates
1. Update CategoryDialog with color selection
2. Update ShoppingListCard with color indicator
3. Update CreateShoppingListDialog to show category colors

### Phase 3: Database Migration
1. Add migration script for existing categories
2. Set default color for existing categories

### Phase 4: Testing
1. Unit tests for color selection
2. UI tests for category dialog
3. Integration tests for shopping list cards

## Technical Details

### Color Constants
```kotlin
object CategoryColors {
    const val RED = "#FF6B6B"
    const val BLUE = "#4D9DE0"
    const val GREEN = "#2D936C"
    const val YELLOW = "#FFD93D"
    const val PURPLE = "#9B5DE5"
    const val ORANGE = "#FF9F1C"
    const val TEAL = "#00C896"
}
```

### Color Picker Component
Create a reusable color picker that:
- Displays 7 color options in a grid
- Shows visual feedback for selected color
- Returns selected color string

## Expected Behavior
1. When creating/editing a category, user can select one of 7 colors
2. When a shopping list is created with a category, the card displays a colored line at the top
3. Color persists across app sessions
4. Default color is assigned to existing categories during migration