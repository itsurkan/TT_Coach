# Tasks: KMP Shared Module Refactoring

**Input**: Design documents from `/specs/001-kmp-shared-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/shared-module-api.md, quickstart.md

**Tests**: Included in Phase 7 (User Story 4 explicitly requests unit tests).

**Organization**: Tasks grouped by user story. US2 (Data Models) precedes US1 (Analysis Logic) because analysis functions depend on shared model types.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the KMP shared module skeleton and configure Gradle for multiplatform builds.

- [X] T001 Create shared module directory structure: `shared/src/commonMain/kotlin/com/ttcoachai/shared/{models,analysis,detection}`, `shared/src/commonTest/kotlin/com/ttcoachai/shared/{models,analysis,detection}`, `shared/src/commonTest/resources/fixtures/`, `shared/src/androidMain/`
- [X] T002 Create `shared/build.gradle.kts` with `kotlin("multiplatform")` and `com.android.library` plugins, configure `androidTarget()` + `jvm()` targets, set `compileSdk=36`, `minSdk=24`, namespace `com.ttcoachai.shared`, add `kotlin("test")` to commonTest dependencies (see quickstart.md step 2)
- [X] T003 Update root `settings.gradle` to add `include ':shared'` after `include ':app'`
- [X] T004 Update root `build.gradle` to add `id 'org.jetbrains.kotlin.multiplatform' version '2.1.0' apply false` to the plugins block
- [X] T005 Update `app/build.gradle` to add `implementation project(':shared')` to the dependencies block
- [ ] T006 Verify project syncs and builds: run `./gradlew :shared:assemble` and `./gradlew :app:assembleDebug`

**Checkpoint**: Shared module exists, compiles (empty), and app module depends on it successfully.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: No foundational tasks needed beyond setup. The shared module skeleton from Phase 1 is sufficient. User story phases can begin immediately.

**⚠️ CRITICAL**: Phase 1 must be complete before proceeding.

---

## Phase 3: User Story 2 — Shared Data Models (Priority: P1) 🎯 MVP

**Goal**: Define all core data models in the shared module so both analysis logic (US1) and mappers (US3) can reference them.

**Independent Test**: Instantiate each model, verify default values, confirm exercise parameter presets produce expected threshold values. Run `./gradlew :shared:jvmTest` (once tests exist in US4).

### Implementation for User Story 2

- [X] T007 [P] [US2] Create `StrokePhase` enum (READY, BACKSWING, FORWARD_SWING, CONTACT, FOLLOW_THROUGH, RECOVERY) in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/StrokePhase.kt` — extract verbatim from `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:104-111`
- [X] T008 [P] [US2] Create `CorrectionType` enum (WRIST, BODY_ROTATION, FOLLOW_THROUGH, CONTACT_HEIGHT, ELBOW_POSITION, STROKE_SPEED, GENERAL) in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/CorrectionType.kt` — extract verbatim from `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:13-21`
- [X] T009 [P] [US2] Create `Landmark3D` data class (x, y, z, visibility, presence) in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/Landmark3D.kt` — new type replacing MediaPipe NormalizedLandmark usage
- [X] T010 [P] [US2] Create `PoseFrame` data class (frameIndex, timestampMs, landmarks) in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/PoseFrame.kt` — replaces JsonPoseFrame
- [X] T011 [P] [US2] Create `FeedbackItem` data class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/FeedbackItem.kt` — change `strokeLandmarks` type from `List<List<NormalizedLandmark>>` to `List<List<Landmark3D>>` per research R4
- [X] T012 [P] [US2] Create `TechniqueErrors` object with string constants in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TechniqueErrors.kt` — extract verbatim
- [X] T013 [P] [US2] Create `TechniqueRecommendations` object with string constants in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TechniqueRecommendations.kt` — extract verbatim
- [X] T014 [P] [US2] Create `AnalysisResult` data class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/AnalysisResult.kt` — extract from `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:36-65`, change `timestamp` default to `0L` (per migration note 3), preserve `isSuccessful()`, `getSummary()`, `getPrimaryError()`, `getPrimaryRecommendation()` methods
- [X] T015 [P] [US2] Create `ExerciseParameters` data class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/ExerciseParameters.kt` — extract from `app/src/main/java/com/ttcoachai/models/ExerciseParameters.kt`, remove `fromSharedPreferences()` factory, keep `forehandDrive()`, `backhandDrive()`, `forehandDriveBeginner()` presets and all validation methods
- [X] T016 [P] [US2] Create `TrackingAxis` enum (X, Y, Z) in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TrackingAxis.kt`
- [X] T017 [P] [US2] Create `StrokeDetectorConfig` data class with FOREHAND and BACKHAND companion presets in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/StrokeDetectorConfig.kt` — extract from `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:24-51`
- [X] T018 [P] [US2] Create `DetectedStroke` data class with `containsFrame()` and `getPhaseForFrame()` methods in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/DetectedStroke.kt` — extract from `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:77-117`
- [X] T019 [P] [US2] Create `FramePhaseInfo` data class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/FramePhaseInfo.kt`
- [X] T020 [P] [US2] Create `StrokeDetectionResult` data class with `getStrokeForFrame()`, `getPhaseForFrame()`, `getFrameInfo()` methods in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/StrokeDetectionResult.kt` — extract from `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:132-151`
- [ ] T021 [US2] Verify shared models compile: run `./gradlew :shared:compileKotlinJvm`

**Checkpoint**: All shared data models compile in commonMain with zero platform-specific imports. US1 can now begin.

---

## Phase 4: User Story 1 — Platform-Independent Stroke Analysis Logic (Priority: P1)

**Goal**: Extract all angle calculations, metric computations, stroke phase detection, and stroke segmentation into the shared module using shared data models from US2.

**Independent Test**: Feed an array of Landmark3D points into shared analysis functions, verify angle calculations, speed measurements, and stroke phase detection return correct results without an Android device.

### Implementation for User Story 1

- [ ] T022 [P] [US1] Create `AngleCalculations` object in `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/AngleCalculations.kt` — extract `calculate3DAngle()`, `calculateWristAngle()`, `calculateBodyRotation()`, `calculateFollowThroughAngle()` from `app/src/main/java/com/ttcoachai/services/MotionAnalyzer.kt`, replace NormalizedLandmark params with Landmark3D, return null when required landmark indices are out of bounds (per contract)
- [ ] T023 [P] [US1] Create `MetricCalculations` object in `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/MetricCalculations.kt` — extract `calculateContactHeight()`, `calculateElbowBodyDistance()`, `calculateStrokeSpeed()` from `app/src/main/java/com/ttcoachai/services/MotionAnalyzer.kt`, replace NormalizedLandmark params with Landmark3D
- [ ] T024 [US1] Create `StrokeAnalyzer` object in `shared/src/commonMain/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzer.kt` — orchestrator that calls AngleCalculations + MetricCalculations + ExerciseParameters validation to produce AnalysisResult; implement `analyzeStroke(landmarks, parameters, phase)` per contract (handle <33 landmarks gracefully, NaN/Infinite safety)
- [ ] T025 [P] [US1] Create `StrokePhaseDetector` class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetector.kt` — extract real-time phase detection from `app/src/main/java/com/ttcoachai/services/StrokePhaseDetector.kt`, implement `detect(landmarks, timestampMs)` and `reset()` per contract, replace `android.util.Log` with `println()` (per research R3)
- [ ] T026 [US1] Create `JsonStrokeDetector` class in `shared/src/commonMain/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetector.kt` — extract batch stroke detection from `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt`, replace `JsonLandmark`→`Landmark3D` and `JsonPoseFrame`→`PoseFrame` (per migration notes 1-2), replace `android.util.Log` with `println()`, implement `detect(frames)` per contract
- [ ] T027 [US1] Verify shared analysis and detection compile with zero platform imports: run `./gradlew :shared:compileKotlinJvm` and grep for `android.*`, `com.google.mediapipe.*`, `androidx.*` imports in `shared/src/commonMain/`

