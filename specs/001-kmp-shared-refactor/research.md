# Research: KMP Shared Module Refactoring

**Feature**: 001-kmp-shared-refactor | **Date**: 2026-02-23

## R1: KMP Plugin Setup with Existing Groovy Android Project

**Decision**: Add a new `:shared` module using Kotlin DSL (`build.gradle.kts`) with the `kotlin("multiplatform")` plugin. Keep the existing `:app` module in Groovy DSL unchanged.

**Rationale**: The KMP plugin requires Kotlin DSL for its `kotlin {}` multiplatform block. Mixing DSLs across modules is fully supported by Gradle — each module's build file is independent. This avoids the risk of converting the entire existing Android build to Kotlin DSL.

**Alternatives considered**:
- Convert entire project to Kotlin DSL first: Higher risk, larger diff, no functional benefit for this phase.
- Use Groovy DSL for the shared module: Not supported by the KMP plugin's `kotlin("multiplatform")` syntax.

## R2: KMP Target Configuration (JVM + Android)

**Decision**: Configure the shared module with `androidTarget()` and `jvm()` targets. The `jvm()` target enables running `commonTest` on desktop without Android SDK. The `androidTarget()` allows the `:app` module to depend on `:shared` as an Android library.

**Rationale**: The spec requires tests that run "without any Android runtime" (SC-005). Having a `jvm()` target ensures `commonTest` runs on desktop JVM. The `androidTarget()` is needed because `:app` is an Android application module and needs Android-compatible bytecode from `:shared`.

**Alternatives considered**:
- `androidTarget()` only: Would require Android test runner for shared module tests, violating SC-005.
- `jvm()` only: The `:app` module could not depend on it as an Android library, causing compile errors with Android-specific Gradle resolution.

## R3: Replacing `android.util.Log` in Shared Code

**Decision**: Use `println()` as a simple drop-in replacement in shared code. Introduce no logging framework dependency.

**Rationale**: The only platform dependency in the extractable analysis code (besides MediaPipe types) is `android.util.Log`. The logging calls are debug-level diagnostic output used during development. For a first extraction, `println()` is sufficient. A proper KMP logging library (e.g., Kermit, Napier) can be added later when iOS support is added and log routing matters.

**Alternatives considered**:
- Kermit (`co.touchlab:kermit`): Production-quality KMP logging, but adds an external dependency for code that mostly just needs debug prints during development.
- `expect/actual` Logger interface: Over-engineering for the current scope. Only valuable when there are distinct platform logging requirements.
- Remove all logging: Too aggressive — the debug logging in `JsonStrokeDetector` is useful for diagnosing stroke detection issues.

## R4: Handling `FeedbackItem.strokeLandmarks` MediaPipe Dependency

**Decision**: Split `FeedbackItem` into a shared version (in `:shared`) that uses `List<List<Landmark3D>>` for `strokeLandmarks` and keep the conversion at the mapper boundary. The shared `FeedbackItem` holds platform-independent data.

**Rationale**: `FeedbackItem` is part of the analysis output (`AnalysisResult` contains a list of them). To make `AnalysisResult` fully shared, `FeedbackItem` must also be shared. The `strokeLandmarks` field currently holds `NormalizedLandmark` lists, but the shared equivalent using `Landmark3D` carries the same information.

**Alternatives considered**:
- Keep `FeedbackItem` in Android module: Would prevent `AnalysisResult` from being fully shared, breaking the core requirement.
- Make `strokeLandmarks` nullable/optional in shared: Loses data — the landmarks are used for overlay visualization.

## R5: Build Configuration — Kotlin and AGP Version Compatibility

**Decision**: Keep Kotlin 2.1.0. The KMP plugin version matches the Kotlin version (both are `2.1.0`). AGP 8.13.2 (classpath) is compatible with the KMP Android target. No version changes needed.

**Rationale**: Kotlin 2.1.0 ships with full KMP support. The `kotlin("multiplatform")` plugin version is always the same as the Kotlin version. AGP 8.x supports being consumed as a library dependency, and the `:shared` module uses `androidTarget()` which produces an AAR compatible with the app module.

**Alternatives considered**:
- Upgrade to Kotlin 2.1.10+: No benefit for this phase; risk of breaking existing code.

## R6: Test Data Fixtures for commonTest

**Decision**: Copy the existing JSON pose fixture files from `app/src/main/assets/Videos/` to `shared/src/commonTest/resources/fixtures/`. Use `kotlin.test` with JUnit runner for `commonTest`.

**Rationale**: The existing test files (`forehand_drive_poses.json`, etc.) contain real captured pose data. They are currently read using Android asset APIs or direct file I/O in tests. In `commonTest`, they can be loaded via `ClassLoader.getResource()` on JVM. The `kotlin.test` library provides `@Test`, `assertEquals`, etc. that work across all KMP targets.

**Alternatives considered**:
- Keep fixtures in `:app` and test only from Android tests: Violates SC-005 (desktop test requirement).
- Generate synthetic test data instead of copying real captures: Loses the validation that the shared code produces identical results to the current Android implementation (SC-003).

## R7: Shared Module Package Structure

**Decision**: Use `com.ttcoachai.shared` as the base package, with sub-packages `models`, `analysis`, and `detection`. This creates a clear boundary between shared and platform code.

**Rationale**: Using a distinct `.shared` package prevents import conflicts when the Android module still has its own copies during migration. It also makes it immediately visible in any import statement whether a type comes from the shared module or the platform layer.

**Alternatives considered**:
- Same package as Android (`com.ttcoachai.models`, etc.): Would cause duplicate class errors during migration; harder to track what's been extracted.
- Flat package (`com.ttcoachai.shared` with no sub-packages): Would put ~20 classes in one package, reducing navigability.

## R8: Gradle Module Dependency Wiring

**Decision**: The `:app` module declares `implementation(project(":shared"))` in its dependencies. The root `settings.gradle` adds `include ':shared'`. No other wiring needed.

**Rationale**: Standard Gradle multi-module setup. The KMP plugin's `androidTarget()` produces an AAR that the Android app module can consume directly. The `implementation` scope is sufficient since the shared types are used internally by the app.

**Alternatives considered**:
- `api` scope: Would transitively expose shared types to any module depending on `:app`. Unnecessary since there's only one consuming module.
