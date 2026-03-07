# Research: Ball Tracking and Trajectory Prediction

**Feature**: 002-ball-tracking | **Date**: 2026-02-24

## R1: Vision Library for Ball Detection

**Decision**: Use OpenCV Android SDK 4.9.0 for color/shape-based ball detection.

**Rationale**: OpenCV provides a complete CV pipeline (color conversion, thresholding, morphology, contour detection) running on CPU via native C++ with JNI. Since MediaPipe pose detection uses the GPU delegate, the two do not compete for the same hardware resources. OpenCV is mature, well-documented, and supports the exact operations needed (HSV conversion, inRange, findContours).

**Alternatives considered**:
- Pure CameraX ImageAnalysis (no extra dependency): Would require reimplementing color thresholding, morphology, and contour detection in Kotlin. Unacceptable effort and quality risk.
- MediaPipe Object Detection: Generic object detector not trained for 40mm table tennis balls. Custom model training required (data collection, labeling pipeline). Overkill when the ball has distinctive color/shape properties.
- TensorFlow Lite custom model: Same training overhead. Not justified for a color/shape problem.

**Integration**: Add `implementation 'org.opencv:opencv:4.9.0'` to `app/build.gradle`. Use ABI filter `arm64-v8a` to limit APK size increase to ~15 MB. The existing `imageProxy.toBitmap()` path provides the input; `Utils.bitmapToMat()` converts to OpenCV Mat.

## R2: Color Detection — Color Space and Threshold Ranges

**Decision**: Use HSV color space as primary detection filter.

**Rationale**: HSV separates color (Hue) from intensity (Value), making detection robust to lighting variation. A white ball under warm vs cool lighting changes in RGB but stays consistent in HSV saturation/value channels.

**Concrete thresholds**:
- **Orange ball**: H: 5-25, S: 100-255, V: 100-255 (covers ITTF standard orange)
- **White ball**: H: 0-180, S: 0-50, V: 200-255 (low saturation + high value)

White detection produces more false positives (table lines, shirts, lights). Mitigated by: ROI restriction, size filtering (expected pixel radius range), and circularity check after contour detection.

**Alternatives considered**:
- RGB thresholding: Too sensitive to lighting conditions. Rejected.
- YCrCb: Useful as secondary validation for white balls but not sufficient as primary method.

## R3: Shape Detection Method

**Decision**: Use contour analysis as the primary method. Reserve Hough Circle Transform as optional secondary validation.

**Rationale**: After color thresholding, `findContours` on a sparse binary mask is fast (~1-2ms on ROI). Contour analysis handles motion-blurred balls (elongated blobs) where HoughCircles fails. Combined filtering by area, circularity (≥0.5), and aspect ratio provides multi-criterion false positive rejection.

**Alternatives considered**:
- HoughCircles: Slower (~5-10ms), parameter-sensitive, fails on motion-blurred non-circular shapes. Rejected as primary method.

**Expected ball pixel size** at typical filming distances (1080p):
- 2m: ~15-20px diameter, 3m: ~10-14px, 4m: ~7-10px. Default `expectedRadiusRange = IntRange(4, 25)`.

## R4: Region of Interest Strategy

**Decision**: Fixed proportional ROI (lower 75% height, central 80% width of frame) as default. Zero-copy `Mat(frame, rect)` sub-matrix crop.

**Rationale**: A default proportional ROI captures the ball in most standard camera placements. The ~60% area reduction gives ~40% speedup on the detection pipeline. Automatic table detection via color/line segmentation deferred to a future enhancement.

**Alternatives considered**:
- Automatic table detection: Adds complexity, can fail on non-standard table colors. Deferred.
- User-drawn ROI: Good future feature but not needed for MVP.

## R5: Performance — OpenCV + MediaPipe at Target Frame Rate

**Decision**: Feasible at 15 FPS (matching existing pose detection throttle). Ball detection adds ~8-9ms per frame; combined total ~40-50ms within the 66ms budget.

**Performance budget per frame (15 FPS, 66ms total)**:
| Operation | Time |
|-----------|------|
| ImageProxy → Bitmap | ~5ms |
| Bitmap → Mat | ~2ms |
| ROI crop (zero-copy) | ~0ms |
| cvtColor RGB→HSV on ROI | ~2ms |
| inRange color threshold | ~1ms |
| Morphology (open + close) | ~2ms |
| findContours + filtering | ~1.5ms |
| **Ball detection total** | **~8-9ms** |
| MediaPipe pose detection | ~30-40ms (GPU) |
| **Combined total** | **~40-50ms** |

**Key optimizations**:
1. Downscale ROI to half resolution for detection (ball still detectable at ~6px radius).
2. Pre-allocate and reuse Mat objects to avoid GC pressure.
3. Run ball detection on `backgroundExecutor` thread alongside (sequential) MediaPipe async call.
4. Skip ball detection when training is paused.

