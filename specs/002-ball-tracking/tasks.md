# Tasks: Ball Tracking and Trajectory Prediction

**Input**: Design documents from `/specs/002-ball-tracking/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ball-tracking-api.md

**Tests**: Included per plan.md (JUnit 4.13.2 + kotlin.test for commonTest; fixture-based testing with synthetic ball trajectory data).

**Organization**: Tasks grouped by user story. US4 and US1 are both P1 and can be developed in parallel. US2 depends on US1 models. US3 depends on US1 and US2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Exact file paths included in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add OpenCV dependency and create package directories for ball tracking code

- [X] T001 Add OpenCV 4.9.0 dependency and arm64-v8a ABI filter in app/build.gradle
- [X] T002 [P] Create tracking package directory at shared/src/commonMain/kotlin/com/ttcoachai/shared/tracking/
- [X] T003 [P] Create tracking package directory at app/src/main/java/com/ttcoachai/tracking/
- [X] T004 [P] Create test package directory at shared/src/commonTest/kotlin/com/ttcoachai/shared/tracking/
- [X] T005 [P] Create test package directory at app/src/test/java/com/ttcoachai/tracking/

---

## Phase 2: Foundational (Shared Models Used Across Stories)

**Purpose**: Create core data classes and enums referenced by multiple user stories. MUST complete before any story implementation.

**CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Create BallDetection data class and BallDetectionStatus enum in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/BallDetection.kt
- [X] T007 [P] Create DataSource enum (DETECTED, INTERPOLATED, ABSENT) in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/DataSource.kt
- [X] T008 [P] Create BallPosition2D data class in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/BallPosition2D.kt
- [X] T009 [P] Create RegionOfInterest data class with createDefault(frameWidth, frameHeight) factory method in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/RegionOfInterest.kt

**Checkpoint**: Core shared models ready — user story implementation can now begin

---

## Phase 3: User Story 4 — Optimized Camera Capture (Priority: P1)

**Goal**: Camera automatically configures low exposure time and adapts ISO/exposure to lighting conditions, producing sharp ball frames with minimal motion blur.

**Independent Test**: Compare recording with default camera settings vs. optimized settings — optimized recording shows noticeably less motion blur on the ball.

### Implementation for User Story 4

- [X] T010 [US4] Create CameraConfiguration data class in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/CameraConfiguration.kt
- [X] T011 [US4] Implement CameraOptimizer class with applyBallTrackingMode(), restoreDefaultMode(), and onBrightnessUpdate() in app/src/main/java/com/ttcoachai/tracking/CameraOptimizer.kt
- [X] T012 [US4] Integrate CameraOptimizer into CameraManager — add Camera2Interop exposure settings before bindToLifecycle and periodic brightness adaptation in app/src/main/java/com/ttcoachai/managers/CameraManager.kt
- [X] T013 [US4] Create CameraOptimizerTest with tests for initial config, brightness adaptation, rate limiting, and fallback behavior in app/src/test/java/com/ttcoachai/tracking/CameraOptimizerTest.kt

**Checkpoint**: Camera optimization active during ball-tracking sessions. Frames show reduced motion blur.

---

## Phase 4: User Story 1 — Detect Ball During Rally (Priority: P1)

**Goal**: Detect and track the table tennis ball in real-time using OpenCV color/shape analysis within an ROI, marking frames where the ball is not found as "not detected" rather than producing false positives.

**Independent Test**: Record a rally and verify ball is highlighted/marked in playback. Ball detected in at least 70% of visible frames, false positives below 5%.

### Implementation for User Story 1

- [X] T014 [P] [US1] Implement BallDetector class with detect() using HSV color thresholding, morphology, contour filtering by area/circularity, and release() for Mat cleanup in app/src/main/java/com/ttcoachai/tracking/BallDetector.kt
- [X] T015 [P] [US1] Implement ROIManager class with createDefault() (lower 75% height, central 80% width) and adapt() in app/src/main/java/com/ttcoachai/tracking/ROIManager.kt
- [X] T016 [US1] Integrate BallDetector into PoseLandmarkerProcessor.detectLiveStream() — call detect() on rotated bitmap after existing pose detection, emit ball detection via callback in app/src/main/java/com/ttcoachai/helpers/PoseLandmarkerProcessor.kt
- [X] T017 [US1] Update OverlayView to draw detected ball position as a circle marker on the canvas in app/src/main/java/com/ttcoachai/OverlayView.kt
- [X] T018 [US1] Create BallDetectorTest with tests for orange ball detection, white ball detection, NOT_DETECTED status when no ball in ROI, and ROI coordinate normalization in app/src/test/java/com/ttcoachai/tracking/BallDetectorTest.kt

**Checkpoint**: Ball detected and highlighted during live recording. ROI restricts detection to table area. Missing detections marked as NOT_DETECTED.

---

## Phase 5: User Story 2 — View Reconstructed Ball Trajectory (Priority: P2)

**Goal**: Reconstruct the full ball flight path including gap frames where detection was lost, using a parabolic trajectory model. Detect bounces, paddle contacts, and net clips to split into trajectory segments.

**Independent Test**: Review rally playback where ball path is drawn as a smooth curve matching actual ball flight even through detection gaps. Trajectory deviates by no more than 3 cm on average.

### Models for User Story 2

- [X] T019 [P] [US2] Create ParabolicFit data class (ax, bx, ay, by, cy coefficients) in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TrajectorySegment.kt
- [X] T020 [P] [US2] Create ContactEvent data class and ContactType enum (BOUNCE, PADDLE_CONTACT, NET_CLIP, UNKNOWN_CONTACT) in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TrajectorySegment.kt
- [X] T021 [P] [US2] Create TrajectorySegment data class with segment state fields (detections, fittedPositions, contactBefore/After, fitCoefficients, fitRmsError) in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/TrajectorySegment.kt

### Tests for User Story 2

- [X] T022 [P] [US2] Create synthetic ball trajectory test fixtures (straight line, parabolic arc, bounce sequence, gap scenario) as JSON in shared/src/commonTest/resources/fixtures/ball_trajectory_straight.json and ball_trajectory_parabolic.json
- [X] T023 [P] [US2] Create TrajectoryFilterTest with tests for linear fit (2 points), parabolic fit (3+ points), gap filling, RMS error calculation, and null return for <2 detections in shared/src/commonTest/kotlin/com/ttcoachai/shared/tracking/TrajectoryFilterTest.kt
- [X] T024 [P] [US2] Create TrajectorySegmenterTest with tests for bounce detection (vertical velocity reversal), paddle contact detection (speed ratio >1.8x), segment splitting at contacts, and recursive sub-splitting on high RMS in shared/src/commonTest/kotlin/com/ttcoachai/shared/tracking/TrajectorySegmenterTest.kt

### Implementation for User Story 2

- [X] T025 [US2] Implement TrajectoryFilter object with fit() (linear regression 2x2, quadratic regression 3x3 via Cramer's rule), evaluate(), rmsError(), and fillGaps() using pure kotlin.math in shared/src/commonMain/kotlin/com/ttcoachai/shared/tracking/TrajectoryFilter.kt
- [X] T026 [US2] Implement TrajectorySegmenter class with detectContacts() (three-signal detector: velocity reversal, speed ratio, direction angle) and segment() (split-then-fit pipeline with recursive refinement) in shared/src/commonMain/kotlin/com/ttcoachai/shared/tracking/TrajectorySegmenter.kt
- [X] T027 [US2] Update OverlayView to draw trajectory curves (smooth parabolic arcs between segments, color-coded by segment) in app/src/main/java/com/ttcoachai/OverlayView.kt

**Checkpoint**: Trajectory reconstruction fills detection gaps with physically plausible arcs. Bounces and contacts detected and split into segments. Trajectory curves drawn on playback overlay.

---

## Phase 6: User Story 3 — Synchronized Skeleton-Ball Timeline (Priority: P3)

**Goal**: Merge ball position data and skeleton pose data into a single synchronized timeline so the user sees body pose and ball position together, aligned to within 1 frame.

**Independent Test**: Play back a session and confirm ball overlay and skeleton overlay are aligned in time — ball contact visually coincides with paddle-hand reaching the ball.

### Models for User Story 3

- [ ] T028 [US3] Create SynchronizedFrame data class (frameIndex, timestampMs, pose: PoseFrame?, ball: BallDetection?, poseSource: DataSource, ballSource: DataSource) in shared/src/commonMain/kotlin/com/ttcoachai/shared/models/SynchronizedFrame.kt

### Tests for User Story 3

- [ ] T029 [US3] Create TimelineSynchronizerTest with tests for exact timestamp match, 1-frame interpolation, missing data marked ABSENT, and output ordering matching allTimestampsMs in shared/src/commonTest/kotlin/com/ttcoachai/shared/tracking/TimelineSynchronizerTest.kt

### Implementation for User Story 3

- [ ] T030 [US3] Implement TimelineSynchronizer class with merge() (map-join on timestamps, linear interpolation for 1-frame gaps) and interpolateBall() in shared/src/commonMain/kotlin/com/ttcoachai/shared/tracking/TimelineSynchronizer.kt
- [ ] T031 [US3] Update OverlayView to render synchronized ball + skeleton data from SynchronizedFrame, showing both overlays aligned on the same canvas in app/src/main/java/com/ttcoachai/OverlayView.kt

**Checkpoint**: Skeleton and ball data synchronized to within 1 frame. Combined overlay shows body pose and ball position together during playback.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validation, build verification, and integration quality

- [ ] T032 Run all shared module tests via ./gradlew :shared:jvmTest and verify pass
- [ ] T033 Run all app unit tests via ./gradlew :app:testDebugUnitTest and verify pass
- [ ] T034 Run full build via ./gradlew assembleDebug and verify no compilation errors
- [ ] T035 Run quickstart.md validation — verify integration steps from specs/002-ball-tracking/quickstart.md work end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **US4 (Phase 3)**: Depends on Phase 2 (uses CameraConfiguration model)
- **US1 (Phase 4)**: Depends on Phase 2 (uses BallDetection, RegionOfInterest models)
- **US2 (Phase 5)**: Depends on Phase 2 + Phase 4 (uses BallDetection from detection pipeline)
- **US3 (Phase 6)**: Depends on Phase 2 + Phase 4 (uses BallDetection; integrates with PoseFrame)
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US4 (P1 — Camera)**: Can start after Phase 2 — No dependencies on other stories
- **US1 (P1 — Detection)**: Can start after Phase 2 — No dependencies on other stories. US4 improves quality but is not a hard blocker
- **US2 (P2 — Trajectory)**: Can start after Phase 2 — Depends on BallDetection model from Phase 2. Does NOT depend on US1 runtime (uses same model types). Models T019-T021 and tests T022-T024 can start immediately after Phase 2
- **US3 (P3 — Sync)**: Can start after Phase 2 — Depends on BallDetection model. Does NOT depend on US1/US2 runtime for implementation

### Parallel Opportunities

**US4 and US1 are fully parallel** (different files, no shared dependencies beyond Phase 2 models):
- US4: CameraConfiguration.kt, CameraOptimizer.kt, CameraManager.kt
- US1: BallDetector.kt, ROIManager.kt, PoseLandmarkerProcessor.kt

**Within US2, models and tests are parallel** (T019-T024 all touch different files)

**Within Phase 2, all model tasks are parallel** (T006-T009 each create a separate file)

---

## Parallel Example: Phase 2 (Foundational Models)

```
# All foundational models can be created simultaneously:
Task T006: "Create BallDetection + BallDetectionStatus in shared/.../models/BallDetection.kt"
Task T007: "Create DataSource enum in shared/.../models/DataSource.kt"
Task T008: "Create BallPosition2D in shared/.../models/BallPosition2D.kt"
Task T009: "Create RegionOfInterest in shared/.../models/RegionOfInterest.kt"
```

## Parallel Example: US4 + US1 (Both P1)

```
# After Phase 2, launch both P1 stories in parallel:
Stream A (US4): T010 → T011 → T012 → T013
Stream B (US1): T014 + T015 (parallel) → T016 → T017 → T018
```

## Parallel Example: US2 (Models + Tests before Implementation)

```
# Models (all different files, parallel):
Task T019: "Create ParabolicFit in shared/.../models/TrajectorySegment.kt"
Task T020: "Create ContactEvent + ContactType in shared/.../models/TrajectorySegment.kt"
Task T021: "Create TrajectorySegment in shared/.../models/TrajectorySegment.kt"

