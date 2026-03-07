# Data Model: Ball Tracking and Trajectory Prediction

**Feature**: 002-ball-tracking | **Date**: 2026-02-24

## Entity Diagram

```
┌─────────────────────────┐
│    BallDetection         │
├─────────────────────────┤
│ x: Float                │ ◄── Normalized 0..1
│ y: Float                │ ◄── Normalized 0..1
│ confidence: Float       │ ◄── 0..1
│ radiusPx: Float         │ ◄── Detected radius in pixels
│ frameIndex: Int          │
│ timestampMs: Long        │
│ status: BallDetectionStatus │
└────────────┬────────────┘
             │ *
             ▼
┌─────────────────────────┐       ┌──────────────────────────┐
│   TrajectorySegment      │       │    ContactEvent           │
├─────────────────────────┤       ├──────────────────────────┤
│ segmentIndex: Int        │──────►│ type: ContactType         │
│ startFrameIndex: Int     │ 0..1  │ frameIndex: Int           │
│ endFrameIndex: Int       │ start │ timestampMs: Long         │
│ detections: List<BD>     │◄──────│ position: Pair<Fl,Fl>     │
│ fittedPositions: List<BP>│ 0..1  │ velocityBefore: Fl        │
│ contactBefore: CE?       │ end   │ velocityAfter: Fl         │
│ contactAfter: CE?        │       │ confidence: Float         │
│ fitCoefficients: PF      │       └──────────────────────────┘
│ fitRmsError: Double      │
│ segmentDurationMs: Long  │       ┌──────────────────────────┐
└─────────────────────────┘       │    ContactType (enum)     │
             │                     ├──────────────────────────┤
             │ uses                │ BOUNCE                   │
             ▼                     │ PADDLE_CONTACT           │
┌─────────────────────────┐       │ NET_CLIP                 │
│    ParabolicFit          │       │ UNKNOWN_CONTACT          │
├─────────────────────────┤       └──────────────────────────┘
│ ax: Double               │ ◄── x = ax + bx*t
│ bx: Double               │
│ ay: Double               │ ◄── y = ay + by*t + cy*t²
│ by: Double               │
│ cy: Double               │
└─────────────────────────┘

┌─────────────────────────┐
│ BallDetectionStatus(enum)│
├─────────────────────────┤       ┌──────────────────────────┐
│ DETECTED                 │       │    BallPosition2D         │
│ NOT_DETECTED             │       ├──────────────────────────┤
│ OUT_OF_FRAME             │       │ x: Float                 │
└─────────────────────────┘       │ y: Float                 │
                                   │ frameIndex: Int           │
                                   │ timestampMs: Long         │
                                   │ source: DataSource        │
                                   └──────────────────────────┘

┌─────────────────────────┐
│   RegionOfInterest       │       ┌──────────────────────────┐
├─────────────────────────┤       │    DataSource (enum)      │
│ x: Int                   │       ├──────────────────────────┤
│ y: Int                   │       │ DETECTED                 │
│ width: Int               │       │ INTERPOLATED             │
│ height: Int              │       │ ABSENT                   │
└─────────────────────────┘       └──────────────────────────┘

┌─────────────────────────┐
│   SynchronizedFrame      │       ┌──────────────────────────┐
├─────────────────────────┤       │   CameraConfiguration     │
│ frameIndex: Int          │       ├──────────────────────────┤
│ timestampMs: Long        │       │ exposureTimeNs: Long      │
│ pose: PoseFrame?         │       │ isoSensitivity: Int       │
│ ball: BallDetection?     │       │ targetFps: Int            │
│ poseSource: DataSource   │       │ isAutoExposure: Boolean   │
│ ballSource: DataSource   │       │ luminanceEma: Float       │
└─────────────────────────┘       └──────────────────────────┘

Existing entities (from Phase 1, unchanged):
┌─────────────────────┐       ┌───────────────────────┐
│     Landmark3D      │◄──────│      PoseFrame        │
├─────────────────────┤  1..* ├───────────────────────┤
│ x: Float            │       │ frameIndex: Int        │
│ y: Float            │       │ timestampMs: Long      │
│ z: Float            │       │ landmarks: List<L3D>   │
│ visibility: Float   │       └───────────────────────┘
│ presence: Float     │
└─────────────────────┘
```

## Entities

### BallDetection (NEW — shared KMP `commonMain`)

