# AI Coach MVP 2.0 - Android Implementation Plan

## Overview

Build an Android native app for table tennis coaching that analyzes Forehand Drive technique with real-time pose tracking, ball detection, and instant voice feedback (<200ms latency). Includes asynchronous LLM analysis for training reports.

## Architecture Overview

The app consists of four main modules working in parallel:

```
Camera Feed → Computer Vision Module
    ├── MediaPipe Pose (33 Key Points)
    └── YOLO Nano (Ball Detection)
        └── Kalman Filter (Trajectory Tracking)
            └── Heuristic Engine (Real-time Analysis)
                ├── Native TTS (Voice Feedback <200ms)
                └── Statistics Collector
                    └── OpenAI API (Async Reports)
                        └── Training Report
```

## Project Structure

```
app/src/main/java/com/ttcoach/
├── MainActivity.kt              # Main entry point
├── camera/                      # Camera2 API integration
│   └── CameraManager.kt          # Camera setup and frame capture
├── cv/                          # Computer vision modules
│   ├── MediaPipePoseProcessor.kt    # Pose estimation (33 key points)
│   ├── YOLOBallDetector.kt          # Ball detection using TensorFlow Lite
│   └── KalmanTracker.kt            # Ball tracking with Kalman filter
├── analysis/                    # Analysis engine
│   ├── HeuristicEngine.kt           # Real-time rule-based analysis
│   ├── CalibrationManager.kt        # Table calibration (4 corners)
│   ├── HitDetector.kt              # Strike detection
│   └── TechniqueAnalyzer.kt        # Angle calculations
├── ball/                        # Ball analysis module
│   ├── BallTracker.kt               # Main ball tracking coordinator
│   ├── InOutDetector.kt             # Table boundary checking
│   ├── SpeedCalculator.kt           # Velocity computation (m/s)
│   ├── SpinEstimator.kt             # Basic spin analysis (top/back)
│   └── TrajectoryAnalyzer.kt        # Flight path analysis
├── audio/                       # Audio feedback
│   ├── TTSManager.kt                # Android TextToSpeech wrapper
│   ├── AudioAssets.kt               # Pre-recorded MP3 files manager
│   └── FeedbackQueue.kt             # Queue management for <200ms feedback
├── llm/                         # LLM integration
│   ├── OpenAIService.kt             # Retrofit API client for OpenAI
│   ├── ConfigGenerator.kt          # Exercise config generation
│   └── ReportGenerator.kt          # Training report generation
├── ui/                          # UI components (Jetpack Compose)
│   ├── CalibrationScreen.kt         # Table calibration (4 corners)
│   ├── TrainingScreen.kt            # Main training view with camera preview
│   └── ReportScreen.kt              # Training reports and statistics
└── data/                        # Data models and storage
    ├── models/                      # Data classes
    └── database/                    # Room database
```

## Implementation Phases

### Week 1: Skeleton Tracking & Rules ✅ IN PROGRESS

**Tasks:**
- ✅ Set up Android project with Gradle configuration
- ✅ Add MediaPipe Pose SDK dependency
- ✅ Configure Camera2 API permissions
- ✅ Set up Kotlin Coroutines and Flow
- ⏳ Implement Camera2 API integration (CameraManager.kt)
- ⏳ Integrate MediaPipe Pose SDK (MediaPipePoseProcessor.kt)
- ⏳ Create TechniqueAnalyzer.kt for angle calculations
- ⏳ Implement HitDetector.kt using wrist velocity
- ⏳ Basic UI setup with camera preview

**Deliverables:**
- Real-time pose tracking at 60 FPS
- Angle calculations (elbow, shoulder, body rotation)
- Hit detection (start/end of stroke)
- Basic camera preview UI

### Week 2: Ball Detection & Trajectory

**Tasks:**
- Integrate YOLO Nano model (TensorFlow Lite)
- Implement YOLOBallDetector.kt for ball detection
- Create KalmanTracker.kt for stable ball tracking
- Implement CalibrationManager.kt - 4-corner table calibration
- Create InOutDetector.kt for table boundary checking
- Implement bounce point detection

**Deliverables:**
- Stable ball tracking with Kalman filter
- Table calibration UI and persistence
- In/Out detection with real-time feedback
- Bounce point detection

### Week 3: Metrics (Speed & Spin)

**Tasks:**
- Implement SpeedCalculator.kt - velocity computation using calibrated scale
- Create SpinEstimator.kt - basic top/back spin estimation
- Implement TrajectoryAnalyzer.kt - flight path analysis
- Integrate all metrics into BallTracker.kt coordinator

**Deliverables:**
- Ball speed calculation (m/s) with calibrated accuracy
- Basic spin estimation (top/back)
- Trajectory deviation analysis
- Unified ball analysis interface

### Week 4: Integration (UI, TTS & LLM Summary)

