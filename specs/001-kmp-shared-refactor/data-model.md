# Data Model: KMP Shared Module Refactoring

**Feature**: 001-kmp-shared-refactor | **Date**: 2026-02-23

## Entity Diagram

```
┌─────────────────────┐       ┌───────────────────────┐
│     Landmark3D      │◄──────│      PoseFrame        │
├─────────────────────┤  1..* ├───────────────────────┤
│ x: Float            │       │ frameIndex: Int        │
│ y: Float            │       │ timestampMs: Long      │
│ z: Float            │       │ landmarks: List<L3D>   │
│ visibility: Float   │       └───────────┬───────────┘
│ presence: Float     │                   │ *
└─────────────────────┘                   ▼
                              ┌───────────────────────┐
                              │   DetectedStroke      │
                              ├───────────────────────┤
                              │ strokeIndex: Int       │
                              │ prep*Frame: Int (x4)   │
                              │ contactFrame: Int      │
                              │ forward*Frame: Int (x2)│
                              │ return*Frame: Int (x2) │
                              │ backswingMinValue: Fl   │
                              │ forwardPeakValue: Fl    │
                              │ peakVelocity: Float    │
                              │ strokeDurationMs: Long │
                              │ forwardSwingDurMs: Long│
                              │ isComplete: Boolean    │
                              └───────────────────────┘
                                        │
                                        ▼ uses
┌──────────────────┐   ┌───────────────────────────┐   ┌───────────────────┐
│   StrokePhase    │◄──│     AnalysisResult         │──►│   FeedbackItem    │
├──────────────────┤   ├───────────────────────────┤   ├───────────────────┤
│ READY            │   │ timestamp: Long            │   │ message: String   │
│ BACKSWING        │   │ wristAngle: Float?         │   │ type: CorrType    │
│ FORWARD_SWING    │   │ bodyRotation: Float?       │   │ isPositive: Bool  │
│ CONTACT          │   │ followThroughAngle: Float? │   │ strokeLandmarks:  │
│ FOLLOW_THROUGH   │   │ contactHeight: Float?      │   │  List<List<L3D>>  │
│ RECOVERY         │   │ strokeSpeed: Float?        │   └───────────────────┘
└──────────────────┘   │ elbowBodyDistance: Float?   │
                       │ isWristAngleValid: Bool     │   ┌───────────────────┐
                       │ isBodyRotationValid: Bool   │   │  CorrectionType   │
                       │ isFollowThroughValid: Bool  │   ├───────────────────┤
                       │ isContactHeightValid: Bool  │   │ WRIST             │
                       │ isStrokeSpeedValid: Bool    │   │ BODY_ROTATION     │
                       │ isElbowPositionValid: Bool  │   │ FOLLOW_THROUGH    │
                       │ overallScore: Float         │   │ CONTACT_HEIGHT    │
                       │ phase: StrokePhase          │   │ ELBOW_POSITION    │
                       │ errors: List<String>        │   │ STROKE_SPEED      │
                       │ recommendations: List<Str>  │   │ GENERAL           │
                       │ feedbackItems: List<FBI>    │   └───────────────────┘
                       └───────────────────────────┘

┌──────────────────────────────┐   ┌──────────────────────────────┐
│     ExerciseParameters       │   │    StrokeDetectorConfig      │
├──────────────────────────────┤   ├──────────────────────────────┤
│ exerciseId: String           │   │ landmarkIndex: Int           │
│ idealWristAngle: Float       │   │ trackingAxis: TrackingAxis   │
│ wristAngleTolerance: Float   │   │ backswingThreshold: Float    │
│ minBodyRotation: Float       │   │ forwardPeakThreshold: Float  │
│ bodyRotationTolerance: Float │   │ readyPositionThreshold: Float│
│ followThroughAngle: Float    │   │ forwardVelocityThresh: Float │
│ followThroughTolerance: Float│   │ returnVelocityThresh: Float  │
│ contactHeightMin: Float      │   │ minBackswingDepth: Float     │
│ contactHeightMax: Float      │   │ minForwardExtension: Float   │
│ minStrokeSpeed: Float        │   │ minStrokeFrames: Int         │
│ maxStrokeSpeed: Float        │   │ maxStrokeFrames: Int         │
│ maxElbowBodyDistance: Float   │   │ smoothingWindow: Int         │
│ minElbowBodyDistance: Float   │   │ invertDirection: Boolean     │
│ movementStartThreshold: Fl   │   ├──────────────────────────────┤
│ movementEndThreshold: Float  │   │ companion: FOREHAND, BACKHAND│
│ minStrokeDuration: Long      │   └──────────────────────────────┘
│ maxStrokeDuration: Long      │
├──────────────────────────────┤   ┌──────────────────────────────┐
│ +forehandDrive()             │   │      TrackingAxis            │
│ +backhandDrive()             │   ├──────────────────────────────┤
│ +forehandDriveBeginner()     │   │ X, Y, Z                     │
│ +isWristAngleValid(Float)    │   └──────────────────────────────┘
│ +isBodyRotationValid(Float)  │
│ +isFollowThroughValid(Float) │   ┌──────────────────────────────┐
│ +isContactHeightValid(Float) │   │     FramePhaseInfo           │
│ +isStrokeSpeedValid(Float)   │   ├──────────────────────────────┤
│ +isElbowPositionValid(Float) │   │ frameIndex: Int              │
│ +isElbowNotTooClose(Float)   │   │ phase: StrokePhase           │
└──────────────────────────────┘   │ strokeIndex: Int?            │
                                   │ phaseProgress: Float         │
┌──────────────────────────────┐   └──────────────────────────────┘
│   StrokeDetectionResult      │
├──────────────────────────────┤   ┌──────────────────────────────┐
│ strokes: List<DetectedStroke>│   │ TechniqueErrors (object)     │
│ framePhases: List<FPI>       │   ├──────────────────────────────┤
│ totalFrames: Int             │   │ WRIST_BENT, LOW_ROTATION,    │
├──────────────────────────────┤   │ HIGH_CONTACT, LOW_CONTACT,   │
│ +getStrokeForFrame(Int)      │   │ NO_FOLLOW_THROUGH, ELBOW_FAR │
│ +getPhaseForFrame(Int)       │   │ ELBOW_CLOSE, SLOW_STROKE,    │
│ +getFrameInfo(Int)           │   │ FAST_STROKE                  │
└──────────────────────────────┘   └──────────────────────────────┘

                                   ┌──────────────────────────────┐
                                   │ TechniqueRecommendations(obj)│
                                   ├──────────────────────────────┤
                                   │ STRAIGHTEN_WRIST, ROTATE_MORE│
                                   │ ADJUST_CONTACT_HEIGHT,       │
                                   │ COMPLETE_FOLLOW_THROUGH,     │
                                   │ KEEP_ELBOW_CLOSE, etc.       │
                                   └──────────────────────────────┘
```

