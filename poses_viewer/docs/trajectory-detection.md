# Ball Trajectory Detection

**Feature**: 002-ball-tracking | **Date**: 2026-03-27

## Overview

Takes YOLO ball detections (x, y, confidence per frame) and:
1. Filters low-confidence detections (confidence < 0.1)
2. Detects direction changes to split into trajectory arcs
3. Fits parabolic curves to each arc
4. In predict mode: uses only past data to extrapolate forward

## Entity Diagram

```
┌─────────────────────────┐
│    ParabolicFit          │
├─────────────────────────┤
│ ax: number               │ ◄── x(t) = ax + bx*t + cx*t²
│ bx: number               │     (horizontal velocity)
│ cx: number               │     (drag / side spin)
│ ay: number               │ ◄── y(t) = ay + by*t + cy*t²
│ by: number               │     (initial vertical velocity)
│ cy: number               │     (gravity in screen coords)
└─────────────────────────┘

┌─────────────────────────┐       ┌──────────────────────────┐
│   TrajectorySegment      │       │    ContactEvent           │
├─────────────────────────┤       ├──────────────────────────┤
│ segmentIndex: number     │──────►│ type: ContactType         │
│ startFrameIndex: number  │ 0..1  │ frameIndex: number        │
│ endFrameIndex: number    │ start │ timestampMs: number       │
│ fittedPositions: []      │◄──────│ position: {x, y}          │
│ fitCoefficients: PF      │ 0..1  │ velocityBefore: number    │
│ fitRmsError: number      │ end   │ velocityAfter: number     │
│ segmentDurationMs: number│       │ confidence: number        │
│ contactBefore: CE?       │       └──────────────────────────┘
│ contactAfter: CE?        │
└─────────────────────────┘       ┌──────────────────────────┐
                                   │    ContactType             │
┌─────────────────────────┐       ├──────────────────────────┤
│   FittedPosition         │       │ BOUNCE                   │
├─────────────────────────┤       │ PADDLE_CONTACT           │
│ frameIndex: number       │       │ NET_CLIP                 │
│ x: number                │       │ UNKNOWN_CONTACT          │
│ y: number                │       └──────────────────────────┘
│ source: DETECTED |       │
│         INTERPOLATED     │
└─────────────────────────┘

┌─────────────────────────┐
│  PredictiveTrajectory    │
├─────────────────────────┤
│ pastPositions: []        │ ◄── Fitted from arc start to now
│ predictedPositions: []   │ ◄── Extrapolated into future
│ fit: ParabolicFit        │
│ segmentStartFrame: number│
│ detectionCount: number   │
└─────────────────────────┘
```

## Two Modes

### Fitted (retroactive)

Uses all detections across the entire video. Applies kinematic contact detection (velocity reversals, speed ratio spikes, direction angle changes) to split into segments. Fits parabolas to each segment. Recursive sub-splitting when RMS error exceeds threshold or gravity constraint is violated (cy <= 0).

Entry point: `segmentTrajectory(frames, intervalMs)`

### Predict (causal)

At frame N, uses only detections from frames 0..N. Detects direction reversals to find the latest sub-arc. Fits a parabola to that arc. Extrapolates forward using current velocity + gravity.

Entry point: `predictTrajectory(frames, currentFrame, intervalMs, predictionFrames)`

## Physics Model

### Past Fit

```
x(t) = ax + bx*t + cx*t²    (quadratic — captures drag / side spin)
y(t) = ay + by*t + cy*t²    (quadratic — gravity effect)
```

- `t` = frame offset from arc start (integer)
- `cx` captures horizontal deceleration (air drag) or side spin curve
- `cy > 0` = ball curving downward (correct gravity in screen coords where Y increases downward)
- `cy <= 0` = unphysical — forces segment split in fitted mode

### Prediction Extrapolation

