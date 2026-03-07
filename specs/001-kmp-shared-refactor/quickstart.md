# Quickstart: KMP Shared Module Refactoring

**Feature**: 001-kmp-shared-refactor | **Date**: 2026-02-23

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later (KMP support)
- Kotlin 2.1.0 (already configured)
- Gradle 8.14.3 (already configured)
- No additional SDK or tools needed

## Step-by-Step Setup

### 1. Create the shared module directory

```bash
mkdir -p shared/src/commonMain/kotlin/com/ttcoachai/shared/{models,analysis,detection}
mkdir -p shared/src/commonTest/kotlin/com/ttcoachai/shared/{models,analysis,detection}
mkdir -p shared/src/commonTest/resources/fixtures
mkdir -p shared/src/androidMain
```

### 2. Create `shared/build.gradle.kts`

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            // No external dependencies — pure Kotlin only
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.ttcoachai.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
}
```

### 3. Update root `settings.gradle`

Add `include ':shared'` after `include ':app'`.

### 4. Update root `build.gradle`

Add KMP plugin to the plugins block:
```groovy
id 'org.jetbrains.kotlin.multiplatform' version '2.1.0' apply false
```

### 5. Update `app/build.gradle`

Add shared module dependency:
```groovy
dependencies {
    implementation project(':shared')
    // ... existing dependencies
}
```

### 6. Extract models to shared module

Move these files from `app/src/main/java/com/ttcoachai/models/` to `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/`:

| Source | Target | Changes |
|--------|--------|---------|
| `StrokePhase` enum | `StrokePhase.kt` | Package rename only |
| `CorrectionType` enum | `CorrectionType.kt` | Package rename only |
| `ExerciseParameters` data class | `ExerciseParameters.kt` | Remove `fromSharedPreferences()` |
| `TechniqueErrors` object | `TechniqueErrors.kt` | Package rename only |
| `TechniqueRecommendations` object | `TechniqueRecommendations.kt` | Package rename only |
| — (new) | `Landmark3D.kt` | New data class |
| — (new) | `PoseFrame.kt` | New data class |
| `FeedbackItem` data class | `FeedbackItem.kt` | Change `NormalizedLandmark` → `Landmark3D` |
| `AnalysisResult` data class | `AnalysisResult.kt` | Change timestamp default to `0L` |

### 7. Extract analysis logic

Move math from `MotionAnalyzer.kt` into:
- `shared/.../analysis/AngleCalculations.kt` — angle computation functions
- `shared/.../analysis/MetricCalculations.kt` — distance/speed functions
- `shared/.../analysis/StrokeAnalyzer.kt` — orchestrator

### 8. Extract detection logic

Move from `app` to `shared`:
- `JsonStrokeDetector` class → `shared/.../detection/JsonStrokeDetector.kt`
- `StrokePhaseDetector` class → `shared/.../detection/StrokePhaseDetector.kt`

Replace `android.util.Log` calls with `println()`.

### 9. Create MediaPipe mapper in app

Create `app/src/main/java/com/ttcoachai/mappers/MediaPipeMapper.kt` that converts between MediaPipe types and `Landmark3D`/`PoseFrame`.

### 10. Update Android callers

Update `MotionAnalyzer`, `PoseAnalysisProcessor`, and other Android-side code to:
1. Convert MediaPipe results via `MediaPipeMapper`
2. Call shared module functions with `Landmark3D` inputs
3. Receive `AnalysisResult` from shared module

### 11. Copy test fixtures and write tests

```bash
cp app/src/main/assets/Videos/forehand_drive_poses.json shared/src/commonTest/resources/fixtures/
cp app/src/main/assets/Videos/forehand_drive_wrong_poses.json shared/src/commonTest/resources/fixtures/
cp app/src/main/assets/Videos/forehand_drive2_poses.json shared/src/commonTest/resources/fixtures/
```

Write `commonTest` tests using `kotlin.test` that validate all analysis functions.

## Verification

```bash
# Run shared module tests (desktop JVM — no Android SDK needed)
./gradlew :shared:jvmTest

# Run Android app tests (ensure no regressions)
./gradlew :app:testDebugUnitTest

# Build the full app
./gradlew :app:assembleDebug
```

## Key Verification Points

1. `./gradlew :shared:jvmTest` passes with zero Android dependencies
2. `./gradlew :app:assembleDebug` builds successfully
3. The app produces identical analysis output before and after refactoring