## Entities

### Landmark3D (NEW — replaces MediaPipe NormalizedLandmark in shared code)

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| x | Float | Horizontal position (normalized 0-1) | Required |
| y | Float | Vertical position (normalized 0-1) | Required |
| z | Float | Depth position | Required |
| visibility | Float | Landmark visibility confidence | 0.0-1.0, default 0.0 |
| presence | Float | Landmark presence confidence | 0.0-1.0, default 0.0 |

**Origin**: New shared type. Replaces direct usage of `com.google.mediapipe.tasks.components.containers.NormalizedLandmark`.
**Note**: Maps 1:1 from `JsonLandmark` (existing in `JsonStrokeDetector.kt`) with identical fields. `JsonLandmark` also has an `index` field which is represented by list position in `PoseFrame`.

### PoseFrame (NEW — replaces MediaPipe PoseLandmarkerResult in shared analysis)

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| frameIndex | Int | Ordinal frame number | >= 0 |
| timestampMs | Long | Frame capture timestamp | >= 0 |
| landmarks | List\<Landmark3D\> | 33 body landmarks (MediaPipe pose format) | Non-empty |

**Origin**: Combines `JsonPoseFrame` (existing) with the landmark list from `PoseLandmarkerResult`. Subsumes `JsonPoseFrame`.

