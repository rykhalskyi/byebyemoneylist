# Project Structure

## Directory Organization

```
byebyemoney/
├── app/                        # Main Android application module
│   ├── src/
│   │   ├── main/               # Application source code
│   │   │   ├── java/           # Kotlin source files
│   │   │   │   └── com/otakeeesen/byebyemoneylist/
│   │   │   │       ├── data/      # Data layer (Models, Repositories)
│   │   │   │       ├── ui/        # UI layer (Screens, ViewModels, Theme)
│   │   │   │       │   ├── components/  # Reusable UI components
│   │   │   │       │   ├── theme/       # Material 3 theme definitions
│   │   │   │       │   └── viewmodel/   # ViewModels
│   │   │   │       └── MainActivity.kt # Entry point activity
│   │   │   ├── res/            # Android resources (strings, drawables, etc.)
│   │   │   └── AndroidManifest.xml # App manifest
│   │   ├── test/               # Local unit tests
│   │   └── androidTest/        # Instrumented tests
│   └── build.gradle.kts        # Module-level build configuration
├── gradle/                     # Gradle wrapper and version catalog
│   └── libs.versions.toml      # Centralized dependency management
├── .design-specs/              # Project design and technical documentation
├── build.gradle.kts            # Project-level build configuration
├── settings.gradle.kts         # Project settings
└── README.md                   # Project overview
```

## Naming Conventions

### Files
- **Kotlin Files**: `PascalCase.kt` (e.g., `MainActivity.kt`, `ShoppingListViewModel.kt`)
- **Resource Files**: `snake_case.xml` (e.g., `strings.xml`, `ic_launcher_background.xml`)

### Code
- **Classes/Interfaces**: `PascalCase`
- **Compose Functions**: `PascalCase` (e.g., `@Composable fun ShoppingListCard(...)`)
- **Functions/Methods**: `camelCase`
- **Variables/Parameters**: `camelCase`
- **Packages**: `lowercase` (e.g., `com.otakeeesen.byebyemoneylist`)

## Import Patterns

### Import Order
1. Standard Library (`kotlin.*`, `java.*`)
2. Android/Jetpack (`android.*`, `androidx.*`)
3. Third-party Libraries
4. Local Project Imports (`com.otakeeesen.byebyemoneylist.*`)

## Code Structure Patterns

### Jetpack Compose Organization
- **State hoisting**: Pass state down and events up.
- **Parameters**: `Modifier` is always the first optional parameter.
- **Composition**: Small, reusable functions over large monolithic blocks.

## Code Organization Principles

1. **Single Responsibility**: Each file handles one specific concern.
2. **Modularity**: Logic is separated into layers (UI, Data) to ensure decoupling.
3. **Consistency**: Adhere to the Material 3 design system.

## Code Size Guidelines
- **File size**: Aim for < 300 lines.
- **Composable size**: Keep functions focused; extract sub-components if needed.

## Documentation Standards
- **KDoc**: Use for public APIs, complex logic, and custom Composables.
- **Inline Comments**: Use sparingly for "why" rather than "what".