```
xNow = evaluate x(t) at current frame
yNow = evaluate y(t) at current frame
vxNow = bx + 2*cx*t           (current horizontal velocity)
vyNow = by + 2*cy*t           (current vertical velocity)
gravity = max(cy, 0.002)      (minimum downward acceleration)

For future frame dt steps ahead:
  x = xNow + vxNow * dt       (LINEAR — no cx amplification)
  y = yNow + vyNow * dt + gravity * dt²
```

Key decisions:
- **Linear X for prediction**: quadratic cx*dt² caused prediction to loop back on itself. Current velocity is extrapolated linearly.
- **Minimum gravity = 0.002**: prevents prediction from curving upward on noisy straight shots. Even fast flat balls have slight downward arc from gravity.

### Fit Method

Least-squares via Cramer's rule on normal equations:
- 2 points: linear fit for both X and Y (cx = cy = 0)
- 3+ points: quadratic fit for both X and Y

## Rules for New Trajectory (Predict Mode)

A new trajectory starts when the ball changes direction:

| Direction change | Meaning | New trajectory? |
|---|---|---|
| Left → Right | Paddle contact (ball hit back) | **YES** |
| Right → Left | Paddle contact (ball hit back) | **YES** |
| Bottom → Top | Table bounce (ball bouncing up) | **YES** |
| Top → Bottom | Gravity (ball falling naturally) | **NO** — same arc |

### Thresholds

- X reversal: `|vx| > 0.01` normalized units/frame
- Y reversal (bottom→top only): `vyIn > 0.003` AND `vyOut < -0.003`

### How it Works

1. Collect all past detections from frame 0 to current frame (confidence >= 0.1)
2. Walk through them looking for reversals
3. Split at each reversal point
4. Use only the **last sub-arc** for fitting and prediction
5. Show only current sub-arc dots (not all past detections)

## Contact Detection (Fitted Mode)

Three-signal kinematic detector applied at each interior detection point:

| Signal | Condition | Contact Type | Confidence |
|---|---|---|---|
| Vertical velocity reversal | vy sign changes, both `|vy| > MIN_SPEED` | BOUNCE | 0.9 |
| Speed ratio spike | ratio > 2.5x or inverse > 2.5x | PADDLE_CONTACT | 0.85 |
| Direction angle change | angle > 60° | NET_CLIP or UNKNOWN_CONTACT | 0.7 |

Signals are evaluated in priority order (bounce > speed > angle). First match wins.

## Thresholds

| Parameter | Value | Purpose |
|---|---|---|
| SPEED_RATIO_THRESHOLD | 2.5 | Min speed change ratio for paddle contact |
| DIRECTION_ANGLE_THRESHOLD | 60° | Min direction change for contact detection |
| BOUNCE_ANGLE_THRESHOLD | 60° | Min angle for NET_CLIP classification |
| MAX_FIT_RMS_ERROR | 0.008 | Triggers recursive sub-splitting |
| MAX_RECURSION_DEPTH | 2 | Limits sub-splitting depth |
| MIN_SPEED | 0.015 | Minimum velocity for meaningful contact detection |
| MIN_GROUP_SIZE | 3 | Minimum detections per segment |
| MIN_GRAVITY | 0.002 | Floor for prediction gravity coefficient |
| Confidence filter | 0.1 | Detections below this are excluded |

Tuned for 10fps YOLO data (100ms frame intervals, normalized [0,1] coordinates).

## Physics Limitations

| Factor | Real effect | Model handles it? |
|---|---|---|
| Gravity | Ball curves downward | YES (cy term) |
| Topspin | Ball dips faster, accelerates after bounce | NO — cy underestimates drop |
| Backspin | Ball floats longer, slows after bounce | NO — cy overestimates drop |
| Side spin | Horizontal curve (not straight X line) | Partially — cx captures some curve |
| Air drag | Ball decelerates (more at high speed) | Partially — cx captures deceleration |
| Magnus effect | Spin creates lift/sink force | NO |
| Bounce speed change | Ball speeds up or slows depending on spin | NO |

### Impact on Prediction

