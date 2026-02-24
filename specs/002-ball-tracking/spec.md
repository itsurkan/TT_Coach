# Feature Specification: Ball Tracking and Trajectory Prediction

**Feature Branch**: `002-ball-tracking`
**Created**: 2026-02-24
**Status**: Draft
**Input**: User description: "Phase 2: Ball Tracking - Camera optimization for minimal motion blur, ball detection by color/shape in ROI, parabolic trajectory filter to reconstruct ball path between frames, synchronization of skeleton and ball coordinates into a unified time stream."

## User Scenarios and Testing *(mandatory)*

### User Story 1 - Detect Ball During Rally (Priority: P1)

As a table tennis player recording a practice session, I want the app to detect and track the ball in real-time so that I can later review where the ball was at any point during a rally.

**Why this priority**: Ball detection is the foundational capability. Without reliably locating the ball in each frame, no trajectory analysis or synchronization is possible.

**Independent Test**: Can be fully tested by recording a rally and verifying the ball is highlighted/marked in the playback. Delivers value by showing the user where the ball is in each frame.

**Acceptance Scenarios**:

1. **Given** a recorded video of a table tennis rally with adequate lighting, **When** the system processes the video, **Then** the ball position is identified in at least 70% of frames where the ball is visible.
2. **Given** a frame where the ball is partially occluded or moving fast, **When** the system cannot confidently detect the ball, **Then** the frame is marked as "ball not detected" rather than producing a false positive.
3. **Given** a scene with multiple round, bright objects (e.g., water bottle cap, ceiling light), **When** the system searches for the ball, **Then** it restricts detection to the Region of Interest (table area) and does not misidentify non-ball objects.

---

### User Story 2 - View Reconstructed Ball Trajectory (Priority: P2)

As a coach reviewing a player session, I want to see the full flight path of the ball (including between frames where detection was lost) so that I can assess spin, speed, and arc quality.

**Why this priority**: Raw frame-by-frame ball positions have gaps. Reconstructing the trajectory transforms sparse detections into a continuous, useful flight path that enables coaching insights.

**Independent Test**: Can be tested by reviewing a rally playback where the ball path is drawn as a smooth curve. The curve should visually match the actual ball flight even through frames where the ball was not detected.

**Acceptance Scenarios**:

1. **Given** ball detections in frames N, N+1, and N+4 (with gaps at N+2, N+3), **When** the trajectory filter runs, **Then** estimated positions for frames N+2 and N+3 are generated following a physically plausible parabolic arc.
2. **Given** a sequence of detected ball positions, **When** the trajectory is reconstructed, **Then** the predicted path deviates from actual ball position by no more than 3 cm on average (measured on validation clips).
3. **Given** the ball changes direction (e.g., bounce on table, contact with paddle), **When** the trajectory filter processes positions around the bounce, **Then** it recognizes the direction change and starts a new trajectory segment.

---

### User Story 3 - Synchronized Skeleton-Ball Timeline (Priority: P3)

As a player reviewing my session, I want to see my body pose and the ball position together on the same timeline so that I can understand my positioning relative to the ball at the moment of contact.

**Why this priority**: Combining skeleton (already implemented in Phase 1) with ball data creates the full picture needed for stroke analysis. This depends on both detection and trajectory being reliable first.

**Independent Test**: Can be tested by playing back a session and confirming that the ball overlay and skeleton overlay are aligned in time -- ball contact should visually coincide with the paddle-hand reaching the ball.

**Acceptance Scenarios**:

1. **Given** a recorded session with both skeleton tracking and ball tracking active, **When** the user plays back the session, **Then** ball position and skeleton pose are displayed together, synchronized to within 1 frame (no visible lag between them).
2. **Given** timestamps from the skeleton tracker and the ball tracker, **When** the system merges them, **Then** each unified data point contains both body pose and ball position for the same moment in time.
3. **Given** the ball tracker produces data at a different rate than the skeleton tracker, **When** synchronization occurs, **Then** the system interpolates the lower-frequency data to match the higher-frequency stream.

---

### User Story 4 - Optimized Camera Capture (Priority: P1)

As a user starting a recording session, I want the camera to automatically optimize its settings for ball tracking so that I get clear, sharp frames even when the ball is moving fast.

**Why this priority**: Same priority as detection -- if the camera produces blurry frames, ball detection accuracy drops dramatically. Camera optimization is a prerequisite for reliable tracking.