## R6: CameraX to OpenCV Integration

**Decision**: Convert `ImageProxy` → `Bitmap` (existing path) → `Mat` via `Utils.bitmapToMat()`. Do not use direct YUV buffer access.

**Rationale**: The bitmap already exists in the pipeline for pose detection. Reusing it avoids extra conversion. `Utils.bitmapToMat()` is a single well-tested call. Direct YUV buffer access has device-specific stride and padding issues.

**Integration point**: Ball detection plugs into `PoseLandmarkerProcessor.detectLiveStream()` after bitmap rotation, before or after the MediaPipe `detectAsync()` call (which is non-blocking).

## R7: Parabolic Trajectory Model

**Decision**: Decoupled 2D parabolic model with separate horizontal (linear) and vertical (quadratic) components in screen coordinates.

**Model**:
- `screen_x(t) = ax + bx * t` (constant horizontal velocity)
- `screen_y(t) = ay + by * t + cy * t²` (gravity-influenced vertical)

5 parameters total. Solved via ordinary least squares (normal equations). Horizontal: 2×2 system. Vertical: 3×3 system. Both solvable analytically via Cramer's rule.

**Minimum requirement**: 3 detected positions per trajectory segment.

**Rationale**: Decoupling x and y simplifies math, avoids matrix operations larger than 3×3, and is physically motivated (gravity acts only vertically). Air resistance over short segments (0.5-1.5s flight) introduces deviation well under 1cm.

**Alternatives considered**:
- Full 3D reconstruction: Requires stereo cameras or camera calibration. Spec uses single phone camera. Rejected.
- Spline fitting: Overfits on sparse data (3-8 points). Parabolic model provides physics-based regularization.
- Kalman filter: Optimal for online real-time filtering; our use case is post-hoc batch reconstruction where batch least-squares is statistically more efficient. Reserved as future enhancement for real-time overlay.

## R8: Gap Filling Strategy

**Decision**: Evaluate the fitted parabolic model at missing frame times. No separate interpolation algorithm needed.

**Rationale**: Once the parabola is fit to detected points in a segment, evaluating at any frame time produces a physically plausible position. For offline batch processing, this is more accurate than sequential Kalman filtering.

**Limitations**: Gaps exceeding 5 frames (~170ms at 30fps) should be marked low-confidence. Beyond that, the ball likely left the frame or a contact event was missed.

**Alternatives considered**:
- Kalman filter: More complex, no accuracy advantage for offline use. Rejected for MVP.
- Cubic spline: Overfits sparse data, non-physical oscillations. Rejected.
- Linear interpolation: Too simplistic, misses parabolic arc. Rejected.

## R9: Bounce and Contact Detection

**Decision**: Three-signal detector operating on consecutive detection triplets.

**Signals** (ranked by reliability):
1. **Vertical velocity reversal** (bounce): `sign(vy_before) ≠ sign(vy_after)` with minimum magnitude threshold. Validated by proximity to table surface y-coordinate.
2. **Velocity magnitude spike** (paddle contact): `speed_after / speed_before > 1.8` or inverse. Detects acceleration from paddle strikes.
3. **Direction angle discontinuity** (general contact): Angle between incoming and outgoing velocity vectors > 30-45°. Catches net clips and glancing contacts.

**Contact classification**: BOUNCE, PADDLE_CONTACT, NET_CLIP, or UNKNOWN_CONTACT based on which signals triggered.

**Minimum detection window**: 3 consecutive detected positions required.

**Alternatives considered**:
- Residual-based detection (fit parabola to full sequence, find high-residual points): Works but is retrospective, doesn't classify type. Used as secondary validation.
- ML classifier: Requires training data. Rejected for MVP.

## R10: Segment Splitting Pipeline

**Decision**: Detect-then-split-then-fit pipeline with recursive refinement.

**Steps**:
1. Collect all BallDetection points for a rally, time-ordered.
2. Pre-filter outliers (confidence below threshold, spatially impossible positions).
3. Detect contacts using three-signal detector.
4. Split at contacts (contact point belongs to both adjacent segments for continuity).
5. Fit each segment independently with parabolic least-squares.
6. Validate fit quality (RMS residual). If high, attempt sub-splitting at max-residual point.
7. Fill gaps by evaluating parabola at all frame times.

**Short segment handling**: 2 points → linear fit (cy=0). 1 point → isolated detection. 0 points → "ball not tracked."

## R11: Accuracy Assessment — <3cm Target

**Decision**: Achievable for typical rallies with moderate spin. Heavy-spin shots are the main limitation.

**Analysis**: At 1080p, 3cm ≈ 8.3 pixels (with table filling ~70% of frame width). The ball itself is ~11px diameter, so 3cm error ≈ 0.75 ball-diameters — the predicted position would visually overlap the actual ball.

