## Pre release preparations:

### Legal
- Add Impresum for germany
- Check all Privacy policies

### Cloud sync
- Hide cloud sync behind feature flag and disable it
- Hide in Settings
- Hi Share in Clous in ShoppingListCard

### Fix recurring lists.
**STR:**
- Create recurring list.
- After coping in new period delete copy.

**AR:**
- Deleted copy is restored after app restart.
- it is not possible delete it.

**ER:**
- copy is deleted and original list hasn't be copied anymore


**Proposed soluiton:**
- instead of boolean flag use some integer enum: 0 - normal list, 1 - have to be copied in new period, 2 - copied to the new period.
- newly created recurring list has 1. when time comes it copied innew period, flag of the original list set to 2, flag of the newly created copy - 1;
- if user uncheck list as recurring, this field is set to 0;

- Add tests fro thes feature, copy decsion logic must be incapsulated in separate class.
- test week, month and year copying interval

### Agent
- Create new feature flag and hide Analytics/agent behind it
