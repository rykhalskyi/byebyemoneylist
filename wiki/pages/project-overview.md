---
created: 2026-08-08
type: overview
tags: [tech-stack, architecture, kotlin, compose, android]
---

# Project Overview: Bye-bye Money List

AI-powered shopping list manager and expense tracker for Android.

## Tech Stack

### Language & Runtime
- **Kotlin 2.2.10** (JVM 11 bytecode target)
- **Kotlin Coroutines** for async/concurrency

### UI
- **Jetpack Compose** with **Material 3** design system
- **Compose BOM 2026.02.01** for dependency alignment
- **Coil Compose 2.7.0** for image loading
- **MPAndroidChart 3.1.0** for analytics charts
- **reorderable 3.1.0** for drag-to-reorder list items

### Architecture
- **MVVM** with Clean Architecture
- **StateFlow** for reactive state management
- **Manual constructor-based DI** via `ByeByeMoneyApplication` (no Hilt/Dagger)
- Single Activity pattern (`MainActivity.kt`)

### Data Layer
- **Room 2.6.1** (SQLite) with KSP annotation processing
- Database version: **20** (18 migration paths)
- **10 entities** with cross-reference tables
- **Encrypted SharedPreferences** via `androidx.security.crypto`
- Room schema export at `app/schemas/`

### Networking & AI
- **OkHttp 4.12.0** for HTTP
- **Google Gemini SDK 0.9.0** for LLM features (receipt scanning, AI chat)
- **SiliconFlow API** (OpenAI-compatible) via OkHttp
- **ML Kit Text Recognition 19.0.1** for on-device OCR
- **CameraX 1.4.1** for camera capture
- **Code Scanner 16.1.0** for barcode scanning

### Serialization
- **Kotlinx Serialization 1.8.0** (JSON)

### Build System
- **Gradle 8.13** with Kotlin DSL (`.gradle.kts`)
- **AGP 9.3.1**
- **Version Catalog** (`gradle/libs.versions.toml`)
- **KSP 2.3.6**

### Testing
- JUnit 4.13.2, Mockito Kotlin 5.4.0, Espresso 3.6.1, Room Testing, Compose UI Testing

### Target
- minSdk 29 (Android 10), targetSdk 36 (Android 15)
- Distribution: Google Play Store (AAB)
- Version: 1.0.7.0-alpha (code 66)

## Folder Structure

```
byebyemoneylist/
├── app/
│   ├── src/main/java/com/otakeeesen/byebyemoneylist/
│   │   ├── MainActivity.kt
│   │   ├── ByeByeMoneyApplication.kt
│   │   ├── data/
│   │   │   ├── PurchaseItem.kt, ShoppingList.kt, LlmProfile.kt, ...
│   │   │   ├── agent/          # AI Agent (AgentManager, AgentQuery, AgentQueryExecutor)
│   │   │   ├── sync/           # Cloud sync (ListSyncEngine, SyncDto, SyncFolderRepository)
│   │   │   └── local/          # Room DB, PreferencesManager, entities, DAOs, repositories
│   │   ├── ui/
│   │   │   ├── navigation/     # Screen sealed class (11 routes)
│   │   │   ├── theme/          # Color.kt, Theme.kt, Type.kt
│   │   │   ├── viewmodel/      # 12 ViewModels
│   │   │   └── components/     # Composable screens & widgets (dashboard, shoppinglist, 
│   │   │                       #   product, analytics, catalog, scanner, settings, ...)
│   │   ├── util/               # CurrencyFormatter, CsvExporter, PdfReportGenerator, ...
│   │   └── res/                # Android resources (EN, DE, UK strings)
│   ├── src/test/               # Unit tests
│   ├── src/androidTest/        # Instrumentation tests
│   └── schemas/                # Room schema exports
├── gradle/
│   └── libs.versions.toml      # Version catalog
├── .design-specs/              # Design documents (tech.md, structure.md, codestyle.md, specs/)
├── .opencode/                  # AI assistant integration (plans/)
├── wiki/                       # Project wiki (this document)
├── docs/                       # Website HTML docs (about, privacy, multilingual)
├── build.gradle.kts            # Root build
└── settings.gradle.kts         # Project settings
```

## Key Concepts

### Navigation (11 routes)
Dashboard, Shopping, Analytics, Catalog, Product Detail, Add Product, Settings, LLM Settings, Product Merge Search, Product Merge Detail, Store Merge Search/Detail.

### Database Schema (20 versions)
Core entities: Category, Store, Product, Price, ShoppingList, ShoppingListItem, ProductAlias, plus cross-ref tables (ProductAnalog, StoreCategory, ShoppingListCategory).

### AI Features
- **Receipt Scanner**: Multi-engine (Gemini, SiliconFlow, ML Kit) receipt OCR with review dialog
- **AI Chat**: Natural language spending queries via AgentManager

### Data Sync
Shared shopping lists via cloud folder sync with product matching.

### Dual Price System
Estimated vs actual price tracking per purchase item (added in Issue #11).

See also: [[build-deploy]], `.design-specs/tech.md`, `.design-specs/structure.md`
