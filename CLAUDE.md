# CLAUDE.md

This file provides guidance to AI agents (Claude Code, Codex, Gemini, Cursor, etc.) when working with code in this repository.

## Project Overview

This is **axon-kotlin**, a Maven multi-module Kotlin library that extends [AxonFramework](https://github.com/AxonFramework/AxonFramework) with idiomatic Kotlin APIs. It targets **Axon Framework 5** and **JVM 21**.

### Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.

### Workflow

1. **Spec-first**: Enter plan mode for non-trivial tasks (3+ steps or architectural decisions).
2. **Verify before fixing**: Confirm a finding against the current code before correcting it.
3. **Self-improvement**: After corrections, update this file to prevent the same mistake.
4. **Verification**: Run tests and the full build after every change. Ask: "Would a staff engineer approve this?"

## Modules

| Module | Artifact | Package |
|---|---|---|
| `kotlin/` | `axon-kotlin` | `org.axonframework.extensions.kotlin` |
| `kotlin-test/` | `axon-kotlin-test` | `org.axonframework.extension.kotlin.test` |
| `coverage-report/` | aggregated JaCoCo report | activated via `-Pcoverage` |

> Note: `extensions` (with "s") in the main module vs `extension` (no "s") in the test module — this is intentional and matches the upstream package layout.

## Build Commands

Maven wrapper (`./mvnw`) is used throughout. No Makefile.

```bash
# Full build + tests
./mvnw clean install

# Full verify (CI standard)
./mvnw -B -U -Dstyle.color=always clean verify

# Build with code coverage
./mvnw -B -U -Dstyle.color=always -Dcoverage clean verify

# Skip tests
./mvnw clean install -DskipTests=true

# Build a single module only
./mvnw clean install -pl kotlin
./mvnw clean install -pl kotlin-test

# Generate KDoc + source jars
./mvnw clean install -Pdocs-and-sources
```

## Test Commands

```bash
# Run all tests in a module
./mvnw test -pl kotlin
./mvnw test -pl kotlin-test

# Run a single test class
./mvnw test -pl kotlin -Dtest="CommandGatewayExtensionsTest"

# Run a single test method (backtick names must be quoted carefully)
./mvnw test -pl kotlin -Dtest="CommandGatewayExtensionsTest#testMethodName"

# Run tests matching a pattern
./mvnw test -pl kotlin -Dtest="*Gateway*"
```

Test runner is Maven Surefire with JUnit 5 Platform.

## Code Style

### Formatting

- **Indent:** 4 spaces (no tabs)
- **Line endings:** LF
- **Max line length:** 180 characters
- **Continuation indent:** 8 spaces
- **Final newline:** none (`insert_final_newline = false`)
- XML/POM files: 2-space indent
- Opening braces on the same line as the declaration

### Naming Conventions

- Classes/objects/interfaces: `PascalCase`
- Functions/properties/local vars: `camelCase`
- Top-level constants: `camelCase` (val) or `PascalCase` if they represent a named object
- **Test methods:** backtick-enclosed natural language sentences:
  ```kotlin
  @Test
  fun `Send extension should invoke correct method on the gateway`() { ... }
  ```
- Test utility files: lowercase with descriptive names (`testObjects.kt`, `mockkExtensions.kt`, `testTypes.kt`)

### Imports

- Wildcard imports are acceptable in tests (e.g., `import io.mockk.*`)
- Explicit single imports for production code
- No strict enforced ordering beyond IntelliJ defaults

### Type Annotations

- Omit return types for expression-body functions when inferable from context
- Explicit return types required on all public API
- Use `?` nullable types purposefully; rely on `-Xjsr305=strict` for Java interop null-safety

### Generics / Inline Functions

- Use `inline fun` with `reified` type parameters to eliminate `::class.java` boilerplate at call sites
- Use `noinline` on lambda params that must be stored or passed to non-inline functions
- Cast to `Any` when calling a Java overload to prevent infinite recursion on inline extensions:
  ```kotlin
  inline fun <reified C : Any> CommandGateway.send(command: C): CommandResult =
      // Cast to Any to route to CommandGateway.send(Object) and avoid infinite recursion.
      this.send(command as Any)
  ```

### Error Handling

- Standard exceptions — no `Result` type or `Either`
- At serialization boundaries, catch library exceptions and rethrow as domain-specific ones

### KDoc

- All public API must have KDoc with `@param`, `@return`, `@see`, `@since`
- `@since` values use version numbers matching the extension release (e.g., `@since 0.5.0`)
- Apache License 2.0 header on every source file
- Do not add spurious `@param` tags for type parameters that are not described in the body

## Testing Patterns

**Frameworks:** JUnit 5 (Jupiter) + `kotlin-test-junit5` + MockK

```kotlin
internal class SomeExtensionsTest {
    private val mockGateway: CommandGateway = mockk()

    @AfterTest
    fun tearDown() {
        clearMocks(mockGateway)
    }

    @Test
    fun `description of behaviour under test`() {
        every { mockGateway.someMethod(any()) } returns someValue

        val result = mockGateway.someExtension()

        verify(exactly = 1) { mockGateway.someMethod(any()) }
        assertEquals(expected, result)
    }
}
```

- All test classes are `internal class`
- Fields are `private val`
- Use `@BeforeTest` / `@AfterTest` (from `kotlin.test`) for lifecycle, **not** JUnit 5's `@BeforeEach`
- Use `@Nested` (JUnit 5) for grouping related cases within a class
- Shared test data in separate `testObjects.kt` / `testTypes.kt` files (not inner classes)
- MockK preferred over Mockito; custom matchers go in `mockkExtensions.kt`
- Assertions: prefer `kotlin.test.assertEquals` / `assertSame`

## Compiler Configuration

```
-Xjsr305=strict           # strict null-safety for Java annotations
JVM target: 21
Kotlin plugins: no-arg, all-open, kotlinx-serialization
```

> `all-open` is kept for potential future use but has no annotation targets configured since `@AggregateRoot` was removed in Axon Framework 5.

## Axon Framework 5 — Key API Changes

This extension targets Axon Framework **5.0.x**. Major removals from AF4:

| AF4 concept | AF5 replacement |
|---|---|
| `CommandCallback` / `CommandResultMessage` | `CommandResult` (returned directly by `CommandGateway.send`) |
| `ResponseTypes` / `AbstractResponseType` | Removed — pass `Class<R>` directly |
| `SubscriptionQueryResult` (Reactor) | `Publisher<R>` from `reactive-streams` |
| `AggregateTestFixture` / `SagaTestFixture` | `AxonTestFixture` with phase-based API |
| `AggregateLifecycle` / `@AggregateRoot` | Removed entirely |
| `scatterGather` on `QueryGateway` | Removed — use `streamingQuery` |
| `axon-configuration` artifact | Renamed to `axon-messaging` |
| `EventUpcaster` Kotlin DSL | Not available in 5.0 — upcasting returns in 5.2.0; use payload conversion at handling time in the meantime |

## Linting / Quality

- No ktlint or Detekt — style enforced via IntelliJ's `axon_code_style.xml` (from the main AxonFramework repo) and `.editorconfig`
- SonarCloud analysis runs in CI (`./mvnw sonar:sonar`)
- CLA required for contributions (cla-assistant.io)

## CI Workflows

- `main.yml` — push to main: verify + deploy to Sonatype
- `pullrequest.yml` — PRs: verify only
- `docs.yml` — documentation generation
- Coverage aggregated by `coverage-report/` module when `-Pcoverage` is active
