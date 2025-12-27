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

### Files Exceeding Limit (Needs Refactoring)
- ❌ `GalleryFragment.kt` - 400 lines
- ❌ `CameraFragment.kt` - 361 lines
- ❌ `PoseLandmarkerHelper.kt` - 342 lines
- ❌ `SettingsActivity.kt` - 253 lines

### Files Within Limit (Good)
- ✅ `TrainingActivity.kt` - 215 lines
- ✅ `VideoPlayerManager.kt` - 150 lines
- ✅ `TrainingStateManager.kt` - 88 lines
- ✅ `TrainingUIController.kt` - 109 lines

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

The following files should be prioritized for refactoring:
1. `GalleryFragment.kt` - split into GalleryFragment + GalleryVideoProcessor + GalleryUIController
2. `CameraFragment.kt` - split into CameraFragment + CameraManager + CameraUIController  
3. `PoseLandmarkerHelper.kt` - split into PoseLandmarkerHelper + PoseLandmarkerConfig + ResultProcessor
4. `SettingsActivity.kt` - already close to limit, monitor for future changes