**Favorable factors**: Parabolic model is correct for free flight. Least-squares across 4-8 detections averages out detection jitter. Fitting acts as noise filter.

**Limiting factors**: Heavy topspin/backspin (Magnus effect) can cause 5-8cm deviation per segment. Camera angle perpendicular to table long axis produces best results.

**Conclusion**: Average across all segments in a session should be <3cm since most include serves, blocks, and pushes with less spin. Per-segment fit quality should be reported so UI can flag low-confidence reconstructions.

## R12: Camera Exposure Optimization

**Decision**: Use Camera2 interop APIs to disable auto-exposure and manually control exposure time and ISO. Default 2ms exposure, 800 ISO. Hard ceiling 8ms exposure.

**APIs**:
- `Camera2Interop.Extender` at build time for initial settings.
- `Camera2CameraControl` at runtime for mid-session adaptation.
- Both available via already-included `camera-camera2:1.5.3` dependency.

**Exposure targets**:
| Lighting | Exposure | ISO |
|----------|----------|-----|
| Bright gym (500+ lux) | 1-2ms | 400-800 |
| Average indoor (200-500 lux) | 2-4ms | 800-1600 |
| Dim basement (<200 lux) | 4-8ms | 1600-3200 |

**Adaptation strategy**: Hybrid manual exposure with brightness-based EMA sampling every ~30 frames (~1s). Adjust ISO first (up to 3200), then relax exposure (up to 8ms). Rate-limited to max 1 adjustment per 2 seconds. Applied via `Camera2CameraControl.addCaptureRequestOptions()` (no camera restart needed).

**Fallback**: If device doesn't support `CONTROL_AE_MODE_OFF`, keep AE on with `CONTROL_AE_TARGET_FPS_RANGE = Range(30, 30)` plus negative exposure compensation index.

**Alternatives considered**:
- CameraX exposure compensation only: Cannot set exposure ceiling; AE may choose 16ms+ in dim conditions. Rejected.
- Fully manual without adaptation: Would fail when lighting changes. Rejected.

## R13: Frame Rate

**Decision**: 30 FPS target, matching existing pipeline.

**Rationale**: At 30 FPS, the 33ms frame budget is already tight with pose + ball detection combined (~40-50ms). 60 FPS would cause most frames to drop. The existing throttle at 15 FPS actual processing rate provides sufficient temporal resolution for trajectory reconstruction.

**Alternatives considered**:
- 60 FPS: Processing budget exceeded. Would require parallel GPU pipelines. Deferred.

## R14: White Balance

**Decision**: Keep AWB AUTO. No manual override.

**Rationale**: Android's AWB is generally good. Locking to specific WB would fail across different lighting types. Instead, make ball detection robust with wide HSV ranges. `CONTROL_AWB_MODE` operates independently of `CONTROL_AE_MODE`.

## R15: Timeline Synchronization

**Decision**: Use camera frame timestamp (`ImageProxy.imageInfo.timestamp`) as single source of truth. Both detectors process the same ImageProxy sequentially on the background executor.

**Rationale**: Both detectors receive the same physical frame, so timestamps are identical by definition. No clock drift, no synchronization buffer needed for live mode. Sequential processing fits within the 66ms budget (15 FPS throttle).

**Interpolation**: Linear interpolation for 1-frame sync gaps (sufficient accuracy for 33ms intervals). Parabolic model used only for trajectory reconstruction across longer gaps.

**Data structure**: `SynchronizedFrame` with nullable `pose: PoseFrame?` and `ball: BallDetection?` fields plus `DataSource` enum (DETECTED, INTERPOLATED, ABSENT) for provenance tracking.

**Merge approach**:
- Real-time: Ring buffer with timeout-based emission (100ms max wait).
- Post-hoc: Map-join on timestamps.

## R16: Pure Kotlin Feasibility for Shared Module

**Decision**: All trajectory math and synchronization logic implementable in pure Kotlin for KMP `commonMain`. No external math libraries needed.

**Operations required**: Linear regression (2×2), quadratic regression (3×3 Cramer's rule), finite differences, vector dot product/magnitude/angle, RMS error. All use only `kotlin.math.*` which is available in commonMain.

**Estimated size**: ~200-300 lines across `TrajectoryFilter.kt`, `TrajectorySegmenter.kt`, `TimelineSynchronizer.kt`.

**Note**: Existing `AngleCalculations.kt` uses `java.lang.Math.toDegrees` and `java.lang.Math.acos` which are JVM-specific. New trajectory code must use `kotlin.math` equivalents for true multiplatform compatibility.

**Alternatives considered**:
- Apache Commons Math: JVM-only, breaks KMP commonMain. Rejected.
- KMath/Multik: Heavy dependency for trivial 3×3 linear algebra. Rejected.
