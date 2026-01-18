# Table Tennis Stroke Phases

## Overview

The TT Coach AI system tracks and analyzes table tennis strokes by breaking them down into distinct phases. Each phase is color-coded in the overlay visualization to help players and coaches understand stroke mechanics and timing.

## Stroke Phase Definitions

### 1. READY (Gray)
- **Description**: Initial stance position before the stroke begins
- **Characteristics**:
  - Player is in neutral/waiting position
  - Prepared to react to incoming ball
  - Balanced stance with weight centered
- **Color Code**: Gray (`Color.GRAY`)
- **Visual Location**: [OverlayView.kt:109](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L109)

### 2. BACKSWING (Blue)
- **Description**: The preparation phase where the racket moves backward
- **Characteristics**:
  - Player loads energy by rotating the body
  - Racket pulled back to create potential energy
  - Sets up the trajectory and power for the stroke
  - Weight shifts to the back foot
- **Color Code**: Blue (`Color.rgb(33, 150, 243)`)
- **Visual Location**: [OverlayView.kt:110](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L110)

### 3. FORWARD_SWING (Green)
- **Description**: The acceleration phase where the racket moves forward toward the ball
- **Characteristics**:
  - Body rotation and arm extension generate speed and power
  - Occurs just before ball contact
  - Weight transfers from back to front foot
  - Maximum racket acceleration
- **Color Code**: Green (`Color.rgb(76, 175, 80)`)
- **Visual Location**: [OverlayView.kt:111](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L111)

### 4. CONTACT (Orange)
- **Description**: The critical moment when the racket makes contact with the ball
- **Characteristics**:
  - Determines spin, direction, and speed of the shot
  - Requires precise timing and racket angle
  - Peak moment of energy transfer
  - Most critical phase for shot quality
- **Color Code**: Orange (`Color.rgb(255, 152, 0)`)
- **Visual Location**: [OverlayView.kt:112](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L112)

### 5. FOLLOW_THROUGH (Purple)
- **Description**: Continuation of the swing motion after hitting the ball
- **Characteristics**:
  - Ensures smooth energy transfer
  - Maintains proper stroke mechanics
  - Affects consistency and control of the shot
  - Natural deceleration of the racket
- **Color Code**: Purple (`Color.rgb(156, 39, 176)`)
- **Visual Location**: [OverlayView.kt:113](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L113)

### 6. RECOVERY (Gray)
- **Description**: Return to ready position after completing the stroke
- **Characteristics**:
  - Player repositions for the next shot
  - Returns to balanced, neutral stance
  - Completes the full stroke cycle
  - Prepares for opponent's return
- **Color Code**: Gray (`Color.GRAY`)
- **Visual Location**: [OverlayView.kt:114](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/OverlayView.kt#L114)

## Stroke Cycle Flow

The complete stroke cycle follows this sequence:

```
READY → BACKSWING → FORWARD_SWING → CONTACT → FOLLOW_THROUGH → RECOVERY → READY
```

## Visual Feedback

Each phase is displayed with a distinct color in the overlay view, allowing players to:
- Identify which phase they're currently in during practice
- Analyze timing and transitions between phases
- Improve stroke consistency and mechanics
- Compare stroke patterns across different sessions

## Technical Implementation

The phase detection and color mapping is implemented in:
- **OverlayView.kt**: Visual rendering and color assignment
- **StrokePhase enum**: Phase definitions and state management

## Related Documentation

- [Pose Logging](RAW_POSE_LOGGING.md) - How pose data is captured during each phase
- [Video Debug Testing](VIDEO_DEBUG_TESTING.md) - Testing stroke phase detection