**Independent Test**: Can be tested by comparing a recording made with default camera settings vs. optimized settings. The optimized recording should show noticeably less motion blur on the ball.

**Acceptance Scenarios**:

1. **Given** the user starts a ball-tracking recording session, **When** the camera initializes, **Then** it configures exposure time to minimize motion blur while maintaining sufficient brightness.
2. **Given** varying lighting conditions (bright gym vs. dimmer basement), **When** the camera auto-adjusts, **Then** it finds a balance between low exposure (less blur) and adequate frame brightness (ball still visible).
3. **Given** the optimized camera mode is active, **When** recording at the target frame rate, **Then** the ball appears sharp (not smeared) in at least 80% of frames during a typical rally.

---

### Edge Cases

- What happens when the ball goes out of the camera field of view? The system should mark the ball as "out of frame" and not attempt to extrapolate indefinitely.
- How does the system handle a ball that is stationary (e.g., held by the player or resting on the table)? It should still detect and track it without errors.
- What if the lighting changes mid-session (e.g., someone turns on additional lights)? The camera settings and detection thresholds should adapt without requiring a restart.
- What happens when two balls are visible on the table? The system should track only the ball in active play (the moving one) and ignore the spare.
- How does the system handle net clips where the ball barely touches the net and changes trajectory unpredictably? The trajectory filter should detect the anomalous direction change and start a new segment.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect a table tennis ball in video frames using color and shape analysis within a defined Region of Interest (table area).
- **FR-002**: System MUST define and maintain a Region of Interest that encompasses the playing area to reduce false detections and improve processing speed.
- **FR-003**: System MUST configure the camera for low exposure time to minimize motion blur of the fast-moving ball.
- **FR-004**: System MUST adapt camera exposure settings based on ambient lighting conditions to maintain ball visibility.
- **FR-005**: System MUST reconstruct the ball flight path between frames where detection was lost, using a parabolic trajectory model.
- **FR-006**: System MUST detect trajectory breaks (bounces, paddle contact, net clips) and start new trajectory segments at those points.
- **FR-007**: System MUST merge ball position data and skeleton pose data into a single synchronized timeline.
- **FR-008**: System MUST interpolate data from the lower-frequency tracker to align with the higher-frequency tracker during synchronization.
- **FR-009**: System MUST mark frames where ball detection confidence is below threshold as "not detected" rather than reporting a false position.
- **FR-010**: System MUST track only the ball in active play when multiple balls are visible in the frame.
- **FR-011**: System MUST mark the ball as "out of frame" when it leaves the camera field of view and stop trajectory extrapolation.

### Key Entities

- **BallDetection**: Represents a single detection of the ball in one frame -- includes position (x, y coordinates within the frame), confidence score, frame timestamp, and detection status (detected / not detected / out of frame).
- **Trajectory Segment**: A continuous arc of ball flight between two direction-change events (bounce, paddle contact, or net clip). Contains a sequence of BallDetection points plus interpolated positions for gap frames.
- **Region of Interest (ROI)**: The rectangular area within the camera frame where ball detection is performed, corresponding to the playing surface and immediate surroundings.
- **Synchronized Frame**: A unified data point that combines skeleton pose data and ball position data for a single moment in time, enabling combined analysis.
- **Camera Configuration**: The set of camera parameters (exposure, frame rate, white balance) optimized for ball tracking under current lighting conditions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ball is correctly detected in at least 70% of frames where it is visible during a typical rally.
- **SC-002**: False positive rate (non-ball objects identified as ball) is below 5% of total detections.
- **SC-003**: Reconstructed trajectory deviates from actual ball position by no more than 3 cm on average in validation clips.
- **SC-004**: Skeleton and ball data are synchronized to within 1 frame (no perceptible lag in playback).
- **SC-005**: Optimized camera settings result in the ball appearing sharp (non-blurred) in at least 80% of frames during active play.
- **SC-006**: The system processes ball detection and trajectory reconstruction without dropping below the target frame rate during recording.

## Assumptions

- The table tennis ball is a standard 40mm white or orange ball (standard ITTF size and color).
- The camera is positioned with a clear view of the table surface (the full table or at least the player half is visible).
- Skeleton tracking (from Phase 1) is already functional and producing timestamped pose data.
- Indoor lighting is available (the system does not need to operate in outdoor or extremely low-light conditions).
- The device running the app has sufficient processing power to handle both skeleton tracking and ball detection simultaneously.