Represents a single detection of the ball in one frame.

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| x | Float | — | Horizontal position (normalized 0-1 within frame) | 0.0-1.0 |
| y | Float | — | Vertical position (normalized 0-1 within frame) | 0.0-1.0 |
| confidence | Float | 0f | Detection confidence score | 0.0-1.0 |
| radiusPx | Float | 0f | Detected radius in pixels | >= 0 |
| frameIndex | Int | — | Sequential frame number | >= 0 |
| timestampMs | Long | — | Frame capture timestamp (ms) | >= 0 |
| status | BallDetectionStatus | DETECTED | Detection result status | Required |

**Validation rules**:
- When `status == DETECTED`: `x`, `y`, `confidence`, and `radiusPx` must be positive.
- When `status == NOT_DETECTED` or `OUT_OF_FRAME`: `x`, `y`, `confidence`, `radiusPx` are 0.
- `confidence` below a configurable threshold (default 0.5) should be treated as NOT_DETECTED by consumers.

**Source**: Spec FR-001, FR-009, FR-011.

### BallDetectionStatus (NEW — shared KMP `commonMain`)

| Value | Description |
|-------|-------------|
| DETECTED | Ball found with confidence above threshold |
| NOT_DETECTED | Ball not found in frame (in ROI but not detected) |
| OUT_OF_FRAME | Ball has left the camera field of view |

**Source**: Spec FR-009, FR-011, edge case "ball out of camera field of view."

### TrajectorySegment (NEW — shared KMP `commonMain`)

A continuous arc of ball flight between two direction-change events.

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| segmentIndex | Int | — | Sequential segment number within rally | >= 0 |
| startFrameIndex | Int | — | First frame of this segment | >= 0 |
| endFrameIndex | Int | — | Last frame of this segment | >= startFrameIndex |
| detections | List\<BallDetection\> | — | Actual detected positions in this segment | Non-empty for fitted segments |
| fittedPositions | List\<BallPosition2D\> | emptyList() | All frames including interpolated | Populated after fitting |
| contactBefore | ContactEvent? | null | Event that started this segment | null for first segment |
| contactAfter | ContactEvent? | null | Event that ended this segment | null for last segment |
| fitCoefficients | ParabolicFit | — | Fitted parabola coefficients | Required for segments with ≥3 detections |
| fitRmsError | Double | 0.0 | RMS deviation of detections from fit (pixels) | >= 0 |
| segmentDurationMs | Long | 0 | Duration from first to last frame | >= 0 |

**State transitions**:
```
COLLECTING → FITTED → VALIDATED
     ↓           ↓
  (< 3 pts)  (high RMS)
     ↓           ↓
  LINEAR_FIT  SPLIT_REQUIRED
```

- `COLLECTING`: Accumulating detections, not yet fitted.
- `FITTED`: Parabola fit complete, awaiting validation.
- `VALIDATED`: Fit quality within threshold.
- `LINEAR_FIT`: Only 2 detections; linear interpolation used instead of parabola.
- `SPLIT_REQUIRED`: High RMS error suggests a missed contact event; attempt sub-splitting.

**Source**: Spec FR-005, FR-006.

### ContactEvent (NEW — shared KMP `commonMain`)

A detected trajectory break (bounce, paddle contact, net clip).

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| type | ContactType | — | Classification of the contact | Required |
| frameIndex | Int | — | Frame where contact occurred | >= 0 |
| timestampMs | Long | — | Contact timestamp (ms) | >= 0 |
| position | Pair\<Float, Float\> | — | Ball position at contact (normalized) | Both 0.0-1.0 |
| velocityBefore | Float | 0f | Ball speed before contact (px/frame) | >= 0 |
| velocityAfter | Float | 0f | Ball speed after contact (px/frame) | >= 0 |
| confidence | Float | 0f | Detection confidence | 0.0-1.0 |

**Source**: Spec FR-006, edge case "net clips."

### ContactType (NEW — shared KMP `commonMain`)

| Value | Description | Detection Signal |
|-------|-------------|------------------|
| BOUNCE | Ball bounced on table surface | Vertical velocity reversal near table y |
| PADDLE_CONTACT | Ball struck by paddle | Speed ratio > 1.8x |
| NET_CLIP | Ball grazed the net | Direction angle > 30° without speed spike |
| UNKNOWN_CONTACT | Unclassified direction change | Direction angle > 30°, unclassified |

**Source**: Spec FR-006.

### ParabolicFit (NEW — shared KMP `commonMain`)

Coefficients of the decoupled parabolic trajectory model.

| Field | Type | Description |
|-------|------|-------------|
| ax | Double | X-intercept: `screen_x = ax + bx * t` |
| bx | Double | X-slope (horizontal velocity in px/frame) |
| ay | Double | Y-intercept: `screen_y = ay + by * t + cy * t²` |
| by | Double | Y-slope (initial vertical velocity) |
| cy | Double | Y-curvature (gravity effect in screen coords) |

