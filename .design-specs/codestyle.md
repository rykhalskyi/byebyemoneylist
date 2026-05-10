# Code Style and Best Practices

This project is an Android application developed with **Kotlin** and **Jetpack Compose**.

---

# Foundational Principles

*   **SOLID**: Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion.
*   **KISS (Keep It Simple, Stupid)**: Prefer simple, straightforward solutions.
*   **YAGNI (You Ain't Gonna Need It)**: Do not add functionality until necessary.
*   **DRY (Don't Repeat Yourself)**: Avoid duplicating code.
*   **Clean Code & Architecture**: Structure the application to separate concerns (UI -> Data).

---

# Specific Guidelines

## Jetpack Compose: Modifier Parameter Placement
Every Composable that emits UI should accept a `Modifier` as its first optional parameter and pass it to its root UI element.

```kotlin
@Composable
fun MyComponent(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) { ... }
}
```

## State Management: ViewModel
Use `ViewModel` to hold and manage UI state. Expose state using `StateFlow` and collect it in Composables using `collectAsStateWithLifecycle`.

## Resource Management: String Hardcoding
Always define user-facing strings in `strings.xml` and access them via `stringResource`.

## Kotlin Coding Style: Trailing Commas
Use trailing commas for multi-line parameter lists, collections, and arguments to minimize diff noise.

```kotlin
val items = listOf(
    "Apple",
    "Orange",
)
```

## State Management: ViewModel State Update
Always use the `.update { ... }` extension function for `MutableStateFlow` to ensure atomic updates.

## Architecture: Dependency Injection (Constructor Injection)
Use constructor injection for all dependencies to facilitate testing and decoupling.
