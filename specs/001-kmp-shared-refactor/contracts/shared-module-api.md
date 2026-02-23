# Contract: Shared Module Public API

**Module**: `:shared` (`com.ttcoachai.shared`)
**Consumers**: `:app` (Android application module)

## Overview

The `:shared` module exposes a pure-Kotlin API for stroke analysis. The Android app converts MediaPipe types to shared types at the boundary, then calls shared functions for all analysis logic.

## Entry Points

### 1. StrokeAnalyzer — Single-Frame Analysis

```kotlin
package com.ttcoachai.shared.analysis

object StrokeAnalyzer {
    /**
     * Analyze a single frame of pose landmarks against exercise parameters.
     *
     * @param landmarks List of 33 Landmark3D points from a single frame
     * @param parameters Exercise-specific thresholds
     * @param phase Current stroke phase (from StrokePhaseDetector)
     * @return AnalysisResult with all computed metrics and validation flags
     */
    fun analyzeStroke(
        landmarks: List<Landmark3D>,
        parameters: ExerciseParameters,
        phase: StrokePhase = StrokePhase.READY
    ): AnalysisResult
}
```

**Behavioral contract**:
- Returns non-null `AnalysisResult` for any input
- If `landmarks.size < 33`, metrics requiring missing landmarks are `null`
- NaN/Infinite coordinates produce `null` for affected metrics (no crash)
- `overallScore` is 0-100 Float, computed as weighted average of valid metrics

### 2. AngleCalculations — Low-Level Math

```kotlin
package com.ttcoachai.shared.analysis

object AngleCalculations {
    /** 3D angle at point b formed by segments ba and bc (degrees, via dot product) */
    fun calculate3DAngle(a: Landmark3D, b: Landmark3D, c: Landmark3D): Float

    /** Wrist angle using landmarks 14 (elbow), 16 (wrist), 20 (index finger) */
    fun calculateWristAngle(landmarks: List<Landmark3D>): Float?

    /** Body rotation using landmarks 11, 12, 23, 24 (shoulders + hips) */
    fun calculateBodyRotation(landmarks: List<Landmark3D>): Float?

    /** Follow-through angle using landmarks 12 (shoulder), 14 (elbow), 16 (wrist) */
    fun calculateFollowThroughAngle(landmarks: List<Landmark3D>): Float?
}
```

**Behavioral contract**:
- Returns `null` when required landmark indices are out of bounds
- Angle results are in degrees [0, 180]
- Identical output to current `MotionAnalyzer` methods for same input coordinates

### 3. MetricCalculations — Distance and Speed

```kotlin
package com.ttcoachai.shared.analysis

object MetricCalculations {
    /** Contact height relative to hip — landmarks 16 (wrist), 24 (hip) */
    fun calculateContactHeight(landmarks: List<Landmark3D>): Float?

    /** Euclidean 2D distance between elbow (14) and hip (24) */
    fun calculateElbowBodyDistance(landmarks: List<Landmark3D>): Float?

    /** Stroke speed from wrist displacement between two frames / time delta */
    fun calculateStrokeSpeed(
        currentLandmarks: List<Landmark3D>,
        previousLandmarks: List<Landmark3D>,
        timeDeltaMs: Long
    ): Float?
}
```

### 4. JsonStrokeDetector — Batch Stroke Detection

```kotlin
package com.ttcoachai.shared.detection

class JsonStrokeDetector(
    private val config: StrokeDetectorConfig = StrokeDetectorConfig.FOREHAND
) {
    /**
     * Detect strokes from a sequence of pose frames.
     *
     * @param frames Ordered list of PoseFrame (by timestampMs)
     * @return StrokeDetectionResult with detected strokes and per-frame phases
     */
    fun detect(frames: List<PoseFrame>): StrokeDetectionResult
}
```

**Behavioral contract**:
- Empty input → `StrokeDetectionResult(emptyList(), emptyList(), 0)`
- Frame order must be ascending by `timestampMs`
- Returns same stroke boundaries as current implementation for identical input data

### 5. StrokePhaseDetector — Real-Time Phase Detection

```kotlin
package com.ttcoachai.shared.detection

class StrokePhaseDetector {
    /**
     * Process a single frame and return the current stroke phase.
     * Stateful: maintains velocity history across calls.
     *
     * @param landmarks List of 33 Landmark3D (world coordinates)
     * @param timestampMs Frame timestamp
     * @return Current StrokePhase
     */
    fun detect(landmarks: List<Landmark3D>, timestampMs: Long): StrokePhase

    /** Reset internal state */
    fun reset()
}
```

**Behavioral contract**:
- Stateful object — call `reset()` between sessions
- Uses landmark 16 (right wrist) Z-coordinate velocity
- Phase transitions: READY → BACKSWING → FORWARD_SWING → CONTACT → FOLLOW_THROUGH → RECOVERY → READY

## Data Types Contract

All types in `com.ttcoachai.shared.models` are:
- Kotlin `data class` or `enum class`
- Immutable (val-only fields)
- Serialization-free (no annotations, no framework coupling)
- Constructed via primary constructor (no builder pattern)

## Android Mapper Contract (stays in `:app`)

```kotlin
package com.ttcoachai.mappers

object MediaPipeMapper {
    /** Convert a MediaPipe NormalizedLandmark to shared Landmark3D */
    fun toLandmark3D(landmark: NormalizedLandmark): Landmark3D

    /** Convert a full PoseLandmarkerResult to a list of Landmark3D */
    fun toLandmarkList(result: PoseLandmarkerResult): List<Landmark3D>

    /** Convert world landmarks (for phase detection) */
    fun toWorldLandmarkList(result: PoseLandmarkerResult): List<Landmark3D>
}
```

**Behavioral contract**:
- `toLandmark3D` maps x→x, y→y, z→z, visibility()→visibility, presence()→presence with no transformation
- Empty landmark list in result → empty Landmark3D list (no crash)
- Preserves MediaPipe landmark ordering (index 0-32)