# Note: T019-T021 are in the same file — execute sequentially

# Tests (parallel, different files):
Task T022: "Create test fixtures in shared/.../fixtures/"
Task T023: "Create TrajectoryFilterTest in shared/.../tracking/TrajectoryFilterTest.kt"
Task T024: "Create TrajectorySegmenterTest in shared/.../tracking/TrajectorySegmenterTest.kt"

# Implementation (sequential):
Task T025: "Implement TrajectoryFilter" (depends on T019-T021 models)
Task T026: "Implement TrajectorySegmenter" (depends on T025)
Task T027: "Update OverlayView for trajectory curves" (depends on T025-T026)
```

---

## Implementation Strategy

### MVP First (US4 + US1 — Both P1)

1. Complete Phase 1: Setup (add OpenCV dependency)
2. Complete Phase 2: Foundational models (BallDetection, DataSource, BallPosition2D, RegionOfInterest)
3. Complete Phase 3: US4 — Camera optimization
4. Complete Phase 4: US1 — Ball detection
5. **STOP and VALIDATE**: Ball is detected and highlighted during recording with optimized camera
6. Deploy/demo if ready — this is the MVP

### Incremental Delivery

1. Setup + Foundational → Core models ready
2. US4 + US1 → Ball detected in frames with sharp camera → **MVP!**
3. US2 → Trajectory reconstructed, gaps filled, segments split at bounces → Enhanced analysis
4. US3 → Skeleton + ball synchronized on timeline → Full coaching view
5. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- All shared module code uses `kotlin.math` only (no `java.lang.Math`) for KMP compatibility
- OpenCV Mats must be pre-allocated and reused to avoid GC pressure (per research R5)
- Trajectory math uses Cramer's rule for 2x2 and 3x3 systems — no external math libraries (per research R16)
- Camera optimization uses Camera2 interop APIs — CameraX remains primary camera API (per research R12)
- Both detectors process the same ImageProxy frame — timestamps identical by definition (per research R15)