**Checkpoint**: All analysis and detection logic compiles in commonMain. Shared module has zero platform-specific imports.

---

## Phase 5: User Story 3 — MediaPipe-to-Shared-Model Mappers (Priority: P2)

**Goal**: Create mapper functions in the Android module that bridge MediaPipe types to shared Landmark3D/PoseFrame, then rewire Android callers to use the shared module.

**Independent Test**: Create mock MediaPipe result objects, verify the mapper produces correctly populated shared model instances.

### Implementation for User Story 3

- [ ] T028 [US3] Create `MediaPipeMapper` object in `app/src/main/java/com/ttcoachai/mappers/MediaPipeMapper.kt` — implement `toLandmark3D(NormalizedLandmark)`, `toLandmarkList(PoseLandmarkerResult)`, `toWorldLandmarkList(PoseLandmarkerResult)` per contract; map x→x, y→y, z→z, visibility→visibility, presence→presence; empty input → empty list
- [ ] T029 [US3] Update `MotionAnalyzer` in `app/src/main/java/com/ttcoachai/services/MotionAnalyzer.kt` — simplify to delegate analysis to shared `StrokeAnalyzer`, use `MediaPipeMapper` to convert inputs, remove extracted math functions (now in shared module)
- [ ] T030 [US3] Update all Android callers that use `StrokePhaseDetector` — convert MediaPipe landmarks via `MediaPipeMapper.toWorldLandmarkList()` before calling the shared `StrokePhaseDetector.detect()`
- [ ] T031 [US3] Update all Android callers that use `JsonStrokeDetector` — ensure PoseFrame is constructed using shared types via mapper
- [ ] T032 [US3] Update Android imports across affected files — replace `com.ttcoachai.models.AnalysisResult` with `com.ttcoachai.shared.models.AnalysisResult`, same for ExerciseParameters, StrokePhase, FeedbackItem, CorrectionType, and all other moved types
- [ ] T033 [US3] Remove or mark deprecated the original Android-only model and service files that have been fully extracted to shared (keep `fromSharedPreferences()` as extension function if used)
- [ ] T034 [US3] Verify app builds and runs: `./gradlew :app:assembleDebug`

