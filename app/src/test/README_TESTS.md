# Test Suite for TT Coach AI

This directory contains tests to ensure that modifications to different parts of the app don't break existing functionality.

## Test Structure

### Unit Tests (`src/test/`)
Unit tests run on the JVM and don't require an Android device or emulator.

- **TrainingActivityTest.kt** - Tests camera/video mode switching logic
  - Verifies video container visibility in video mode
  - Verifies camera container visibility in camera mode
  - Tests switching between modes
  - Ensures fragment transactions work correctly

- **TrainingStateManagerTest.kt** - Tests training state management
  - Initial state validation
  - Start/stop training functionality
  - Calibration state management
  - Stroke counting and scoring
  - Reset functionality

- **GalleryFragmentTest.kt** - Tests gallery loading doesn't break camera
  - Gallery initialization isolation
  - Camera configuration preservation
  - Resource management

### Integration Tests (`src/androidTest/`)
Integration tests run on an Android device or emulator and test the full app flow.

- **CameraVideoModeIntegrationTest.kt** - End-to-end tests for camera/video modes
  - Camera mode displays camera container
  - Video mode displays video container
  - Switching between modes works correctly
  - Controls are visible in both modes

## Running Tests

### Run All Unit Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests TrainingActivityTest
./gradlew test --tests TrainingStateManagerTest
```

### Run All Integration Tests (requires connected device/emulator)
```bash
./gradlew connectedAndroidTest
```

### Run Specific Integration Test
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ttcoachai.CameraVideoModeIntegrationTest
```

### Run Tests from Android Studio
1. Right-click on test file or test method
2. Select "Run [TestName]"

## Test Coverage

The test suite ensures:

✅ **Camera Mode Isolation**
- Camera functionality works independently
- Video changes don't affect camera
- Proper container visibility management

✅ **Video Mode Isolation**
- Video playback works correctly
- Gallery loading doesn't break camera
- Proper resource cleanup

✅ **Mode Switching**
- Smooth transitions between modes
- No resource leaks
- State preserved correctly

✅ **State Management**
- Training state tracked correctly
- Calibration works properly
- Stroke analysis data accurate

## Adding New Tests

When adding new features:

1. **Add unit tests** for business logic
2. **Add integration tests** for UI flows
3. **Test both modes** to ensure isolation
4. **Verify cleanup** to prevent resource leaks

Example test structure:
```kotlin
@Test
fun `test feature doesn't break existing functionality`() {
    // Given - setup initial state
    
    // When - perform action
    
    // Then - verify behavior
}
```

## Dependencies

The test suite uses:
- **JUnit 4** - Test framework
- **Mockito** - Mocking library
- **Robolectric** - Android framework for unit tests
- **Espresso** - UI testing for integration tests
- **AndroidX Test** - Testing utilities

## Continuous Testing

Run tests before committing:
```bash
./gradlew test && ./gradlew connectedAndroidTest
```

Or add to git pre-commit hook:
```bash
#!/bin/sh
./gradlew test || exit 1
```
