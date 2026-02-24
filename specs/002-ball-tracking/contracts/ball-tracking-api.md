# Contract: Ball Tracking Public API

**Module**: `:shared` (`com.ttcoachai.shared.tracking`) + `:app` (`com.ttcoachai.tracking`)
**Consumers**: `:app` Android application module

## Overview

Ball tracking adds three shared KMP entry points (trajectory filter, segmenter, synchronizer) and three Android-only components (ball detector, ROI manager, camera optimizer). The shared module remains pure Kotlin with zero platform-specific imports.

## Shared Module Entry Points (`commonMain`)

### 1. TrajectoryFilter — Parabolic Curve Fitting

```kotlin
package com.ttcoachai.shared.tracking

object TrajectoryFilter {
    /**
     * Fit a decoupled parabolic model to a sequence of ball detections.
     * Horizontal: x = ax + bx*t (linear)
     * Vertical:   y = ay + by*t + cy*t² (quadratic)
     *
     * @param detections Detected ball positions (min 3 for parabolic, 2 for linear)
     * @return ParabolicFit coefficients, or null if fewer than 2 detections
     */
    fun fit(detections: List<BallDetection>): ParabolicFit?

    /**
     * Evaluate the fitted model at a specific timestamp to get interpolated position.
     *
     * @param fit The fitted parabola coefficients
     * @param timestampMs Target timestamp
     * @param referenceTimestampMs Base timestamp (t=0 reference)
     * @return Interpolated (x, y) position (normalized 0-1)
     */
    fun evaluate(fit: ParabolicFit, timestampMs: Long, referenceTimestampMs: Long): Pair<Float, Float>

    /**
     * Compute RMS error of the fit against detected positions.
     *
     * @param fit Fitted coefficients
     * @param detections Original detected positions
     * @return RMS deviation in normalized coordinates
     */
    fun rmsError(fit: ParabolicFit, detections: List<BallDetection>): Double

    /**
     * Fill gaps: generate BallPosition2D for every frame in the range,
     * using detected positions where available and interpolated positions elsewhere.
     *
     * @param fit Fitted coefficients
     * @param detections Original detected positions
     * @param startFrameIndex First frame to generate
     * @param endFrameIndex Last frame to generate
     * @param frameDurationMs Duration of one frame in milliseconds
     * @return Ordered list of BallPosition2D with DataSource tags
     */
    fun fillGaps(
        fit: ParabolicFit,
        detections: List<BallDetection>,
        startFrameIndex: Int,
        endFrameIndex: Int,
        frameDurationMs: Long
    ): List<BallPosition2D>
}
```

**Behavioral contract**:
- `fit()` returns `null` if fewer than 2 detections provided
- With exactly 2 detections, returns a linear fit (cy = 0.0)
- With 3+ detections, returns a full parabolic fit
- `evaluate()` returns normalized coordinates (0-1) — may exceed 0-1 for extrapolation
- `rmsError()` returns 0.0 for a perfect fit
- `fillGaps()` tags positions with `DataSource.DETECTED` or `DataSource.INTERPOLATED`

### 2. TrajectorySegmenter — Contact Detection and Segment Splitting

```kotlin
package com.ttcoachai.shared.tracking

class TrajectorySegmenter(
    private val bounceAngleThreshold: Float = 30f,
    private val speedRatioThreshold: Float = 1.8f,
    private val directionAngleThreshold: Float = 30f,
    private val maxFitRmsError: Double = 0.02
) {
    /**
     * Detect contact events in a sequence of ball detections.
     *
     * @param detections Time-ordered ball detections (DETECTED status only)
     * @return List of detected contact events
     */
    fun detectContacts(detections: List<BallDetection>): List<ContactEvent>

    /**
     * Split detections into trajectory segments at contact events,
     * fit each segment, and fill gaps.
     *
     * @param detections All ball detections for a rally (including NOT_DETECTED)
     * @param frameDurationMs Duration of one frame in ms
     * @return Ordered list of fitted trajectory segments
     */
    fun segment(
        detections: List<BallDetection>,
        frameDurationMs: Long
    ): List<TrajectorySegment>
}
```

**Behavioral contract**:
- `detectContacts()` requires at least 3 detections; returns empty list otherwise
- Contact point belongs to both adjacent segments (continuity)
- `segment()` calls `detectContacts()`, splits, fits each segment via `TrajectoryFilter`, then validates
- Segments with `fitRmsError > maxFitRmsError` trigger recursive sub-splitting (max 1 level)
- Empty input → empty segment list

### 3. TimelineSynchronizer — Merge Ball + Skeleton Streams

```kotlin
package com.ttcoachai.shared.tracking

class TimelineSynchronizer {
    /**
     * Merge pose frames and ball detections into synchronized frames.
     * Post-hoc batch mode for recorded video analysis.
     *
     * @param poses Pose frames sorted by timestampMs
     * @param balls Ball detections sorted by timestampMs
     * @param allTimestampsMs Master timeline of all frame timestamps
     * @return Ordered list of SynchronizedFrame
     */
    fun merge(
        poses: List<PoseFrame>,
        balls: List<BallDetection>,
        allTimestampsMs: List<Long>
    ): List<SynchronizedFrame>

    /**
     * Interpolate a ball position between two known detections.
     * Uses linear interpolation (sufficient for 1-frame gaps in sync context).
     *
     * @param before Earlier detection
     * @param after Later detection
     * @param targetTimestampMs Timestamp to interpolate at
     * @return Interpolated BallDetection with INTERPOLATED status metadata
     */
    fun interpolateBall(
        before: BallDetection,
        after: BallDetection,
        targetTimestampMs: Long
    ): BallDetection
}
```

