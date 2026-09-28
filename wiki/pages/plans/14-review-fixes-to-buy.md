---
created: 2026-09-28
type: plan
status: implemented
tags: [review, to-buy, ci, coroutines, localization, privacy]
related:
  - "10-to-buy-list-redesign.md"
  - "11-add-list-dialog-fab-redesign.md"
  - "12-to-buy-auto-check.md"
  - "13-to-buy-widget.md"
summary: Fix the blocking and important findings from the review of branches/jaro/to-buy-list-redesign vs main (red unit test, swallowed CancellationException, unlocalized list title, shared-list sync intent, receipt logging, missing translations, bundled scanner change).
---

# 14. To Buy Redesign — Review Fixes (Blocking & Important)

Remediates the review of `branches/jaro/to-buy-list-redesign` against `main`. Fixes are on a
new branch `fix/to-buy-review-findings` cut from the feature branch, then merged back.

Related plans: [10. To Buy List Redesign](10-to-buy-list-redesign.md),
[11. Add List Dialog & SpeedDialFab](11-add-list-dialog-fab-redesign.md),
[12. Auto-check To Buy after Purchase](12-to-buy-auto-check.md),
[13. To Buy Widget & Screen](13-to-buy-widget.md).

## Findings addressed

| # | Severity | Finding | Fix phase |
|---|---|---|---|
| B1 | 🔴 blocking | `testDebugUnitTest` red: stale `ToBuyAutoMatcherTest` consent case (326 tests, 1 fail) | Phase 1 |
| B2 | 🔴 blocking | `CancellationException` swallowed in `ToBuyAutoMatcher` + `ShoppingListRepository` | Phase 2 |
| I3 | 🟡 important | `createToBuyList()` hardcodes English title, uses `SimpleDateFormat`, non-atomic | Phase 3 |
| I4 | 🟡 important | Shared-list sync forces lists to To Buy; misleading "active" comment; asymmetric merge | Phase 4 |
| I5 | 🟡 important | Scanner logs `reasoning_content` (possible receipt/PII leakage) | Phase 5 |
| I6 | 🟡 important | New strings missing in `values-de` / `values-uk`; unused `add_subscription` | Phase 6 |
| I7 | 🟡 important | Unrelated default-model/timeout/`enable_thinking` change bundled in the branch | Phase 7 |
| S1 | ⚠️ process | 67 files / +5294 in one branch (guide says split >400 lines) | Phase 8 |

## Decisions

