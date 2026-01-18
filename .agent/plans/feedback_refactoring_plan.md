# Implementation Plan - Feedback Module Refactoring

## User Requirements
- Feedback should be given after each stroke (after Recovery phase).
- Feedback should be an array of items.
- Each item must have a property indicating the `CorrectionType`.
- Refactor `analyzeStroke` for better code quality.

## Proposed Changes

### 1. Models (`AnalysisResult.kt`) - DONE
- Added `CorrectionType` enum.
- Added `FeedbackItem` data class.
- Added `feedbackItems: List<FeedbackItem>` to `AnalysisResult`.

### 2. Motion Analysis (`MotionAnalyzer.kt`)
- Refactor `analyzeStroke` into smaller, focused methods.
- Define a `StrokeMetrics` data class for internal calculations.
- Create a validation pipeline that populates `feedbackItems`.

### 3. Processor Logic (`PoseAnalysisProcessor.kt`) - IN PROGRESS
- Accumulate frames during `BACKSWING` through `RECOVERY`.
- On transition to `READY`, find the "best" frame (e.g. at `CONTACT`).
- Send the `feedbackItems` array to the UI and State Manager.

### 4. State Management (`TrainingStateManager.kt`) - DONE
- Added `feedbackItemsHistory`.
- Added `addFeedbackItems` and `getLatestFeedbackItems`.

### 5. Verification
- Create `MotionAnalyzerTest.kt` to verify that a set of landmarks results in the correct list of feedback items.

## Success Criteria
- [ ] Code is cleaner and easier to read in `MotionAnalyzer`.
- [ ] Feedback is no longer "spammed" frame-by-frame.
- [ ] UI receives an array of specific corrections at the end of a stroke.