### StrokePhase (EXISTING — extract as-is)

| Value | Description |
|-------|-------------|
| READY | Ready position |
| BACKSWING | Backswing |
| FORWARD_SWING | Forward swing |
| CONTACT | Ball contact point |
| FOLLOW_THROUGH | Follow-through |
| RECOVERY | Return to ready |

**Source file**: `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:104-111`
**Changes**: None — extract verbatim.

### CorrectionType (EXISTING — extract as-is)

| Value | Description |
|-------|-------------|
| WRIST | Wrist angle correction |
| BODY_ROTATION | Body rotation correction |
| FOLLOW_THROUGH | Follow-through correction |
| CONTACT_HEIGHT | Contact height correction |
| ELBOW_POSITION | Elbow position correction |
| STROKE_SPEED | Stroke speed correction |
| GENERAL | General technique correction |

**Source file**: `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:13-21`
**Changes**: None — extract verbatim.

### FeedbackItem (EXISTING — modify `strokeLandmarks` type)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| message | String | — | Feedback text |
| type | CorrectionType | — | Correction category |
| isPositive | Boolean | false | Positive or negative feedback |
| strokeLandmarks | List\<List\<Landmark3D\>\> | emptyList() | Captured pose frames for visualization |

**Source file**: `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:26-31`
**Changes**: `strokeLandmarks` type changes from `List<List<NormalizedLandmark>>` to `List<List<Landmark3D>>`. The mapper layer converts at the boundary.

### AnalysisResult (EXISTING — extract as-is)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| timestamp | Long | currentTimeMillis() | Analysis timestamp |
| wristAngle | Float? | null | Measured wrist angle in degrees |
| bodyRotation | Float? | null | Body rotation angle in degrees |
| followThroughAngle | Float? | null | Follow-through angle in degrees |
| contactHeight | Float? | null | Relative contact height |
| strokeSpeed | Float? | null | Stroke speed in m/s |
| elbowBodyDistance | Float? | null | Elbow-to-body distance in meters |
| isWristAngleValid | Boolean | false | Within acceptable range |
| isBodyRotationValid | Boolean | false | Sufficient rotation |
| isFollowThroughValid | Boolean | false | Proper follow-through |
| isContactHeightValid | Boolean | false | Correct height |
| isStrokeSpeedValid | Boolean | false | Within speed range |
| isElbowPositionValid | Boolean | false | Elbow close enough |
| overallScore | Float | 0f | Overall technique score (0-100) |
| phase | StrokePhase | READY | Current stroke phase |
| errors | List\<String\> | emptyList() | Error codes (TechniqueErrors keys) |
| recommendations | List\<String\> | emptyList() | Recommendation codes |
| feedbackItems | List\<FeedbackItem\> | emptyList() | Detailed feedback |

**Source file**: `app/src/main/java/com/ttcoachai/models/AnalysisResult.kt:36-65`
**Changes**: `timestamp` default must use `expect/actual` or parameter injection since `System.currentTimeMillis()` is JVM-only. Use `kotlinx.datetime` or a simple `expect fun currentTimeMillis(): Long` / `actual fun` pair. Alternatively, make the default `0L` and set it at call site.
**Methods preserved**: `isSuccessful()`, `getSummary()`, `getPrimaryError()`, `getPrimaryRecommendation()`

### ExerciseParameters (EXISTING — extract as-is)

All 18 fields preserved exactly. See source file for complete field list.

**Source file**: `app/src/main/java/com/ttcoachai/models/ExerciseParameters.kt`
**Changes**: Remove `fromSharedPreferences()` factory (Android-specific concept). Keep `forehandDrive()`, `backhandDrive()`, `forehandDriveBeginner()` presets and all validation methods.

