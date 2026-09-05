---
created: 2026-08-08
type: build-deploy
tags: [build, gradle, test, deploy, release]
related:
  - "./project-overview.md"
---

# Build & Deploy

## Prerequisites

- **JDK 17+**
- **Android SDK** (compileSdk 36, build-tools)
- **Android Studio** (recommended) or command-line tools
- `local.properties` with `sdk.dir` and optionally `SILICON_FLOW_KEY`

## Build Commands

### Assemble Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Assemble Release AAB (for Play Store)
```bash
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

### Clean Build
```bash
./gradlew clean
```

### Build with Gradle Parallel (faster)
```bash
./gradlew assembleDebug --parallel
```

## Test Commands

### Unit Tests (JVM)
```bash
./gradlew test
```
Tests live in `app/src/test/`. Uses JUnit 4.13.2, Mockito Kotlin 5.4.0, coroutines-test 1.8.0.

### Instrumentation Tests (Android)
```bash
./gradlew connectedAndroidTest
```
Tests live in `app/src/androidTest/`. Uses AndroidX Test, Espresso 3.6.1, Room Testing, Compose UI Testing. Requires a connected device or emulator.

### Specific Test Class
```bash
./gradlew test --tests "com.otakeeesen.byebyemoneylist.ExampleTest"
```

## Lint

```bash
./gradlew lint
```
Runs Android Lint static analysis. Results at `app/build/reports/lint-results*.html`.

## Gradle Configuration

- **Wrapper**: `gradlew` (Gradle 8.13). Regenerate with `gradle wrapper --gradle-version 8.13`.
- **JVM Args**: `-Xmx2048m` (in `gradle.properties`).
- **Version Catalog**: `gradle/libs.versions.toml` manages all dependency versions.
- **Room Schema**: Exported to `app/schemas/` (configured via KSP arg).

## Deployment

### Google Play Store
1. Increment `versionCode` and `versionName` in `app/build.gradle.kts` (`defaultConfig` block).
2. Build the release AAB: `./gradlew bundleRelease`.
3. Upload `app/build/outputs/bundle/release/app-release.aab` to Google Play Console.
4. ProGuard is currently disabled (`isMinifyEnabled = false`).

### Distribution
- Target: Android 10+ (API 29), compiled against Android 15 (API 36).
- `SILICON_FLOW_KEY` is injected from `local.properties` into `BuildConfig`.

## Key Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | Top-level plugins: AGP, Kotlin Compose, KSP |
| `app/build.gradle.kts` | Dependencies, SDK versions, build features |
| `settings.gradle.kts` | Repositories (Google, Maven Central, JitPack), module includes |
| `gradle/libs.versions.toml` | Centralized version catalog |
| `gradle.properties` | JVM args, Kotlin code style |
| `local.properties` | Local SDK path, API keys (gitignored) |

## Updates
- [2026-08-08]: Initial version. Auto-scanned from project build files.
