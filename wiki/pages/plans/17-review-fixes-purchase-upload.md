---
created: 2026-11-10
type: plan
status: implemented
tags: [review, nextcloud, purchase-upload, coroutines, security, tests, compose]
related:
  - "16-purchase-upload-integration.md"
summary: Fix the blocking and important findings from the review of feature/nextcloud-purchase-upload vs main (upload metadata copied onto duplicates/recurrences, swallowed CancellationException, missing client/repository tests, prefs I/O in composition, settings screen DI, cleartext credentials).
---

# 17. Purchase Upload — Review Fixes (Blocking & Important)

Remediates the review of `feature/nextcloud-purchase-upload` against `main`. The branch also
bundles the sync removal, reviewed separately; these fixes only touch the upload feature.

Related plan: [16. Nextcloud Purchase Upload](16-purchase-upload-integration.md).

## Findings addressed

| # | Severity | Finding | Fix |
|---|---|---|---|
| B1 | 🔴 blocking | `uploadId`/`lastUploadedAt` copied onto duplicated and recurring/forwarded lists (same server dedupe key, false "Send again") | Phase 1 |
| I2 | 🟡 important | `CancellationException` swallowed by `PurchaseUploadRepository` and `PurchaseUploadApiClient` | Phase 2 |
| I3 | 🟡 important | No unit tests for the API client (URL/status/parsing) or repository (eligibility/uploadId reuse) | Phase 3 |
| I4 | 🟡 important | `NextcloudUploadSettings` read EncryptedSharedPreferences per list card on the main thread | Phase 4 |
| I5 | 🟡 important | Settings screen built its own `PreferencesManager`, wrote the password on every keystroke, local-only state | Phase 5 |
| I6 | 🟡 important | Basic-Auth app password could be sent over `http://` (local-host auto-downgrade + manifest cleartext) | Phase 6 |

## Decisions

- **D1 — Fresh upload identity per list.** Only the *same* list may reuse its `uploadId`. Any new
  list row produced by duplicate or recurring forwarding starts with `uploadId = null` and
  `lastUploadedAt = null`.
- **D2 — Cancellation is control flow.** `CancellationException` is rethrown before any mapping to
  `PurchaseUploadResult` / `Result.failure`.
- **D3 — Configured flag is state, not a getter.** `ShoppingListUiState.nextcloudConfigured` is
  computed once per list-flow emission on `Dispatchers.IO`; cards no longer read prefs.
- **D4 — Settings state lives in a ViewModel.** `NextcloudUploadSettingsViewModel` uses the
  application-scoped `PreferencesManager` and persists through `Dispatchers.IO`.
- **D5 — HTTPS outside debug.** Bare hosts default to `https://`; an explicit `http://` URL is
  rejected in release builds (client defence-in-depth + settings message). Debug/local HTTP for
  Docker Nextcloud remains possible.

## Phases

### Phase 1 — Upload metadata isolation (B1)
- `ShoppingListRepository.duplicateShoppingList` and `checkAndForwardRecurringLists` reset
  `uploadId` and `lastUploadedAt` on the new row.
- Test `ShoppingListUploadMetadataTest` (JVM): duplicate and forwarded lists have null metadata.

### Phase 2 — Cancellation (I2)
- `PurchaseUploadRepository.uploadList`: `catch (CancellationException) { throw e }` first.
- `PurchaseUploadApiClient.testConnection`/`upload`: replace `runCatching` with `try/catch`,
  rethrowing cancellation and wrapping other failures in `Result.failure`.
- Covered by client + repository tests.

### Phase 3 — Tests (I3)
- `PurchaseUploadApiClientTest` (fake `OkHttpClient` interceptor): OCS/direct parsing, 409→already
  imported, 401/403→auth, 404→app-not-found, 422→rejected, IO→network, cancellation propagation,
  HTTPS defaulting, request path/headers/body.
- `PurchaseUploadRepositoryTest` (in-memory fake HTTP + mocked Room/prefs): eligibility matrix,
  not-configured, uploadId reuse/generation, `lastUploadedAt` only on success, error mapping,
  cancellation propagation.

### Phase 4 — Configured flag in UI state (I4)
- `ShoppingListUiState.nextcloudConfigured`, set in the `flowOn(ioDispatcher)` transform.
- `ShoppingListViewModel.isEligibleForUpload(list)` is pure; the card uses
  `uiState.nextcloudConfigured && isEligibleForUpload(list)`.

### Phase 5 — Settings ViewModel (I5)
- New `ui/viewmodel/NextcloudUploadSettingsViewModel` with `StateFlow`, one-shot test events,
  manual-DI `Factory` reading `APPLICATION_KEY`, and the application `PreferencesManager`.
- Screen collects state with `collectAsStateWithLifecycle()`; no local `PreferencesManager`.

### Phase 6 — HTTPS enforcement (I6)
- `sanitizeUrl` no longer auto-selects `http://` for local hosts; defaults to `https://`.
- `PurchaseUploadApiClient.execute` rejects non-HTTPS when `!BuildConfig.DEBUG`.
- Settings emits `InsecureUrl`; new `nextcloud_insecure_url` string (en/de/uk).

## Verification

- `./gradlew :app:testDebugUnitTest` — green (new client, repository and metadata tests).
- `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` — green.
- `./gradlew :app:assembleDebug` — green.
- Manual: duplicate an uploaded list → send creates a new import; recurring purchase → each
  occurrence new import; release build rejects `http://`.

## Deferred

- `cachedPath` single-candidate simplification, OCS `meta.statuscode` handling, distinct DB-error
  result, `nextcloud_not_configured` snackbar copy, `SpendingCalculator.toDomain` upload fields,
  EOF newline.
