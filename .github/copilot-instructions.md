# AI Coach for Table Tennis - Copilot Instructions

## Project Overview
This is an Android app for analyzing table tennis technique using MediaPipe Pose Landmarker. Built on the MediaPipe Android example, it adds rule-based motion analysis for real-time coaching feedback. The app is in Ukrainian (project language), targeting table tennis players learning proper stroke technique.

## Architecture & Key Patterns

### Activity Structure
- **Activity Flow**: `WelcomeActivity` → `ExerciseSelectionActivity` → `TrainingActivity`
- All activities extend `BaseActivity` which handles locale management via `LocaleHelper`
- Navigation uses explicit intents with extras (e.g., `EXERCISE_ID`, `EXERCISE_NAME`)
- Legacy `MainActivity` and `CameraActivity` exist from MediaPipe example but are not part of main flow

### Manager Pattern
The codebase uses specialized manager classes to separate concerns:
- **State Management**: `TrainingStateManager` - tracks training session state, feedback history, consecutive good strokes
- **UI Control**: `*UIController` classes (`TrainingUIController`, `CameraUIController`, `SettingsUIController`) - handle UI updates and interactions
- **Settings**: `SettingsManager` - handles SharedPreferences for exercise parameters
- **Media Processing**: `GalleryMediaProcessor`, `VideoPlayerManager`, `CameraManager` - handle different input sources

Each activity typically initializes multiple managers in `onCreate()` via `initializeManagers()`.

### MediaPipe Integration
- **PoseLandmarkerHelper**: Main interface to MediaPipe, configured via `PoseLandmarkerConfig`
- **RunningMode**: Three modes - `IMAGE`, `VIDEO`, `LIVE_STREAM` (camera uses LIVE_STREAM)
- **Models**: Three variants downloaded by Gradle - `pose_landmarker_lite`, `pose_landmarker_full`, `pose_landmarker_heavy`
- **Configuration**: Detection/tracking/presence confidence thresholds, delegate selection (CPU/GPU/NNAPI)
- **Lifecycle**: Create helper in background thread, clear in `onPause()`, recreate in `onResume()`

### Motion Analysis Architecture
Custom analysis pipeline for table tennis technique:
1. **PoseLandmarkerResult** → `MotionAnalyzer.analyzeStroke()`
2. Returns `AnalysisResult` with errors, recommendations, scores, detected parameters
3. **FeedbackGenerator** converts `AnalysisResult` to user-facing feedback strings
4. **StrokePhase** enum tracks stroke phases: `PREPARATION`, `BACKSWING`, `FORWARD`, `CONTACT`, `FOLLOW_THROUGH`

**Key Analysis Parameters** (defined in `ExerciseParameters`):
- `idealWristAngle`, `minBodyRotation`, `followThroughAngle` - angle validation
- `contactHeightMin/Max` - height relative to floor (0.7-1.1)
- `maxElbowBodyDistance` - elbow position constraint (0.3m)
- All have tolerance ranges for validation (`isWristAngleValid()`, etc.)

### Data Models
- **Exercise**: UI model (id, name, description, difficulty, duration)
- **ExerciseParameters**: Technical parameters for analysis, factory method pattern (`forehandDrive()`, `backspin()`)
- **AnalysisResult**: Analysis output (errors list, recommendations, overallScore, phase, detected angles)
- **TechniqueErrors/TechniqueRecommendations**: Categorized feedback enums

## Build & Development Workflow

### Gradle Model Download
**IMPORTANT**: MediaPipe models are auto-downloaded by `download_tasks.gradle` during preBuild:
```gradle
task downloadTaskFile(type: Download) {
    src 'https://storage.googleapis.com/mediapipe-models/...'
    dest project.ext.ASSET_DIR + '/pose_landmarker_heavy.task'
}
preBuild.dependsOn downloadTaskFile, downloadTaskFile1, downloadTaskFile2
```
No manual model management needed. Models go to `app/src/main/assets/`.

### Build Commands
```powershell
# Build APK
.\gradlew assembleDebug

# Install on device
.\gradlew installDebug

# View device logs (task available)
$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe logcat -d | Select-String -Pattern 'AndroidRuntime|FATAL|Exception' -Context 5
```

### Testing
- Unit tests in `app/src/test/java/` - example: `TrainingStateManagerTest`
- Test managers and models independently without Android dependencies
- No instrumented tests for MediaPipe integration yet