**Behavioral contract**:
- `merge()` produces exactly one `SynchronizedFrame` per timestamp in `allTimestampsMs`
- Missing data → `null` field with `DataSource.ABSENT`
- Interpolated data → non-null field with `DataSource.INTERPOLATED`
- Directly detected data → non-null field with `DataSource.DETECTED`
- Frame order matches `allTimestampsMs` order

## Android Module Entry Points (`:app` only)

### 4. BallDetector — OpenCV Color/Shape Detection

```kotlin
package com.ttcoachai.tracking

class BallDetector(
    private val ballColor: BallColor = BallColor.WHITE,
    private val expectedRadiusRange: IntRange = 4..25
) {
    /**
     * Detect the ball in a bitmap frame within the given ROI.
     *
     * @param bitmap Frame bitmap (ARGB_8888)
     * @param roi Region of interest to search within
     * @param frameIndex Current frame number
     * @param timestampMs Frame timestamp
     * @return BallDetection with status and position
     */
    fun detect(
        bitmap: Bitmap,
        roi: RegionOfInterest,
        frameIndex: Int,
        timestampMs: Long
    ): BallDetection

    /** Release pre-allocated OpenCV Mat objects */
    fun release()

    enum class BallColor {
        WHITE, ORANGE
    }
}
```

**Behavioral contract**:
- Returns `BallDetection` with `status = DETECTED` when ball found with confidence above threshold
- Returns `BallDetection` with `status = NOT_DETECTED` when no ball found in ROI
- Coordinates are normalized (0-1) relative to the full frame (not the ROI)
- Pre-allocates and reuses `Mat` objects; caller must invoke `release()` when done
- Thread-safe: can be called from background executor

### 5. ROIManager — Region of Interest Management

```kotlin
package com.ttcoachai.tracking

class ROIManager {
    /**
     * Create a default ROI based on frame dimensions.
     * Assumes camera behind/beside player looking at table.
     * Default: lower 75% height, central 80% width.
     */
    fun createDefault(frameWidth: Int, frameHeight: Int): RegionOfInterest

    /**
     * Update ROI based on recent ball detection positions.
     * Adapts ROI to track ball movement area over time.
     *
     * @param currentRoi Current ROI
     * @param recentDetections Last N detected ball positions
     * @return Adjusted ROI (may expand or shift)
     */
    fun adapt(
        currentRoi: RegionOfInterest,
        recentDetections: List<BallDetection>
    ): RegionOfInterest
}
```

**Behavioral contract**:
- `createDefault()` always returns a valid ROI within frame bounds
- `adapt()` never returns an ROI outside frame bounds
- `adapt()` with empty detections returns `currentRoi` unchanged

### 6. CameraOptimizer — Exposure Control for Ball Tracking

```kotlin
package com.ttcoachai.tracking

class CameraOptimizer(
    private val camera2Control: Camera2CameraControl,
    private val cameraCharacteristics: CameraCharacteristics
) {
    /**
     * Apply initial optimized camera settings for ball tracking.
     * Sets manual exposure (2ms), ISO 800, 30 FPS.
     * Falls back to AE with compensation if manual mode unsupported.
     */
    fun applyBallTrackingMode()

    /**
     * Restore default camera settings (auto-exposure).
     */
    fun restoreDefaultMode()

    /**
     * Called periodically with frame brightness to adapt exposure.
     * Adjusts ISO first (up to 3200), then exposure time (up to 8ms).
     * Rate-limited to max 1 adjustment per 2 seconds.
     *
     * @param averageLuminance Average brightness of frame center (0-255)
     */
    fun onBrightnessUpdate(averageLuminance: Float)

    /** Current camera configuration state */
    val currentConfig: CameraConfiguration
}
```

**Behavioral contract**:
- `applyBallTrackingMode()` queries device capabilities and applies supported settings
- Exposure time never exceeds 8ms (8,000,000 ns)
- ISO never exceeds 3200
- `onBrightnessUpdate()` rate-limited: max 1 adjustment per 2 seconds
- `restoreDefaultMode()` returns to `CONTROL_AE_MODE_ON`

## Data Types Contract

All types in `com.ttcoachai.shared.models` and `com.ttcoachai.shared.tracking` are:
- Kotlin `data class` or `enum class`
- Immutable (val-only fields)
- Serialization-free (no annotations, no framework coupling)
- Constructed via primary constructor
- Compatible with `commonMain` (no platform-specific imports)

## Integration Points with Phase 1

| Phase 1 Component | Integration | Direction |
|---|---|---|
| `PoseLandmarkerProcessor` | Passes bitmap to `BallDetector.detect()` after rotation | App → App |
| `CameraManager` | Receives `CameraOptimizer` for exposure control | App → App |
| `PoseFrame` | Referenced by `SynchronizedFrame.pose` | Shared → Shared |
| `OverlayView` | Extended to draw ball position and trajectory curves | App → App |
| `TrainingStateManager` | Controls when ball tracking is active | App → App |
