# Quickstart: Ball Tracking and Trajectory Prediction

**Feature**: 002-ball-tracking | **Date**: 2026-02-24

## Prerequisites

- Android Studio with Kotlin 2.1.0
- Existing project with `:shared` KMP module and `:app` Android module (from Phase 1)
- CameraX 1.5.3 already configured
- MediaPipe pose detection already functional

## 1. Add OpenCV Dependency

In `app/build.gradle`:
```groovy
dependencies {
    implementation 'org.opencv:opencv:4.9.0'
}

android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a'
        }
    }
}
```

## 2. Add Shared Models

Create ball tracking models in `shared/src/commonMain/kotlin/com/ttcoachai/shared/models/`:

```kotlin
// BallDetection.kt
data class BallDetection(
    val x: Float,
    val y: Float,
    val confidence: Float = 0f,
    val radiusPx: Float = 0f,
    val frameIndex: Int,
    val timestampMs: Long,
    val status: BallDetectionStatus = BallDetectionStatus.DETECTED
)

enum class BallDetectionStatus { DETECTED, NOT_DETECTED, OUT_OF_FRAME }
```

## 3. Implement Ball Detection (Android)

Create `app/src/main/java/com/ttcoachai/tracking/BallDetector.kt`:

```kotlin
class BallDetector(
    private val ballColor: BallColor = BallColor.WHITE,
    private val expectedRadiusRange: IntRange = 4..25
) {
    private val hsvMat = Mat()
    private val colorMask = Mat()
    private val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))

    fun detect(bitmap: Bitmap, roi: RegionOfInterest, frameIndex: Int, timestampMs: Long): BallDetection {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val rgbMat = Mat()
        Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_RGBA2RGB)

        // Crop ROI (zero-copy)
        val roiRect = Rect(roi.x, roi.y, roi.width, roi.height)
        val roiMat = Mat(rgbMat, roiRect)

        // Color conversion and thresholding
        Imgproc.cvtColor(roiMat, hsvMat, Imgproc.COLOR_RGB2HSV)
        // ... threshold, morphology, findContours, filter by area/circularity ...

        mat.release()
        rgbMat.release()
        // Return BallDetection with normalized coordinates
    }

    fun release() { hsvMat.release(); colorMask.release() }
}
```

## 4. Implement Trajectory Filter (Shared)

Create `shared/src/commonMain/kotlin/com/ttcoachai/shared/tracking/TrajectoryFilter.kt`:

```kotlin
object TrajectoryFilter {
    fun fit(detections: List<BallDetection>): ParabolicFit? {
        if (detections.size < 2) return null
        val times = detections.map { it.timestampMs.toDouble() }.toDoubleArray()
        val xs = detections.map { it.x.toDouble() }.toDoubleArray()
        val ys = detections.map { it.y.toDouble() }.toDoubleArray()

        val (ax, bx) = fitLinear(times, xs)
        val (ay, by, cy) = if (detections.size >= 3) fitQuadratic(times, ys)
                           else Triple(/* linear */ fitLinear(times, ys).let { Triple(it.first, it.second, 0.0) })
        return ParabolicFit(ax, bx, ay, by, cy)
    }
    // ... fitLinear, fitQuadratic using normal equations ...
}
```

## 5. Integrate with Camera Pipeline

In `PoseLandmarkerProcessor.detectLiveStream()`:

```kotlin
// After bitmap creation and rotation (existing code):
val ballDetection = ballDetector?.detect(tempBitmap, roi, frameIndex, frameTime)

// Existing pose detection:
val mpImage = BitmapImageBuilder(tempBitmap).build()
poseLandmarker.detectAsync(mpImage, frameTime)

// Notify listener with both results
ballTrackingListener?.onBallDetected(ballDetection, frameTime)
```

## 6. Apply Camera Optimization

In `CameraManager.kt`, before `bindToLifecycle`:

```kotlin
val previewBuilder = Preview.Builder()
Camera2Interop.Extender(previewBuilder)
    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
    .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 2_000_000L)
    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 800)
```

## 7. Run Tests

```bash
# Shared module tests (JVM, no Android required)
./gradlew :shared:jvmTest

# Android unit tests
./gradlew :app:testDebugUnitTest

# Full build
./gradlew assembleDebug
```

## Key Architecture Decisions

- **Shared KMP module** (`commonMain`): All trajectory math, segmentation, synchronization — pure Kotlin, no external dependencies
- **Android module** (`app`): Ball detection (OpenCV), camera optimization (CameraX/Camera2), ROI management — platform-specific APIs
- **Sequential processing**: Ball detection + pose detection run sequentially on the same frame within the 66ms budget (15 FPS)
- **Timestamp alignment**: Both detectors use `ImageProxy.imageInfo.timestamp` — identical by definition