### ViewBinding Pattern
All activities and fragments use ViewBinding (enabled in `app/build.gradle`):
```kotlin
private lateinit var binding: ActivityTrainingBinding
binding = ActivityTrainingBinding.inflate(layoutInflater)
setContentView(binding.root)
binding.someButton.setOnClickListener { ... }
```

## Project-Specific Conventions

### Package Organization
```
com.ttcoachai/
├── [Activities]      # WelcomeActivity, TrainingActivity, etc.
├── managers/         # State, UI, Settings managers
├── services/         # MotionAnalyzer, FeedbackGenerator
├── models/          # Data classes, parameters
├── helpers/         # PoseLandmarkerConfig, PoseLandmarkerProcessor
└── fragment/        # Legacy: CameraFragment, GalleryFragment
```

### Locale Handling
- App displays in Ukrainian, but code comments can be Ukrainian or English
- `LocaleHelper.applyLocale()` applied in `BaseActivity.attachBaseContext()` and `onCreate()`
- Strings must go in `res/values/strings.xml` (currently no Ukrainian resources defined)

### Settings Storage
- `SettingsManager` uses SharedPreferences with key prefix `PREF_`
- Exercise parameters stored per-exercise: `PREF_FOREHAND_WRIST_ANGLE`, etc.
- Audio settings: `PREF_AUDIO_ENABLED`, `PREF_AUDIO_VOLUME`, `PREF_AUDIO_SPEED`
- Camera settings: `PREF_CAMERA_RESOLUTION`

### Background Execution
MediaPipe operations run on background executor:
```kotlin
backgroundExecutor = Executors.newSingleThreadExecutor()
backgroundExecutor.execute {
    poseLandmarkerHelper = PoseLandmarkerHelper(...)
}
// MUST shutdown in onDestroyView/onDestroy
backgroundExecutor.shutdown()
backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
```

## Critical Developer Knowledge

### Adding New Exercises
1. Create `ExerciseParameters` factory method (see `forehandDrive()` example)
2. Add to `ExerciseSelectionActivity.loadExercises()`
3. Implement angle calculations in `MotionAnalyzer` if needed
4. Add feedback templates in `FeedbackGenerator`

### MediaPipe Landmarks Usage
Landmarks are accessed by index:
```kotlin
val landmarks = poseLandmarkerResult.landmarks()[0]
val leftShoulder = landmarks[11]  // PoseLandmark.LEFT_SHOULDER
val rightShoulder = landmarks[12]
val rightElbow = landmarks[14]
val rightWrist = landmarks[16]
```
Calculate angles using `calculateAngle()` helper with landmark coordinates.

### Training Session Flow
1. User selects exercise → `TrainingActivity` launched with extras
2. Initialize `ExerciseParameters`, `MotionAnalyzer`, `FeedbackGenerator`
3. Start training → `stateManager.startTraining()`
4. On each frame: analyze pose → generate feedback → update UI → add to history
5. Stop training → display summary statistics

### Common Pitfalls
- **Lifecycle**: Always clear `PoseLandmarkerHelper` in `onPause()` to avoid resource leaks
- **Threading**: UI updates must be on main thread (`runOnUiThread { ... }`)
- **Nullable Results**: `PoseLandmarkerResult` can be null or have empty landmarks - always check
- **Model Files**: Don't commit `.task` files to git (large binaries, auto-downloaded)

## Development References
- [MVP Progress Tracker](../docs/MVP_PROGRESS_TRACKER.md) - Feature implementation status (Ukrainian)
- [Navigation Implementation](../docs/NAVIGATION_IMPLEMENTATION.md) - Screen flow details (Ukrainian)
- MediaPipe Pose Landmarker: Uses 33 body landmarks, outputs 3D coordinates and visibility scores
- Min SDK 24 (Android 7.0), Target SDK 34 (Android 14)

## Adding Features
When adding new motion analysis:
1. Add parameters to `ExerciseParameters` with validation methods
2. Implement angle/distance calculation in `MotionAnalyzer`
3. Add error/recommendation enums in models
4. Update `FeedbackGenerator` for user-facing messages
5. Add unit tests in `TrainingStateManagerTest` pattern
6. Update `SettingsManager` if parameters are user-configurable

## After each chat change request
1. Build the project to ensure no errors.
2. Run unit tests to verify functionality.
3. Commit changes with a descriptive message.