### DetectedStroke (EXISTING — extract as-is)

| Field | Type | Description |
|-------|------|-------------|
| strokeIndex | Int | Sequential stroke number |
| preparationStartFrame | Int | Start of backswing phase |
| preparationEndFrame | Int | End of backswing phase |
| forwardStartFrame | Int | Start of forward swing |
| contactFrame | Int | Ball contact frame |
| forwardEndFrame | Int | End of forward swing |
| returnStartFrame | Int | Start of follow-through |
| returnEndFrame | Int | End of follow-through |
| backswingMinValue | Float | Minimum tracking value in backswing |
| forwardPeakValue | Float | Peak tracking value in forward swing |
| peakVelocity | Float | Maximum velocity during stroke |
| strokeDurationMs | Long | Total stroke duration |
| forwardSwingDurationMs | Long | Forward swing phase duration |
| isComplete | Boolean | Whether all phases were detected |

**Source file**: `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:77-117`
**Methods preserved**: `containsFrame()`, `getPhaseForFrame()`

### StrokeDetectorConfig (EXISTING — extract as-is)

All 13 fields preserved exactly. Companion presets `FOREHAND` and `BACKHAND` preserved.

**Source file**: `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:24-51`

### StrokeDetectionResult (EXISTING — extract as-is)

**Source file**: `app/src/main/java/com/ttcoachai/services/JsonStrokeDetector.kt:132-151`
**Methods preserved**: `getStrokeForFrame()`, `getPhaseForFrame()`, `getFrameInfo()`

### Supporting Types (EXISTING — extract as-is)

- **TrackingAxis**: Enum `X, Y, Z`
- **FramePhaseInfo**: Data class with `frameIndex`, `phase`, `strokeIndex?`, `phaseProgress`
- **TechniqueErrors**: Object with String constants (error codes)
- **TechniqueRecommendations**: Object with String constants (recommendation codes)

## Relationships

| From | To | Cardinality | Description |
|------|----|-------------|-------------|
| PoseFrame | Landmark3D | 1:33 | Frame contains 33 body landmarks |
| AnalysisResult | StrokePhase | N:1 | Result has one detected phase |
| AnalysisResult | FeedbackItem | 1:N | Result contains feedback items |
| FeedbackItem | CorrectionType | N:1 | Each feedback has a correction type |
| FeedbackItem | Landmark3D | 1:N:N | Captured stroke landmarks (nested lists) |
| DetectedStroke | StrokePhase | maps | `getPhaseForFrame()` returns phase |
| StrokeDetectionResult | DetectedStroke | 1:N | Detection result contains strokes |
| StrokeDetectionResult | FramePhaseInfo | 1:N | Phase info for every frame |
| FramePhaseInfo | StrokePhase | N:1 | Each frame has one phase |
| ExerciseParameters | AnalysisResult | validates | Validation methods check result metrics |

## Migration Notes

1. **`JsonLandmark` → `Landmark3D`**: `JsonLandmark` is subsumed by `Landmark3D`. The `index` field from `JsonLandmark` is dropped (position in list implies index). Code using `JsonLandmark.index` must use `List.indexOf()` or enumeration index.

2. **`JsonPoseFrame` → `PoseFrame`**: Direct rename/replacement. `JsonPoseFrame` is removed. All references in `JsonStrokeDetector` update to use `PoseFrame`.

3. **`System.currentTimeMillis()`**: Used as default in `AnalysisResult.timestamp`. In shared code, either:
   - (a) Change default to `0L` and set timestamp at call sites, OR
   - (b) Add `expect fun currentTimeMillis(): Long` in commonMain with `actual` for JVM/Android.
   - Recommendation: Option (a) — simpler, avoids `expect/actual` overhead for one usage.

4. **`fromSharedPreferences()`**: Stays in Android module as an extension function on `ExerciseParameters`.