**Checkpoint**: Android app compiles, uses shared module for all analysis, MediaPipe conversion happens at the mapper boundary.

---

## Phase 6: User Story 4 — Unit Tests for Stroke Analysis (Priority: P2)

**Goal**: Comprehensive unit test suite for the shared analysis logic, executable on desktop JVM without Android SDK.

**Independent Test**: Run `./gradlew :shared:jvmTest` on any machine with a Kotlin compiler — no Android SDK required.

### Implementation for User Story 4

- [ ] T035 [US4] Copy test fixture files to shared module: `app/src/main/assets/Videos/forehand_drive_poses.json` → `shared/src/commonTest/resources/fixtures/forehand_drive.json`, `forehand_drive_wrong_poses.json` → `forehand_drive_wrong.json`, `forehand_drive2_poses.json` → `forehand_drive2.json`
- [ ] T036 [US4] Create test utility to load and parse JSON fixture files into `List<PoseFrame>` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/TestFixtures.kt`
- [ ] T037 [P] [US4] Create `AngleCalculationsTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/AngleCalculationsTest.kt` — test `calculate3DAngle` with known geometry, test `calculateWristAngle` / `calculateBodyRotation` / `calculateFollowThroughAngle` with valid landmarks, test null return for insufficient landmarks, test edge cases (NaN, collinear points)
- [ ] T038 [P] [US4] Create `MetricCalculationsTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/MetricCalculationsTest.kt` — test `calculateContactHeight`, `calculateElbowBodyDistance`, `calculateStrokeSpeed` with known inputs, test null return for missing landmarks, test zero time delta edge case
- [ ] T039 [P] [US4] Create `ExerciseParametersTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/models/ExerciseParametersTest.kt` — test `forehandDrive()`, `backhandDrive()`, `forehandDriveBeginner()` preset values, test all validation methods (`isWristAngleValid`, `isBodyRotationValid`, etc.) with in-range and out-of-range values
- [ ] T040 [US4] Create `StrokeAnalyzerTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/analysis/StrokeAnalyzerTest.kt` — test full pipeline with fixture data: correct forehand drive produces expected score range and no errors, wrong forehand drive identifies WRIST correction type, test with <33 landmarks returns partial result
- [ ] T041 [P] [US4] Create `StrokePhaseDetectorTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/detection/StrokePhaseDetectorTest.kt` — test phase transitions (READY→BACKSWING→FORWARD_SWING→CONTACT→FOLLOW_THROUGH→RECOVERY) using fixture data, test `reset()` clears state
- [ ] T042 [P] [US4] Create `JsonStrokeDetectorTest` in `shared/src/commonTest/kotlin/com/ttcoachai/shared/detection/JsonStrokeDetectorTest.kt` — test stroke detection with fixture data matches expected stroke count and phase boundaries, test empty input returns empty result, test FOREHAND and BACKHAND configs
- [ ] T043 [US4] Run full shared test suite and verify all pass: `./gradlew :shared:jvmTest`

**Checkpoint**: All shared module tests pass on desktop JVM without Android SDK (SC-005).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final validation that the refactoring preserved behavioral parity and meets all success criteria.

- [ ] T044 Verify zero platform-specific imports in shared module: search `shared/src/commonMain/` for `android.*`, `com.google.mediapipe.*`, `androidx.*` imports — must find zero (SC-001)
- [ ] T045 Run Android unit tests to verify no regressions: `./gradlew :app:testDebugUnitTest` (SC-003)
- [ ] T046 Build full app and verify it produces identical analysis output: `./gradlew :app:assembleDebug` (FR-010)
- [ ] T047 Verify shared test suite runs under 30 seconds: time `./gradlew :shared:jvmTest` (SC-005)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — no additional blocking tasks
- **US2 Data Models (Phase 3)**: Depends on Phase 1 completion
- **US1 Analysis Logic (Phase 4)**: Depends on Phase 3 (US2 models must exist)
- **US3 Mappers (Phase 5)**: Depends on Phase 3 + Phase 4 (shared models + analysis logic)
- **US4 Unit Tests (Phase 6)**: Depends on Phase 3 + Phase 4 (shared module must be implemented)
- **Polish (Phase 7)**: Depends on all previous phases

### User Story Dependencies

```
US2 (Data Models) ──► US1 (Analysis Logic) ──► US3 (Mappers)
                                             ──► US4 (Unit Tests)
