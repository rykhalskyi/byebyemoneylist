---
name: code-review
description: Code review guide for the Bye-bye Money List Android app (Kotlin + Jetpack Compose + Room, MVVM). Use when reviewing pull requests, code changes, or conducting code reviews in this repo.
---

# Code Review — Bye-bye Money List

## Stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Navigation Compose · Room (KSP) · kotlinx.serialization · Coroutines/Flow · CameraX + ML Kit + Gemini/SiliconFlow (receipt scanning) · Coil · MPAndroidChart · OkHttp

## Process

1. Read the PR description and any linked wiki spec/plan (`wiki/pages/specs`, `wiki/pages/plans`).
2. Scope: >400 changed lines → ask to split.
3. Verify it builds and tests pass: `./gradlew compileDebugKotlin`, `./gradlew test`, `./gradlew assembleDebug` (run `./gradlew lint` for touched files).
4. Line-by-line review against the checklists below.
5. Decide: ✅ Approve · 💬 Comment · 🔄 Request Changes.

## Severity labels

🔴 `[blocking]` must fix · 🟡 `[important]` should fix · 🟢 `[nit]` optional · 💡 `[suggestion]` alternative · 🎉 `[praise]`

## Project conventions (must match)

### Architecture
- MVVM with **manual DI** — no Hilt/Dagger. Dependencies are `lazy` singletons on `ByeByeMoneyApplication`; ViewModels resolve them via a `companion object` `ViewModelProvider.Factory` reading `CreationExtras[APPLICATION_KEY]`.
- Each ViewModel exposes a `data class XxxUiState` through `MutableStateFlow` → `asStateFlow()`. Never expose the mutable flow. Composables read it with `collectAsStateWithLifecycle()`.
- Business logic lives in repositories (`data/local/repository/`), not in ViewModels or composables.
- One repository per aggregate (Category, Store, Product, Price, ShoppingList). DAOs are private to their repository.

### Room
- Entities in `data/local/entity/`, DAOs in `data/local/dao/`.
- Reactive reads return `Flow`; one-shot reads are `suspend` and wrap DAO calls in `withContext(Dispatchers.IO)`.
- Any schema change: bump `@Database(version = N)`, add a `Migration(N-1, N)`, and register it in `.addMigrations(...)`. `exportSchema = true` is set — keep it on.
- Multi-DAO writes use `@Transaction`.

### Coroutines
- `viewModelScope` only — no `GlobalScope`, no detached `Job()`.
- Rethrow `CancellationException`; don't swallow it in a broad `catch (e: Exception)`.
- `Dispatchers.IO` for DB/network/file I/O, `Dispatchers.Default` for CPU work.

### Money
- Amounts are `Double` (Room `REAL`). Display only through `CurrencyFormatter.format(price, context)`. Watch precision/rounding on division, discounts, and trend percentages.

### Compose
- Stateless reusable composables; hoist state to the ViewModel.
- Side effects in `LaunchedEffect` / `remember`, never directly in the composable body.
- Stable params, `key` in `LazyColumn` items, `remember` for lambdas passed down.

## Review checklist

**Logic & correctness**
- Null safety (no `!!`), empty-collection cases, off-by-one.
- Date/time via `java.time` (`YearMonth`, `ZoneId`) — already used across the app.

**Security**
- No hardcoded keys/secrets. The API key flows from `local.properties` → `BuildConfig.SILICON_FLOW_KEY`; never commit `local.properties`.
- Scanner/LLM input is untrusted — validate parsed receipt items before insert.
- No PII or receipt data in logs.

**Performance**
- No N+1: no per-item DAO queries inside loops.
- Watch Flow collection loops that recompute on every emission (e.g. `collect { refreshWidgetData() }`).
- Bitmaps/images via Coil; no heavy decode on the main thread.

**Maintainability**
- No dead code, commented-out blocks, or stray `TODO` in production.
- Clear names, single responsibility, no oversized composables or ViewModels.
- New files follow the existing package layout (`data/`, `ui/components/<feature>/`, `ui/viewmodel/`, `util/`).

**Testing**
- Unit tests in `app/src/test` (JUnit4 + `kotlinx-coroutines-test` + `mockito-kotlin`).
- Instrumented in `app/src/androidTest` (Room testing / Compose UI test).
- Logic-heavy code (calculators, matchers, sync engine) should have unit tests.

## Verdict

✅ Approve · 💬 Comment (minor, can merge) · 🔄 Request Changes (blocking issues)
