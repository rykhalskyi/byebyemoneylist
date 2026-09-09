## Sync Improvements after Shopping list sync introdution

### ShoppingListSyncScreen

- create ShoppingListSyncScreen similar as for other items in /home/admin/Source/byebyemoneylist/app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/settings
- Click on NextcloudSyncSettingsScreen's ShoppingListItem opens items
- The ShoppingListSyncScreen contain detailed information about synced shopping list 

### Use LLM for Stores and Products

- add LLM driven steps for matching Stores and Products. Fuzzy matching by names.

### Stores sync
- Stores sync contain categories for stores.

### NextcloudSyncSettingsScreen

- Spinners stop to spinn for each item when this item is synced
- User can update any item group separately
- need new names for buttons "Sync Now" and "Confirm and sync". now they are confusing, both say sync but one make request for the last state and other actially saves.

### Products sync

- Sync also prices. Prices sync android -> server
- Check dates of purchase. is it possible on server to determine real purchase date of the list items?


