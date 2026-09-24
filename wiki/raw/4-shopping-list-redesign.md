## Epic: Shopping list redesign

### Phase 1: Shopping List Types and States.

Shopping list Types:

1. isNeedToBuy. Completely new type of the list. Contains Only one action button "Add item"
  - has creation date
  - has name: Need To Buy + [date of creation]
  - has a list of items to Buy
  - items are in plain string. No connection with products from the db
  - no price of the list
  - no category of the list
  - no number of item or its price
  - user can Add, Edit, Remove and mark as purchased items in the list.
  - new Add item dialog, user cannot create new product in catalog
  - user can use item form catalog. only items name as string is added to the list.
  - the list cannot be purchased
  - no list edit
  - can be isActive (true/false)
  - only one, the last created list isActive. 
  - If user creates new list it bacomes isActive and previou lost thos flag.
  - the list is shown in the same view as isPurchased lists, the same list of lists, ordred by creation/purchase date. 
  - it has no price so it doesn't take part in calulations.
  - it has own Card design. no category line. 
  - active lost is displayed little more with other colors.

2. isPurchased - our current isFinished list. No changes at all. the same dialogs and features.
3. isSubscription - no changes. keep as is
4. isIncome - no changes. kppe as is

isSubscription and isIncome lists can be isSubscription.

- isNew, isArchived states are deleted completely.
- inStore mode is deleted completely

*Migration*

all isNew lists are converted to isNeedToBuy. Use only items names as plain text.

isFinished -> isPurchased
isSubscription and isIncome - no changes.

*Floating Button*
"Create List" creates new isNeedToBuy list

I need solution how to add new isSubscription list.