```

- **US2** (P1): Start first — no other story dependencies, all models needed by US1
- **US1** (P1): Start after US2 — analysis functions reference shared models
- **US3** (P2): Start after US1 + US2 — mappers bridge MediaPipe to shared types
- **US4** (P2): Start after US1 + US2 — tests validate shared analysis logic
- **US3 and US4 can run in parallel** — US3 modifies Android code, US4 adds test code in shared module

### Within Each User Story

- Models before services/analysis
- Analysis before orchestrator (StrokeAnalyzer)
- Core implementation before integration
- Compile verification at end of each phase

### Parallel Opportunities

- **Phase 3 (US2)**: T007–T020 are ALL parallelizable (each creates a separate file)
- **Phase 4 (US1)**: T022 + T023 + T025 are parallelizable (separate files), T024 depends on T022+T023, T026 depends on models
- **Phase 5 (US3)**: T028 first, then T029–T032 are partially parallelizable (different files but may share import updates)
- **Phase 6 (US4)**: T035–T036 first (fixtures), then T037+T038+T039+T041+T042 are parallelizable (separate test files), T040 depends on fixtures, T043 is final
- **Phase 5 + Phase 6 can run in full parallel** (different modules)

---

## Parallel Example: User Story 2 (Data Models)

```bash
# Launch ALL model files in parallel (14 independent files):
Task: T007 "Create StrokePhase enum in shared/.../models/StrokePhase.kt"
Task: T008 "Create CorrectionType enum in shared/.../models/CorrectionType.kt"
Task: T009 "Create Landmark3D data class in shared/.../models/Landmark3D.kt"
Task: T010 "Create PoseFrame data class in shared/.../models/PoseFrame.kt"
Task: T011 "Create FeedbackItem data class in shared/.../models/FeedbackItem.kt"
Task: T012 "Create TechniqueErrors object in shared/.../models/TechniqueErrors.kt"
Task: T013 "Create TechniqueRecommendations object in shared/.../models/TechniqueRecommendations.kt"
Task: T014 "Create AnalysisResult data class in shared/.../models/AnalysisResult.kt"
Task: T015 "Create ExerciseParameters data class in shared/.../models/ExerciseParameters.kt"
Task: T016 "Create TrackingAxis enum in shared/.../models/TrackingAxis.kt"
Task: T017 "Create StrokeDetectorConfig data class in shared/.../models/StrokeDetectorConfig.kt"
Task: T018 "Create DetectedStroke data class in shared/.../models/DetectedStroke.kt"
Task: T019 "Create FramePhaseInfo data class in shared/.../models/FramePhaseInfo.kt"
Task: T020 "Create StrokeDetectionResult data class in shared/.../models/StrokeDetectionResult.kt"
```

## Parallel Example: User Story 4 (Tests)

```bash
# After fixtures loaded (T035-T036), launch test files in parallel:
Task: T037 "AngleCalculationsTest in shared/.../analysis/AngleCalculationsTest.kt"
Task: T038 "MetricCalculationsTest in shared/.../analysis/MetricCalculationsTest.kt"
Task: T039 "ExerciseParametersTest in shared/.../models/ExerciseParametersTest.kt"
Task: T041 "StrokePhaseDetectorTest in shared/.../detection/StrokePhaseDetectorTest.kt"
Task: T042 "JsonStrokeDetectorTest in shared/.../detection/JsonStrokeDetectorTest.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup (T001–T006)
2. Complete Phase 3: US2 Data Models (T007–T021) — all models in shared module
3. Complete Phase 4: US1 Analysis Logic (T022–T027) — all analysis in shared module
4. **STOP and VALIDATE**: Shared module compiles, zero platform imports
5. This is a valid MVP: shared module exists with all logic, even before Android is rewired

### Incremental Delivery

1. Setup → Shared module skeleton ready
2. US2 (Models) → All data types in shared module → Compile check
3. US1 (Analysis) → All logic in shared module → Compile check (MVP!)
4. US3 (Mappers) → Android uses shared module → Full app builds
5. US4 (Tests) → Confidence in correctness → `jvmTest` passes
6. Polish → All success criteria verified

### Parallel Team Strategy

With two developers after Phases 1–4:
- Developer A: US3 (Mappers — Android module changes)
- Developer B: US4 (Tests — shared module test code)
- Both complete independently, then Polish phase together

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US2 before US1 because models are prerequisites for analysis functions
- `android.util.Log` → `println()` per research decision R3
- `AnalysisResult.timestamp` default → `0L` per migration note 3
- `fromSharedPreferences()` stays in Android as extension function
- `JsonLandmark` → `Landmark3D`, `JsonPoseFrame` → `PoseFrame` per migration notes 1-2
