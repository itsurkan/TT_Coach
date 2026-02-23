# Feature Specification: KMP Shared Module Refactoring

**Feature Branch**: `001-kmp-shared-refactor`
**Created**: 2026-02-23
**Status**: Draft
**Input**: User description: "Фаза 1: Чисті Мізки (Рефакторинг та KMP) — Відокремити логіку від Android, щоб бути готовим до iOS."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Platform-Independent Stroke Analysis Logic (Priority: P1)

As a developer, I need all stroke analysis math (angle calculations, speed measurements, stroke phase detection) to live in a platform-independent shared module so that the same analysis algorithms produce identical results on both Android and iOS without any platform-specific dependencies.

**Why this priority**: This is the core value of the refactoring. Without platform-independent math, there is no foundation for cross-platform reuse. Every other story depends on the analysis logic being cleanly extracted first.

**Independent Test**: Can be fully tested by feeding an array of 3D landmark points into the shared analysis functions and verifying that angle calculations, speed measurements, and stroke phase detection return correct results — all without running an Android device or emulator.

**Acceptance Scenarios**:

1. **Given** a set of 3D landmark coordinates representing a correct forehand drive, **When** the shared analysis module processes these landmarks, **Then** it produces the same angle values, speed measurements, and stroke phase classifications as the current Android implementation.
2. **Given** the shared analysis module, **When** a developer inspects its imports, **Then** there are zero references to any platform-specific packages (no `android.*`, no `com.google.mediapipe.*`, no `androidx.*`).
3. **Given** a known set of landmark data with a deliberate wrist angle error, **When** the shared analysis module evaluates the stroke, **Then** it correctly identifies the wrist angle as out of the acceptable range defined by exercise parameters.

---

### User Story 2 - Shared Data Models (Priority: P1)

As a developer, I need all core data models (analysis results, exercise parameters, stroke phases, feedback items) to be defined in a shared module so that both Android and future iOS code reference the same data structures with consistent field names, types, and validation rules.

**Why this priority**: Data models are the contract between the analysis engine and the platform UI layers. They must be extracted alongside the math logic to form a coherent shared API. Without shared models, the math module cannot define its inputs and outputs.

**Independent Test**: Can be fully tested by instantiating each shared model, verifying default values, validating field constraints, and confirming that exercise parameter presets (forehandDrive, backhandDrive, forehandDriveBeginner) produce the expected threshold values.

**Acceptance Scenarios**:

1. **Given** the shared module, **When** a developer creates an AnalysisResult instance, **Then** all fields (wristAngle, bodyRotation, followThroughAngle, contactHeight, strokeSpeed, elbowBodyDistance, overallScore, phase) are accessible with the same types and semantics as the current Android models.
2. **Given** the ExerciseParameters model in the shared module, **When** calling the forehandDrive preset, **Then** it returns the same threshold values as the current Android implementation.
3. **Given** the StrokePhase enumeration in the shared module, **When** enumerating all values, **Then** it contains READY, BACKSWING, FORWARD_SWING, CONTACT, FOLLOW_THROUGH, and RECOVERY.

---

### User Story 3 - MediaPipe-to-Shared-Model Mappers (Priority: P2)

As a developer, I need mapper/converter functions that transform raw MediaPipe pose detection output into the shared module's data models so that the platform-specific detection layer and the platform-independent analysis layer have a clean boundary.

**Why this priority**: Mappers are the bridge between the platform-specific MediaPipe SDK and the shared analysis engine. They are essential for the separation but depend on Story 1 and Story 2 being complete first. Mappers remain in the Android module since they reference MediaPipe types.

**Independent Test**: Can be tested by creating mock MediaPipe result objects (NormalizedLandmark, PoseLandmarkerResult) and verifying the mapper produces correctly populated shared model instances with expected coordinate values.

**Acceptance Scenarios**:

1. **Given** a MediaPipe PoseLandmarkerResult with known landmark coordinates, **When** the mapper converts it, **Then** the resulting shared landmark list contains the same x, y, z, visibility, and presence values.
2. **Given** a MediaPipe NormalizedLandmark with coordinates (0.5, 0.3, 0.1), **When** the mapper converts it to a shared Landmark3D, **Then** the shared model contains x=0.5, y=0.3, z=0.1 with appropriate visibility and presence values.
3. **Given** a MediaPipe result with no detected landmarks (empty list), **When** the mapper processes it, **Then** it returns an empty shared landmark collection without crashing.

---

### User Story 4 - Unit Tests for Stroke Analysis (Priority: P2)

As a developer, I need a comprehensive unit test suite for the shared analysis logic so that I can verify stroke detection correctness, catch regressions during refactoring, and validate the analysis pipeline without launching an Android device.

**Why this priority**: Unit tests are the safety net that proves the refactoring preserved correctness. They are critical for confidence but can only be written after the shared module (Stories 1-2) exists.

**Independent Test**: Can be fully tested by running the shared module's test suite on any machine with a Kotlin compiler — no Android SDK, emulator, or device required.

**Acceptance Scenarios**:

1. **Given** a pre-recorded array of landmark points representing a correct forehand drive, **When** the unit test feeds this data through the analysis pipeline, **Then** the test passes with the overall score within the expected range and no technique errors flagged.
2. **Given** a pre-recorded array of landmark points with a known wrist angle error, **When** the unit test feeds this data through the analysis pipeline, **Then** the test correctly identifies the WRIST correction type in the feedback.
3. **Given** a sequence of frames representing a full stroke cycle, **When** the unit test processes the sequence through stroke phase detection, **Then** it correctly identifies each phase transition (READY → BACKSWING → FORWARD_SWING → CONTACT → FOLLOW_THROUGH → RECOVERY).
4. **Given** the shared module test suite, **When** executed via a standard test runner on a desktop machine, **Then** all tests pass without requiring any Android dependencies.

---

### Edge Cases

- What happens when landmark data contains NaN or infinite coordinate values? The shared module must handle these gracefully without crashing.
- What happens when fewer landmarks than expected are present in a frame (e.g., partially occluded body)? The analysis must return a partial result or a clear indication of insufficient data.
- What happens when the frame rate is very low (e.g., 5 FPS instead of expected 15-30 FPS)? Velocity calculations must still produce reasonable values or indicate low confidence.
- What happens when the stroke is performed by a left-handed player? The landmark indices for analysis (currently hardcoded to right side: elbow=14, wrist=16, shoulder=12) must be configurable or documented as a known limitation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a shared module containing all core data models (AnalysisResult, ExerciseParameters, StrokePhase, FeedbackItem, CorrectionType, Landmark3D, PoseFrame) that are completely free of platform-specific dependencies.
- **FR-002**: The system MUST provide platform-independent angle calculation functions (3D angle via dot product, wrist angle, body rotation, follow-through angle) that accept shared Landmark3D inputs and return numeric results.
- **FR-003**: The system MUST provide platform-independent metric calculation functions (contact height, elbow-body distance, stroke speed) that accept shared model inputs.
- **FR-004**: The system MUST provide platform-independent stroke phase detection that processes a sequence of landmark frames and identifies phase transitions using velocity-based thresholds.
- **FR-005**: The system MUST provide platform-independent stroke detection that processes a sequence of landmark frames and segments individual strokes with phase boundaries.
- **FR-006**: The system MUST provide mapper functions (in the Android module) that convert MediaPipe PoseLandmarkerResult and NormalizedLandmark objects into the shared module's Landmark3D and PoseFrame models.
- **FR-007**: The system MUST provide exercise parameter presets (forehandDrive, backhandDrive, forehandDriveBeginner) in the shared module with the same threshold values as the current implementation.
- **FR-008**: The system MUST provide a validation function in ExerciseParameters that checks whether analysis results fall within acceptable ranges for each metric (wrist angle, body rotation, follow-through, contact height, elbow distance, stroke speed).
- **FR-009**: The system MUST provide a unit test suite that validates analysis logic by feeding pre-defined landmark arrays and asserting correct outputs — executable without any Android runtime.
- **FR-010**: The existing Android application MUST continue to function identically after refactoring, with no changes to user-visible behavior, analysis accuracy, or performance.

### Key Entities

- **Landmark3D**: A single body landmark point with x, y, z coordinates plus visibility and presence confidence values. Replaces direct usage of MediaPipe's NormalizedLandmark in analysis code.
- **PoseFrame**: A timestamped collection of Landmark3D points representing a full body pose at a single moment. Used as the input unit for analysis functions.
- **AnalysisResult**: The output of stroke analysis containing all computed metrics (angles, speed, height, distance), the detected stroke phase, an overall technique score, and a list of feedback items.
- **ExerciseParameters**: A configuration object defining acceptable ranges and tolerances for each metric, with named presets for different stroke types and skill levels.
- **StrokePhase**: An enumeration of the phases in a table tennis stroke cycle (READY, BACKSWING, FORWARD_SWING, CONTACT, FOLLOW_THROUGH, RECOVERY).
- **DetectedStroke**: A segment of frames representing a single stroke, with indices marking phase boundaries and computed aggregate metrics.
- **FeedbackItem**: A correction recommendation linked to a specific CorrectionType and optionally to specific landmark indices for visualization.

## Assumptions

- The refactoring targets Kotlin Multiplatform (KMP) with `commonMain` source set for shared code and `androidMain` for MediaPipe mappers.
- The existing analysis algorithms are correct and should be preserved as-is during extraction — this is a structural refactoring, not a behavioral change.
- Left-handed player support is out of scope for this phase; current right-side landmark indices (elbow=14, wrist=16, shoulder=12, index=20) are preserved.
- The shared module will initially target JVM and Android; iOS target will be added in a future phase.
- Pre-recorded test data (landmark arrays) will be created from actual MediaPipe capture sessions or manually crafted to represent known stroke patterns.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of analysis math functions (angle calculations, speed measurements, metric computations, stroke phase detection, stroke segmentation) reside in the shared module with zero platform-specific imports.
- **SC-002**: The shared module's unit test suite contains at least one test per analysis function covering correct input, incorrect input (technique error detection), and edge case input — all passing on a desktop JVM without Android runtime.
- **SC-003**: The existing Android application produces identical analysis results (same scores, same feedback, same phase detection) before and after the refactoring, verified by running the same input data through both old and new code paths.
- **SC-004**: Mapper functions correctly convert 100% of MediaPipe landmark fields (x, y, z, visibility, presence) to shared models without data loss or precision degradation.
- **SC-005**: A developer can run the full shared module test suite in under 30 seconds on a standard development machine without installing the Android SDK.