**Source**: Research R7.

### BallPosition2D (NEW — shared KMP `commonMain`)

A ball position in a frame — either detected or interpolated.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| x | Float | Horizontal position (normalized 0-1) | 0.0-1.0 |
| y | Float | Vertical position (normalized 0-1) | 0.0-1.0 |
| frameIndex | Int | Frame number | >= 0 |
| timestampMs | Long | Frame timestamp (ms) | >= 0 |
| source | DataSource | Whether detected or interpolated | Required |

### DataSource (NEW — shared KMP `commonMain`)

| Value | Description |
|-------|-------------|
| DETECTED | Directly detected in this frame |
| INTERPOLATED | Interpolated from neighboring frames or trajectory fit |
| ABSENT | No data available |

### RegionOfInterest (NEW — shared KMP `commonMain`)

Rectangular area for ball search.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| x | Int | Top-left X in frame coordinates (pixels) | >= 0 |
| y | Int | Top-left Y in frame coordinates (pixels) | >= 0 |
| width | Int | ROI width (pixels) | > 0 |
| height | Int | ROI height (pixels) | > 0 |

**Factory method**: `createDefault(frameWidth, frameHeight)` → lower 75% height, central 80% width.

**Source**: Spec FR-002.

### SynchronizedFrame (NEW — shared KMP `commonMain`)

Unified data point combining skeleton pose and ball position.

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| frameIndex | Int | — | Sequential frame number | >= 0 |
| timestampMs | Long | — | Frame timestamp (ms) | >= 0 |
| pose | PoseFrame? | null | Skeleton pose data (from Phase 1) | Null if pose detection failed/skipped |
| ball | BallDetection? | null | Ball detection data | Null if ball detection failed/skipped |
| poseSource | DataSource | ABSENT | Provenance of pose data | Required |
| ballSource | DataSource | ABSENT | Provenance of ball data | Required |

**Design rationale**: Both fields nullable — `null` is semantically clear ("no data for this timestamp"). Consumers can filter by DataSource (e.g., "only DETECTED" for high-confidence analysis, include INTERPOLATED for smooth visualization).

**Source**: Spec FR-007, FR-008, SC-004.

### CameraConfiguration (NEW — shared KMP `commonMain`)

Camera parameter settings model for ball tracking mode.

| Field | Type | Default | Description | Constraints |
|-------|------|---------|-------------|-------------|
| exposureTimeNs | Long | 2_000_000 | Exposure time in nanoseconds | 500_000 - 8_000_000 |
| isoSensitivity | Int | 800 | ISO sensitivity | 100 - 3200 |
| targetFps | Int | 30 | Target frame rate | 15 or 30 |
| isAutoExposure | Boolean | false | Whether using built-in AE | — |
| luminanceEma | Float | 120f | Current brightness EMA (0-255) | 0-255 |

**Source**: Spec FR-003, FR-004, User Story 4.

## Relationships

| From | To | Cardinality | Description |
|------|----|-------------|-------------|
| TrajectorySegment | BallDetection | 1:N | Segment contains detected ball positions |
| TrajectorySegment | BallPosition2D | 1:N | Segment contains all positions (detected + interpolated) |
| TrajectorySegment | ContactEvent | 1:0..1 (×2) | Segment bounded by contact events |
| TrajectorySegment | ParabolicFit | 1:1 | Fitted parabola coefficients |
| SynchronizedFrame | PoseFrame | 1:0..1 | Optional skeleton data |
| SynchronizedFrame | BallDetection | 1:0..1 | Optional ball data |
| BallDetection | BallDetectionStatus | N:1 | Each detection has a status |
| BallPosition2D | DataSource | N:1 | Each position has provenance |
| SynchronizedFrame | DataSource | N:2 | Pose source + ball source |
| ContactEvent | ContactType | N:1 | Each contact has a type |

## Integration with Existing Models

The ball tracking data model integrates with the Phase 1 skeleton tracking model at two points:

1. **SynchronizedFrame wraps PoseFrame**: The existing `PoseFrame` (33 landmarks + timestamp) is referenced by `SynchronizedFrame` as the `pose` field. No changes to `PoseFrame` are needed.

2. **Shared timestamp convention**: Both `PoseFrame.timestampMs` and `BallDetection.timestampMs` use the same source: `ImageProxy.imageInfo.timestamp` converted to milliseconds. This ensures alignment without clock drift.

No existing Phase 1 entities are modified.
