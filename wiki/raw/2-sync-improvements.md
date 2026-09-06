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

### Products sync

- Sync also prices. Prices sync android -> server
- Check dates of purchase. is it possible on server to determine real purchase date of the list items?
