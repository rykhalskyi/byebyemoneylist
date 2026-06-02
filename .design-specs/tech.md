# Technology Stack

## Project Type
This is a modern **Android mobile application** built for the Android platform.

## Core Technologies

### Primary Language(s)
- **Language**: Kotlin 2.0.21
- **Runtime/Compiler**: JVM 17
- **Language-specific tools**: Kotlin Symbol Processing (KSP), Kotlin Coroutines for asynchronous programming.

### Key Dependencies/Libraries
- **Jetpack Compose (BOM)**: Modern toolkit for building native UI.
- **AndroidX Core KTX**: Kotlin extensions for Android framework APIs.
- **AndroidX Lifecycle**: Lifecycle-aware components including ViewModel and Flow support.
- **AndroidX Activity Compose**: Integration of Compose with Android Activities.
- **Material 3**: Google's latest design system for UI components.

### Application Architecture
- **Pattern**: MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **Structure**:
    - **UI Layer**: Jetpack Compose functions for rendering UI and ViewModels for state management.
    - **Domain Layer**: Pure Kotlin logic containing models.
    - **Data Layer**: Repositories for data orchestration.

### Data Storage
- **Primary storage**: Room Database (SQLite).
- **Caching**: In-memory caching for active shopping lists.
- **Data formats**: Kotlin objects persisted to Room entities.

## Development Environment

### Build & Development Tools
- **Build System**: Gradle 8.13 (Kotlin DSL)
- **Package Management**: Gradle Version Catalog (`libs.versions.toml`)
- **Development workflow**: Android Studio with Hot Reload (Live Edit/Apply Changes).

### Code Quality Tools
- **Static Analysis**: Android Lint.
- **Formatting**: ktlint (via Gradle plugin).
- **Testing Frameworks**: 
    - **Unit Tests**: JUnit 4.13.2.
    - **Instrumentation Tests**: AndroidX Test Runner, Espresso.

### Version Control & Collaboration
- **VCS**: Git
- **Branching Strategy**: GitHub Flow (Feature branches -> Main).
- **Code Review Process**: Pull Requests on GitHub.

## Deployment & Distribution
- **Target Platform(s)**: Android devices.
- **Distribution Method**: Google Play Store (AAB).
- **Installation Requirements**: Android 8.0 (API 26) minimum.
- **Update Mechanism**: Google Play Store updates.

## Technical Requirements & Constraints

### Performance Requirements
- **UI Performance**: Consistent 60/120 FPS for smooth animations using Compose.

### Compatibility Requirements  
- **Platform Support**: Android 8.0 (API 26) +.

### Security & Compliance
- **Security Requirements**: Secure storage of sensitive user data using encrypted SharedPreferences if necessary; adherence to Android security best practices.
- **Data Protection**: Local-first architecture minimizes data transmission; data remains on the device.

## Technical Decisions & Rationale

### Decision Log
1. **Jetpack Compose**: Chosen for faster development, declarative UI, and better state management integration.
2. **Kotlin DSL (Gradle)**: Used for build scripts to provide better type safety and IDE support.
3. **Version Catalog**: Centralized dependency management.

## Known Limitations
- **Build Times**: Gradle builds can be slow; requires incremental build optimization.
