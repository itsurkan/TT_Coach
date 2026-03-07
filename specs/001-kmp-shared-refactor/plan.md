# Implementation Plan: KMP Shared Module Refactoring

**Branch**: `001-kmp-shared-refactor` | **Date**: 2026-02-23 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-kmp-shared-refactor/spec.md`

## Summary

Extract all platform-independent stroke analysis logic, data models, and detection algorithms from the single Android `:app` module into a new Kotlin Multiplatform `:shared` module with `commonMain` and `androidMain` source sets. The Android app continues to work identically, calling the same functions through a thin mapper layer that converts MediaPipe types to shared `Landmark3D`/`PoseFrame` types.

## Technical Context

**Language/Version**: Kotlin 2.1.0 (upgrade to KMP plugin from `org.jetbrains.kotlin.android`)
**Primary Dependencies**: MediaPipe tasks-vision 0.10.14 (Android-only), kotlinx-coroutines 1.10.2, Firebase BOM 34.8.0
**Storage**: Room 2.6.1 + Firebase Firestore (stays in Android module, not relevant to shared)
**Testing**: JUnit 4.13.2 + kotlin.test for commonTest; Robolectric 4.16.1 stays in Android tests only
**Target Platform**: Android (minSdk 24, compileSdk 36); shared module targets JVM + Android (iOS added later)
**Project Type**: Mobile app with extracted KMP library module
**Performance Goals**: Analysis functions must maintain current real-time performance (~30 FPS pose processing)
**Constraints**: Zero behavioral changes to existing Android app; shared module must have zero platform-specific imports
**Scale/Scope**: ~8 source files to extract/refactor, ~6 new shared data model classes, 1 new Gradle module

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution is not yet configured for this project (template placeholders only). No gates to enforce. Proceeding with industry-standard KMP best practices.

**Post-Phase 1 re-check**: N/A — no constitution defined.

## Project Structure

### Documentation (this feature)

```text
specs/001-kmp-shared-refactor/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (shared module public API)
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
shared/
├── build.gradle.kts                     # KMP module: commonMain + androidMain targets
└── src/
    ├── commonMain/kotlin/com/ttcoachai/shared/
    │   ├── models/
    │   │   ├── Landmark3D.kt            # Shared landmark point (x, y, z, visibility, presence)
    │   │   ├── PoseFrame.kt             # Timestamped collection of Landmark3D
    │   │   ├── AnalysisResult.kt        # Stroke analysis output
    │   │   ├── FeedbackItem.kt          # Correction recommendation
    │   │   ├── CorrectionType.kt        # Error type enum
    │   │   ├── StrokePhase.kt           # Stroke cycle phase enum
    │   │   ├── ExerciseParameters.kt    # Metric thresholds with presets
    │   │   ├── DetectedStroke.kt        # Stroke segment with phase boundaries
    │   │   └── StrokeDetectorConfig.kt  # Detection configuration
    │   ├── analysis/
    │   │   ├── AngleCalculations.kt     # 3D/2D angle math (extracted from MotionAnalyzer)
    │   │   ├── MetricCalculations.kt    # Contact height, elbow distance, speed
    │   │   └── StrokeAnalyzer.kt        # Orchestrator: landmarks → AnalysisResult
    │   └── detection/
    │       ├── StrokePhaseDetector.kt   # Velocity-based phase detection (extracted)
    │       └── JsonStrokeDetector.kt    # State-machine stroke segmentation (extracted)
    ├── commonTest/kotlin/com/ttcoachai/shared/
    │   ├── analysis/
    │   │   ├── AngleCalculationsTest.kt
    │   │   ├── MetricCalculationsTest.kt
    │   │   └── StrokeAnalyzerTest.kt
    │   ├── detection/
    │   │   ├── StrokePhaseDetectorTest.kt
    │   │   └── JsonStrokeDetectorTest.kt
    │   └── models/
    │       └── ExerciseParametersTest.kt
    └── commonTest/resources/
        └── fixtures/                    # JSON pose data for tests
            ├── forehand_drive.json
            ├── forehand_drive_wrong.json
            └── forehand_drive2.json

app/
├── build.gradle                         # Updated: depends on :shared, keeps MediaPipe
└── src/main/java/com/ttcoachai/
    ├── mappers/
    │   └── MediaPipeMapper.kt           # NormalizedLandmark/PoseLandmarkerResult → Landmark3D/PoseFrame
    ├── services/
    │   ├── MotionAnalyzer.kt            # Simplified: delegates to shared StrokeAnalyzer
    │   └── ...                          # Other services unchanged
    └── ...                              # All other Android code unchanged
```

**Structure Decision**: Two-module setup (`:shared` + `:app`). The shared module uses Kotlin DSL (`.gradle.kts`) as required by KMP plugin. The app module keeps its existing Groovy DSL. The root `settings.gradle` adds `include ':shared'`.

## Complexity Tracking

No constitution violations to justify.