- **D1 — Consent gate (B1): keep "no consent required".** Commit `f204d16` ("No need consent in
  this case") deliberately removed `isLlmConsentGranted()` from `ToBuyAutoMatcher`; that consent
  belongs to the analytics AI Assistant. The auto-check only runs when the user has already
  configured an *active* LLM profile. So we align the test + plan 12 with the code, not the code
  with the test. *(Rejected alternative: re-add the gate. Revisit if the team wants explicit
  opt-in for silent LLM calls — track as a separate decision.)*
- **D2 — Shared lists land as To Buy (I4).** `SyncListDto` only carries title + item
  name/aliases/quantity/checked/position (no price/store/category), so a new shared list is
  materially a planning list. Keep `NEED_TO_BUY`, but fix the comment and confirm `isActive = false`
  is intended (only `createToBuyList()` activates). Adding `kind` to the DTO is deferred.
- **D3 — List title is localized at creation time (I3).** The title is a user-visible list name.
  Pass a localized prefix (`R.string.to_buy`) from the UI/ViewModel into the repository; the repo
  owns date formatting.

---

## Phase 1 — Unblock the build (B1)

**Files:** `app/src/test/java/com/otakeeesen/byebyemoneylist/ToBuyAutoMatcherTest.kt`,
`wiki/pages/plans/12-to-buy-auto-check.md`.

1. Delete `returns empty and does not call llm when consent not granted` (`ToBuyAutoMatcherTest.kt:53`).
2. Remove the now-dead `whenever(prefs.isLlmConsentGranted())` stubs (`:23`, `:43`, `:71`).
3. Add a regression test locking in D1: with an active profile and no consent stub, `match`
   calls the LLM once and returns the matched ids.
4. Update plan 12: drop "`+ isLlmConsentGranted()`" from Decisions #2 and the Design bullet; add a
   note that the gate was removed in `f204d16`.
5. Verify `./gradlew testDebugUnitTest` is fully green (327/327).

## Phase 2 — Coroutine cancellation hygiene (B2)

**Files:** `data/agent/ToBuyAutoMatcher.kt:51`, `data/local/repository/ShoppingListRepository.kt:212`.

1. Add `import kotlinx.coroutines.CancellationException` and split each catch:

   ```kotlin
   } catch (e: CancellationException) {
       throw e
   } catch (e: Exception) {
       // best-effort
   }
   ```
2. Add a `ToBuyAutoMatcherTest` case: generator throws `CancellationException` → `match` rethrows.
3. Verify existing auto-check tests stay green.

## Phase 3 — `createToBuyList()` title + atomicity (I3)

**Files:** `data/local/repository/ShoppingListRepository.kt:675`,
`ui/viewmodel/ShoppingListViewModel.kt:676`, `ui/viewmodel/ToBuyViewModel.kt:196`,
`ui/components/shoppinglist/ShoppingListsScreen.kt:589`,
`ui/components/tobuy/ToBuyScreen.kt`, tests in `to-buy`/repository suites.

1. Change signature to `suspend fun createToBuyList(prefix: String): Long`.
2. Build the title with `java.time`:
   `"$prefix " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))` (drop
   `SimpleDateFormat`).
3. Wrap `deactivateAllToBuyLists()` + insert in `database.withTransaction { }` (room-ktx is already
   a dependency) so the active flag can never be left dangling.
4. Thread a localized prefix through:
   - `ShoppingListsScreen` → `val toBuyPrefix = stringResource(R.string.to_buy)` →
     `viewModel.createToBuyList(carryOverNames, toBuyPrefix)`.
   - `ShoppingListViewModel.createToBuyList(carryOverNames, prefix, onCreated)`.
   - `ToBuyScreen`/`ToBuyViewModel.createActiveList(prefix, onCreated)`.
5. Update tests that mock/verify `createToBuyList()` (`ShoppingListViewModelToBuyTest`,
   `ToBuyViewModelTest`) and the instrumented `ShoppingListRepositoryTest` (pass a prefix, assert
   the title starts with it).
6. Keep `R.string.to_buy` as the prefix (translated in Phase 6); do not add a new string.

## Phase 4 — Shared-list sync intent (I4)

**Files:** `data/sync/ListSyncEngine.kt:198-237`, `wiki/pages/plans/12-to-buy-auto-check.md`/this plan.

1. Fix the misleading comment "lands as an active To Buy list" → a shared list is created as a
   To Buy list with `isActive = false` (only `createToBuyList()` activates one).
2. Confirm D2 with the requester. If confirmed, add a one-liner to `decisions.md` (`D-nn`) and leave
   behaviour as-is.
3. If preservation is later required, add optional `kind: String? = null` to `SyncListDto` and honor
   it in `createLocalListFromDto`, falling back to `NEED_TO_BUY`. **Do not** include this in the fix
   branch unless requested — it changes the share-file format.
4. Add a focused instrumented test asserting a created shared list is `NEED_TO_BUY` and
   `isActive = false`.

## Phase 5 — Scanner logging (I5)

**Files:** `ui/components/scanner/SiliconFlowScanner.kt:203`.

1. Stop logging `reasoning` text. Log metadata only:
   `Log.e(TAG, "Empty content from SiliconFlow. finish_reason=${choice.finish_reason}, reasoningChars=${reasoning?.length ?: 0}")`.
2. Keep `reasoning_content` parsed (needed for the `reasoning-only` error message), just never log it.

## Phase 6 — Localization completeness (I6)

**Files:** `res/values/strings.xml`, `res/values-de/strings.xml`, `res/values-uk/strings.xml`.

1. Add de/uk translations for keys used by the redesign:
   `to_buy`, `filter_to_buy`, `filter_purchases`, `active_list_badge`, `add_item`, `item_name`,
   `add_to_list`.
2. Remove `add_subscription` from all three locales — declared but never referenced (leftover from
   the old 4-action FAB).
3. Remove the now-dead `enter_store_mode` / `exit_store_mode` from all three locales (no code refs).
4. Confirm no remaining unused keys via `grep -rn "<key>" app/src/main`.

## Phase 7 — Separate the scanner/model change (I7)

**Files:** `data/LlmProfile.kt`, `ui/components/scanner/SiliconFlowScanner.kt` (`enable_thinking`).

1. Extract the `LlmpProfile` default-model/timeout change and the `enable_thinking=false` addition
   into a dedicated commit (they belong to the 2026-09-27 reasoning-model fix, not the To Buy
   redesign). If they must ship in this branch, call them out explicitly in the PR description.
2. Add a one-line rationale (`decisions.md` or the scanner `## Updates` entry) for switching the
   default SiliconFlow model to `deepseek-ai/DeepSeek-V4-Flash-Vision-Exp` and for disabling
   thinking globally.

## Phase 8 — Split the branch (S1, process)

No code change; separate the commits into reviewable PRs:
1. Data model + migration 29→30 + repository/DAO.
2. UI redesign (card, screen, AddListDialog, FAB) + strings.
3. LLM auto-check (`LlmTextGenerator`, `ToBuyAutoMatcher`, scheduling, tests).
4. To Buy widget + dedicated screen + navigation.
5. Scanner/model fix (Phase 7).

---

## Verification

- `./gradlew testDebugUnitTest` — all green (target 327/327; was 326/1 fail).
- `./gradlew assembleDebug`.
- `./gradlew compileDebugAndroidTestKotlin`.
- Device/emulator: `MigrationTest.migrate29To30`, `ShoppingListRepositoryTest`,
  `ShoppingListCardTest`, `SpeedDialFabTest`, `AddWidgetDialogTest`, `ToBuyScreenTest`,
  `AddListDialogTest`.
- Manual smoke: create/rename/delete To Buy items in all three locales; verify the created list
  title is localized; confirm a shared list arrives inactive; confirm auto-check still runs after a
  purchase.

## Risks

- **Signature ripple (Phase 3):** `createToBuyList` is called from two ViewModels and mocked in
  several tests; keep a default prefix to minimise churn if preferred.
- **Title frozen at creation locale (Phase 3):** acceptable; a locale change won't rename old lists.
- **Consent decision (Phase 1):** D1 keeps silent LLM calls with only an active profile as the gate;
  revisit as a product/privacy decision.
- **DTO change (Phase 4):** deferred to avoid a share-format compatibility break.

## Optional (nits, not required)

- Add `ORDER BY createDate DESC, id DESC` to `ShoppingListDao.getActiveToBuyList()`
  (`ShoppingListDao.kt:65`).
- Remove the unused `activateToBuyList(id)` DAO method (`ShoppingListDao.kt:71`).
- Remove the unused `val elevation` local in `ShoppingListsScreen.kt:486`.
- Coalesce the two `refreshWidgetData()` collectors in `DashboardViewModel.kt:92-100`.

## Outcome

Implemented on branch `fix/to-buy-review-findings` (cut from `branches/jaro/to-buy-list-redesign`,
changes uncommitted).

- **P1 (B1):** `ToBuyAutoMatcherTest` consent case replaced with `matches with an active profile even
  when consent is not granted`; unused consent stubs removed; plan 12 text updated.
- **P2 (B2):** `CancellationException` rethrown in `ToBuyAutoMatcher.match` and
  `ShoppingListRepository.scheduleToBuyAutoMatch`; new `rethrows cancellation from the llm call` test.
- **P3 (I3):** `createToBuyList(prefix)` now uses `java.time` and a localized prefix passed from the
  UI (`R.string.to_buy`); atomic deactivate+insert via a new `ShoppingListDao.insertActiveToBuyList`
  `@Transaction` default method (instead of `RoomDatabase.withTransaction`, which breaks the
  Mockito-mocked `AppDatabase` in JVM tests). Callers/tests updated.
- **P4 (I4):** misleading "active To Buy list" comment corrected; behaviour left as `NEED_TO_BUY` +
  `isActive = false` (decision D2).
- **P5 (I5):** scanner logs `reasoningChars` length only, not receipt text.
- **P6 (I6):** added de/uk translations for `to_buy`, `filter_to_buy`, `filter_purchases`,
  `active_list_badge`, `add_item`, `item_name`, `add_to_list`; removed unused `add_subscription` and
  the dead `enter_store_mode`/`exit_store_mode` from all three locales.
- **Optional nits:** `getActiveToBuyList()` now orders; unused `activateToBuyList` DAO method and the
  unused `elevation` local removed.
- **Verification:** `testDebugUnitTest` 327/327 green (was 326/1 fail); `assembleDebug` and
  `compileDebugAndroidTestKotlin` green.

Deviations: P4's device-gated instrumented test was not added (no `ListSyncEngine` test harness);
P7/P8 involve git history and were left for explicit go-ahead (no commits made).

## Updates
- [2026-09-28]: Executed on `fix/to-buy-review-findings`. All blocking + important code fixes applied
  and verified; optional nits done. P7 commit-split and P8 branch-split pending an explicit request
  (require commits/rewrites).

