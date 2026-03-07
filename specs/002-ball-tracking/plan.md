# Implementation Plan: Ball Tracking and Trajectory Prediction

**Branch**: `002-ball-tracking` | **Date**: 2026-02-24 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/002-ball-tracking/spec.md`

## Summary

Add ball detection, trajectory reconstruction, and skeleton-ball synchronization to the TT Coach AI app. The system uses CameraX with optimized exposure settings to capture sharp frames, detects the table tennis ball via color/shape analysis within a Region of Interest, reconstructs ball flight paths using a parabolic trajectory filter to fill detection gaps, and merges ball position data with existing skeleton pose data into a unified synchronized timeline.

## Technical Context

**Language/Version**: Kotlin 2.1.0 (KMP shared module + Android app module)
**Primary Dependencies**: CameraX 1.5.3 (camera control + exposure), OpenCV Android SDK 4.9.0 (color/shape detection), MediaPipe tasks-vision 0.10.14 (existing pose detection), kotlinx-coroutines 1.10.2
**Storage**: Room 2.6.1 (ball detection data persisted alongside training sessions)
**Testing**: JUnit 4.13.2 + kotlin.test for commonTest; fixture-based testing with synthetic ball trajectory data
**Target Platform**: Android (minSdk 24, compileSdk 36); ball math in shared KMP module (commonMain)
**Project Type**: Mobile app with KMP shared library
**Performance Goals**: Ball detection must run alongside pose detection without dropping below target frame rate (~30 FPS); trajectory reconstruction runs post-frame or on background thread
**Constraints**: Must not degrade existing skeleton tracking performance; camera optimization applies during ball-tracking sessions only; offline-capable (no network required)
**Scale/Scope**: ~12 new source files (6 shared models, 3 shared algorithms, 3 Android-specific), ROI covering standard table area

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution is not yet configured for this project (template placeholders only). No gates to enforce. Proceeding with established project conventions from Phase 1.

**Post-Phase 1 re-check**: N/A — no constitution defined.

## Project Structure

### Documentation (this feature)

```text
specs/002-ball-tracking/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
shared/src/
├── commonMain/kotlin/com/ttcoachai/shared/
│   ├── models/
│   │   ├── BallDetection.kt          # Ball position, confidence, status per frame
│   │   ├── TrajectorySegment.kt       # Continuous arc between direction changes
│   │   ├── RegionOfInterest.kt        # ROI bounds for ball search area
│   │   ├── SynchronizedFrame.kt       # Merged skeleton + ball data point
│   │   └── CameraConfiguration.kt     # Camera parameter settings model
│   ├── tracking/
│   │   ├── TrajectoryFilter.kt        # Parabolic trajectory reconstruction
│   │   ├── TrajectorySegmenter.kt     # Bounce/contact detection, segment splitting
│   │   └── TimelineSynchronizer.kt    # Merge ball + skeleton streams
│   └── (existing analysis/, detection/, models/)
├── commonTest/kotlin/com/ttcoachai/shared/
│   └── tracking/
│       ├── TrajectoryFilterTest.kt    # Parabolic fit accuracy tests
│       ├── TrajectorySegmenterTest.kt # Bounce detection tests
│       └── TimelineSynchronizerTest.kt # Sync alignment tests
└── commonTest/resources/fixtures/
    └── ball_trajectory_*.json         # Synthetic ball position test data

app/src/main/java/com/ttcoachai/
├── tracking/
│   ├── BallDetector.kt               # OpenCV color/shape detection (Android-only)
│   ├── ROIManager.kt                 # ROI definition and adaptation
│   └── CameraOptimizer.kt            # CameraX exposure/frame rate control
├── managers/
│   └── CameraManager.kt              # Updated: exposure optimization integration
├── ui/
│   └── OverlayView.kt                # Updated: ball position + trajectory drawing
└── (existing code unchanged)

app/src/test/java/com/ttcoachai/
└── tracking/
    ├── BallDetectorTest.kt            # Detection unit tests
    └── CameraOptimizerTest.kt         # Camera config tests
```

**Structure Decision**: Same two-module setup as Phase 1 (`:shared` + `:app`). Platform-independent math (trajectory filter, segmenter, synchronizer) goes in shared KMP `commonMain`. Platform-specific code (OpenCV ball detection, CameraX optimization, ROI management) stays in the Android `:app` module since it depends on Android camera and vision APIs.

## Complexity Tracking

No constitution violations to justify.
