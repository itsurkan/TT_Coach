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
- ✅ `TrainingActivity.kt` - 224 lines (refactored from 309 using PoseAnalysisProcessor)
- ✅ `PoseAnalysisProcessor.kt` - 158 lines (extracted from TrainingActivity)
- ✅ `SettingsActivity.kt` - 135 lines (refactored from 281)
- ✅ `PoseLandmarkerHelper.kt` - 165 lines (refactored from 391)
- ✅ `GalleryFragment.kt` - 241 lines (refactored from 455)
- ✅ `CameraFragment.kt` - 232 lines (refactored from 412)
- ✅ `VideoPlayerManager.kt` - 150 lines
- ✅ `TrainingStateManager.kt` - 88 lines
- ✅ `TrainingUIController.kt` - 109 lines
- ✅ `SettingsManager.kt` - 79 lines
- ✅ `SettingsUIController.kt` - 148 lines
- ✅ `PoseLandmarkerConfig.kt` - 89 lines
- ✅ `PoseLandmarkerProcessor.kt` - 139 lines
- ✅ `GalleryUIController.kt` - 236 lines
- ✅ `GalleryMediaProcessor.kt` - 175 lines
- ✅ `CameraUIController.kt` - 225 lines
- ✅ `CameraManager.kt` - 136 lines

### Files Exceeding Limit (Needs Refactoring ❌)
**All files now comply with the 250-line limit! 🎉**

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
1. ✅ `TrainingActivity.kt` (464 → 215 lines) - Split into TrainingActivity + 3 managers
2. ✅ `SettingsActivity.kt` (281 → 135 lines) - Split into SettingsActivity + SettingsManager + SettingsUIController  
3. ✅ `PoseLandmarkerHelper.kt` (391 → 165 lines) - Split into PoseLandmarkerHelper + Config + Processor
4. ✅ `GalleryFragment.kt` (455 → 241 lines) - Split into GalleryFragment + GalleryUIController + GalleryMediaProcessor
5. ✅ `CameraFragment.kt` (412 → 232 lines) - Split into CameraFragment + CameraUIController + CameraManager

### All Refactoring Complete! 🎉
All Kotlin files in the project now comply with the 250-line limit. The codebase is more maintainable, testable, and follows the Single Responsibility Principle.
