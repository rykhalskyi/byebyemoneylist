---
created: 2026-09-27
type: plan
status: implemented
tags: [to-buy, purchase, llm, auto-check]
related:
  - "10-to-buy-list-redesign.md"
  - "11-add-list-dialog-fab-redesign.md"
---

# 12. Auto-check To Buy items after a Purchase — Implementation Plan

After every finalized purchase, if an active LLM profile is configured, match the
purchased item names against the pending items of the active **To Buy** list and check off the items the
LLM is confident were bought.

Related: [10. To Buy List Redesign](10-to-buy-list-redesign.md), [11. Add List Dialog & SpeedDialFab Redesign](11-add-list-dialog-fab-redesign.md).

## Decisions (confirmed with requester)

1. **Marking = reuse the existing checkbox.** Matched To Buy items get `isChecked = true`. **No DB
   migration** — `AppDatabase` stays at version 30.
2. **All purchase finalizations trigger matching**, centralized in `ShoppingListRepository.processPurchase`
   (covers the Purchase dialog and the dashboard/quick-purchase flows). Only runs when there are
   purchased item names and pending To Buy items.
3. **Silent, no distinct UI style.** Auto-matched items simply appear checked like a manual check-off.
   No badge, no snackbar.

## Design

- New `LlmTextGenerator` seam so the local repository does not depend on `AgentManager`/its query graph.
  `AgentManager` implements it (its constructor's `executor` became optional, since text generation does
  not need DB queries), reusing the existing provider plumbing.
- New `ToBuyAutoMatcher` (pure, side-effect free): gates on an existing active profile,
  prompts the LLM to map purchased names to pending To Buy ids across
  languages/synonyms, parses `{"matchedIds":[...]}`, filters ids to the supplied pending set. Never throws.
- `ShoppingListRepository` gained optional `toBuyAutoMatcher` + `backgroundScope` constructor params
  (defaulted, so tests/callers are unaffected). `processPurchase` computes the purchased names and
  schedules a fire-and-forget match that checks the matched items — never blocking or failing the purchase.
- `ByeByeMoneyApplication` owns an application `CoroutineScope` and wires the matcher into the repository.

## Purchased-name resolution

- Receipt/entered items present → their non-coupon names.
- Otherwise, finalizing an existing list → the names of its remaining checked, quantity > 0 items.
- Manual total-only new list → empty → nothing scheduled.

## Target Files

**New**
- `data/agent/LlmTextGenerator.kt`
- `data/agent/ToBuyAutoMatcher.kt`
- `app/src/test/.../ToBuyAutoMatcherTest.kt`
- `app/src/test/.../ToBuyAutoMatchRepositoryTest.kt`

**Modified**
- `data/agent/AgentManager.kt` — implements `LlmTextGenerator`; `executor` nullable.
- `data/local/repository/ShoppingListRepository.kt` — matcher wiring + scheduling.
- `ByeByeMoneyApplication.kt` — application scope + matcher.

## Edge cases / risks

- The match runs after persistence; the active list is re-fetched inside the coroutine and each item is
  re-verified (same list, still unchecked) before being checked.
- Failures are swallowed (best-effort); no user-visible errors.
- Uses the existing active-LLM-profile gate (no separate consent prompt; the analytics
  consent covers the AI Assistant only); one LLM call per purchase at most, and only when both lists are
  non-empty (total-only purchases cost nothing).
- False positives are bounded because only ids from the supplied pending set are accepted; matching quality
  still depends on the active model.

## Verification

- `./gradlew testDebugUnitTest` — green (incl. 8 `ToBuyAutoMatcherTest` + 3
  `ToBuyAutoMatchRepositoryTest` cases).
- `./gradlew assembleDebug` — green.
- `./gradlew compileDebugAndroidTestKotlin` — green.

## Updates
- [2026-09-27]: Implemented. Added `LlmTextGenerator` + `ToBuyAutoMatcher`; made `AgentManager.executor`
  optional and implemented `generate` via the existing `generateText`; `ShoppingListRepository` now takes
  optional `toBuyAutoMatcher`/`backgroundScope` and schedules a non-blocking match after
  `processPurchase`; `ByeByeMoneyApplication` wires an application-scoped matcher.
  `./gradlew testDebugUnitTest` (with 11 new tests), `assembleDebug`, and
  `compileDebugAndroidTestKotlin` pass.
- [2026-09-28]: Consent gate removed in `f204d16` (the analytics consent covers the AI Assistant
  only; this feature runs off the active LLM profile). Aligned the stale
  `ToBuyAutoMatcherTest` consent case with that behaviour and updated the decisions/design/edge-case
  text above. See plan 14.
