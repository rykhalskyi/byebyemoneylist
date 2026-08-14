# Wiki Log

## [2026-08-08] init | Wiki created — project-overview and build-deploy pages populated via auto-scan
## [2026-08-09] research | Dashboard UX epic — analysis of current codebase, decisions on emoji/icon approach, default product strategy, Quick Purchase flow design
## [2026-08-09] spec | Dashboard UX improvements — feature spec for category emojis, default products, streamlined Quick Purchase
## [2026-08-09] plan | Dashboard UX improvements — implementation plan across 18 files, single DB migration, 4 new files, ~8.5h estimated
## [2026-08-10] update | dashboard-ux-improvements — Task 2 (default product per category) cancelled; replaced by Task 2.a (empty list with category + virtual Quick Purchase product in analytics). Documented Id==0 phantom-item bug (ShoppingListRepository.kt:74-76) and analytics pipeline; migration reduced to emoji-only.
## [2026-08-10] plan | Category emojis (Issue #50) — focused implementation plan for the emoji feature; cross-links the Dashboard UX epic spec/plan/research.
## [2026-08-11] ticket | Issue #52 — Quick Purchase flow ticket documenting remaining Task 3 work; Tasks 1 and 2.a complete
## [2026-08-11] plan | Quick Purchase flow (Issue #52) — focused implementation plan for the two-step Quick Purchase screen; 3 new files, 6 modified, no DB changes
## [2026-08-12] plan | Widget reordering — drag-and-drop dashboard widget reordering reusing sh.calvin.reorderable grid API; 9 files, no DB changes
## [2026-08-12] update | widget-reordering — fixed AbstractMethodError crash: removed default value from @Composable interface method Card(); Kotlin default-arg bridge for @Composable interface methods doesn't generate the abstract-bridge on implementations, causing runtime crash
## [2026-08-13] plan | Scan Purchase widget (Issue #54) — LLM-gated dashboard widget that navigates to Shopping tab and auto-opens PurchaseDialog; savedStateHandle flag bridge, 7 files, no DB changes
## [2026-08-13] ticket | Issue #54 — Scan Purchase widget implemented; button enabled only when an LLM profile is active, tap opens PurchaseDialog on Shopping tab; build verified (compileDebugKotlin + assembleDebug)
## [2026-08-14] research | Month spending sum — documented canonical month-sum formula and centralized `sumExpenses` (widget 1621 / list 1708 / analytics 1698 discrepancy root cause)