- Short predictions (2-3 frames): error is small, model works well
- Long predictions (5+ frames): spin and drag accumulate, prediction diverges
- After bounce: speed/direction change depends on spin, which can't be inferred from position alone

## Known Problems

### 1. Prediction goes through the table

Without table position, the parabola keeps curving past the table surface. No bounce simulation.

Possible fixes:
- Manual table Y (user clicks once per video)
- Estimate from first few detected bounce Y positions
- Limit prediction to N frames
- Compute parabola-table intersection if table Y is known

### 2. Arc too long without splits

Sometimes 150+ detections accumulate without a detected reversal. Fit degrades over long sequences. Reversal thresholds may be too strict for some sequences.

Possible fixes:
- Maximum arc length (e.g., 20 frames)
- Sliding window (only use last N detections)
- Adaptive thresholds based on ball speed

### 3. X reversal detection is noisy at 10fps

At 100ms intervals, ball position jitters. Small X oscillations can trigger false reversals.

Possible fixes:
- Require sustained direction (2+ consecutive frames)
- Increase X threshold for slow-moving balls
- Smooth positions before reversal detection

### 4. Fitted mode produces too many segments

Kinematic contact detector thresholds were tuned for 30fps. At 10fps (100ms intervals), velocity jumps are larger and noisier, producing many false contacts.

Possible fixes:
- Scale thresholds by frame interval
- Use only direction reversal rules (same as predict mode)
- Use predict mode exclusively (more practical for real-time use)

## Rendering

### Predict Mode (Canvas)

- **Solid magenta line**: past arc (evaluateFit with sub-frame sampling for smooth curves)
- **Dashed magenta line**: predicted future (drawn through predicted position points)
- **White dots**: detected ball positions in current arc only
- **Fading magenta dots**: predicted positions (opacity decreases with distance)
- **X marker**: prediction endpoint

### Fitted Mode (Canvas)

- **Colored parabolic curves**: one per segment (sub-frame sampled)
- **Diamond markers**: contact events (only current + adjacent segments shown)

## Data Flow

```
YOLO ball detections (per frame)
    ↓ filter confidence < 0.1
Past detected balls (x, y, frameIndex)
    ↓
    ├── Predict mode                    ├── Fitted mode
    │   ↓ detect X/Y reversals         │   ↓ kinematic contact detection
    │   Sub-arcs                        │   Groups (split at contacts)
    │   ↓ take last sub-arc            │   ↓ fit each group (min 3 points)
    │   Current arc (2+ points)         │   TrajectorySegments[]
    │   ↓ least-squares fit            │   ↓ recursive sub-split (high RMS)
    │   ParabolicFit                    │   Final segments with filled gaps
    │   ↓ current velocity + gravity   │
    │   Predicted positions             │
    │   ↓                              │
    └── Render on canvas ◄─────────────┘
```

## Files

| File | Purpose |
|---|---|
| `src/utils/trajectoryPipeline.ts` | All trajectory math: fitting, contact detection, segmentation, prediction |
| `src/App.tsx` | State management, compute trajectory on frame change, pass to canvas |
| `src/components/PoseCanvas.tsx` | Render past arc (solid), prediction (dashed), detected dots |
| `src/types.ts` | Re-exports TrajectorySegment, FittedPosition, ParabolicFit, etc. |

### Kotlin Counterparts (TT_Coach_AI)

| Kotlin file | TypeScript equivalent |
|---|---|
| `shared/.../TrajectoryFilter.kt` | `fitTrajectory`, `evaluateFit`, `rmsError`, `fillGaps` |
| `shared/.../models/TrajectorySegment.kt` | `ParabolicFit`, `ContactEvent`, `TrajectorySegment` interfaces |

Note: The TypeScript version extends the Kotlin model with:
- Quadratic X fit (cx term) — Kotlin uses linear X only
- Predict mode with direction reversal splitting — not in Kotlin
- Minimum gravity floor (0.002) for prediction
