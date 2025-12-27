# Coding Guidelines for TT Coach AI

## File Size Limits

### Kotlin Files
**Maximum Lines: 250**

No Kotlin file should exceed 250 lines of code. If a file grows beyond this limit, it must be refactored into smaller, focused classes.

### Why 250 lines?
- Easier to understand and maintain
- Better testability
- Single Responsibility Principle
- Reduces cognitive load
- Easier code reviews

### How to Split Large Files

When a file exceeds 250 lines, consider these strategies:

#### 1. **Extract Manager Classes**
Move related functionality into dedicated manager classes.

**Example:**
```kotlin
// Before: TrainingActivity.kt (464 lines)
class TrainingActivity {
    // UI logic
    // State management
    // Video playback
    // Analytics
}

// After: Split into multiple files (each < 250 lines)
- TrainingActivity.kt (215 lines) - coordinates managers
- VideoPlayerManager.kt (150 lines) - video playback
- TrainingStateManager.kt (88 lines) - state management  
- TrainingUIController.kt (109 lines) - UI updates
```

#### 2. **Extract Helper Classes**
Move utility functions and data processing to helper classes.

#### 3. **Create Service Classes**
Business logic should be in separate service classes.

#### 4. **Use Extension Functions**
Group related extensions in separate files.

## Current Status

### Files Within Limit (Good ✅)
- ✅ `TrainingActivity.kt` - 215 lines (refactored)
- ✅ `SettingsActivity.kt` - 135 lines (refactored from 281)
- ✅ `PoseLandmarkerHelper.kt` - 165 lines (refactored from 391)
- ✅ `VideoPlayerManager.kt` - 150 lines
- ✅ `TrainingStateManager.kt` - 88 lines
- ✅ `TrainingUIController.kt` - 109 lines
- ✅ `SettingsManager.kt` - 79 lines
- ✅ `SettingsUIController.kt` - 148 lines
- ✅ `PoseLandmarkerConfig.kt` - 89 lines
- ✅ `PoseLandmarkerProcessor.kt` - 139 lines

### Files Exceeding Limit (Needs Refactoring ❌)
- ❌ `GalleryFragment.kt` - 400 lines (TODO)
- ❌ `CameraFragment.kt` - 361 lines (TODO)

## Enforcement

1. **Before committing**: Check file sizes
   ```powershell
   Get-ChildItem -Path "app\src\main\**\*.kt" -Recurse | 
   ForEach-Object { 
       $lines = (Get-Content $_.FullName | Measure-Object -Line).Lines
       if ($lines -gt 250) {
           Write-Host "⚠️  $($_.Name): $lines lines (exceeds 250)" -ForegroundColor Yellow
       }
   }
   ```

2. **During code review**: Flag files exceeding 250 lines

3. **Gradual improvement**: Refactor large files as part of feature work

## Next Steps

### Completed Refactorings
1. ✅ `TrainingActivity.kt` - Split into TrainingActivity + 3 managers
2. ✅ `SettingsActivity.kt` - Split into SettingsActivity + SettingsManager + SettingsUIController  
3. ✅ `PoseLandmarkerHelper.kt` - Split into PoseLandmarkerHelper + Config + Processor

### Remaining Files to Refactor
The following files should be prioritized for refactoring:
1. `GalleryFragment.kt` (400 lines) - split into:
   - GalleryFragment (< 250 lines) - main coordinator
   - GalleryVideoProcessor (< 250 lines) - video processing logic
   - GalleryUIController (< 250 lines) - UI management

2. `CameraFragment.kt` (361 lines) - split into:
   - CameraFragment (< 250 lines) - main coordinator
   - CameraManager (< 250 lines) - camera setup and control
   - CameraUIController (< 250 lines) - UI updates and controls