**Tasks:**
- Implement TTSManager.kt using Android TextToSpeech API
- Create pre-recorded audio assets (20 MP3 files)
- Integrate HeuristicEngine.kt with TTS for <200ms feedback
- Implement OpenAIService.kt for OpenAI API integration
- Create ConfigGenerator.kt - LLM converts user requests to JSON config
- Implement ReportGenerator.kt - LLM analyzes training statistics
- Build complete UI with training view, calibration, and reports
- Performance optimization and latency testing

**Deliverables:**
- Complete UI with all features
- Real-time voice feedback <200ms
- LLM-powered exercise configuration
- Training reports after 50+ strokes
- Performance metrics meeting latency requirements

## Key Technical Decisions

1. **MediaPipe Pose**: Use official Android SDK from Google
2. **YOLO Nano**: Convert to TensorFlow Lite for Android deployment
3. **Kalman Filter**: Custom Kotlin implementation for ball tracking stability
4. **TTS**: Android TextToSpeech API with pre-initialization for minimal latency
5. **Audio**: Pre-recorded MP3 files for critical feedback (<50ms playback)
6. **LLM**: OpenAI API with Retrofit, async processing (not in real-time loop)
7. **Storage**: Room database for training history and statistics
8. **UI**: Jetpack Compose for modern, declarative UI
9. **Calibration**: Persist 4-corner coordinates in Room database
10. **Threading**: Use Kotlin Coroutines and Flow for async operations

## Data Models

**Stroke Data:**
```kotlin
data class Stroke(
    val timestamp: Long,
    val pose: PoseAnalysis,
    val ball: BallAnalysis?,
    val hitDetected: Boolean,
    val techniqueScore: Float
)

data class PoseAnalysis(
    val elbowAngle: Float,
    val shoulderAngle: Float,
    val bodyRotation: Float,
    val keyPoints: List<KeyPoint> // 33 MediaPipe points
)

data class BallAnalysis(
    val inOut: Boolean,
    val speed: Float, // m/s
    val spin: SpinType, // TOP, BACK, NONE
    val trajectoryDeviation: Float,
    val bouncePoint: PointF?
)
```

**Training Session:**
```kotlin
data class TrainingSession(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val strokes: List<Stroke>,
    val summary: SessionSummary
)

data class SessionSummary(
    val totalStrokes: Int,
    val inCount: Int,
    val avgSpeed: Float,
    val techniqueAvg: Float,
    val spinDistribution: Map<SpinType, Int>
)
```

## Dependencies

**Android:**
- MediaPipe Tasks Vision: `com.google.mediapipe:tasks-vision:0.10.7`
- TensorFlow Lite: `org.tensorflow:tensorflow-lite:2.14.0`
- CameraX: `androidx.camera:camera-camera2:1.3.0`
- Room Database: `androidx.room:room-runtime:2.6.0`
- Retrofit: `com.squareup.retrofit2:retrofit:2.9.0`
- Coroutines: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- Jetpack Compose: `androidx.compose.ui:ui`

## Testing Strategy

- **Unit Tests**: Heuristic engine, angle calculations, Kalman filter, speed/spin estimators
- **Integration Tests**: CV pipeline (pose + ball detection), feedback system
- **Performance Tests**: Latency measurement (<200ms requirement), FPS monitoring
- **UI Tests**: Calibration flow, training screen interactions
- **Device Testing**: Test on multiple Android devices (different performance levels)

## Configuration Files

- `app/src/main/assets/pose_landmarker_lite.task` - MediaPipe Pose model (download required)
- `app/src/main/assets/yolo_nano.tflite` - YOLO Nano model (Week 2)
- `app/src/main/res/raw/` - Pre-recorded audio files (20 MP3 files, Week 4)
- `app/src/main/res/values/config.xml` - App configuration (API keys, thresholds)
- `app/src/main/res/values/audio_phrases.xml` - Mapping of feedback phrases to audio files
- `app/src/main/res/values/heuristic_rules.xml` - Rule-based analysis thresholds

## Current Status

### ✅ Completed (Week 1 - Step 1)
- [x] Android project structure with Gradle
- [x] MediaPipe Pose SDK dependency added
- [x] Camera2 API permissions configured
- [x] Kotlin Coroutines and Flow set up

### ⏳ In Progress (Week 1)
- [ ] Camera integration (CameraManager.kt)
- [ ] MediaPipe Pose processor (MediaPipePoseProcessor.kt)
- [ ] Technique analyzer
- [ ] Hit detector
- [ ] Basic UI

### 📋 Pending
- Week 2: Ball detection and trajectory
- Week 3: Metrics (speed & spin)
- Week 4: Integration (UI, TTS & LLM)

## Notes

- MediaPipe model file (`pose_landmarker_lite.task`) must be downloaded and placed in `app/src/main/assets/`
- API keys for OpenAI should be stored securely (BuildConfig or secure storage)
- Performance optimization is critical for <200ms feedback requirement
- Testing on real devices is essential for accurate performance metrics

