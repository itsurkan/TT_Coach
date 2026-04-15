# Trajectory Pipeline V3

**Updated**: 2026-03-28

## Overview

Real-time ball trajectory detection and prediction for table tennis. Takes YOLO ball detections (x, y, confidence per frame at 10fps) and:

1. Filters low-confidence detections (< 0.1)
2. Detects contacts (table bounces, racket hits) from ball behavior
3. Splits trajectory into arcs at racket contacts, segments at table bounces
4. Fits polynomial curves to each segment
5. Predicts future position using velocity + gravity + bounce simulation

Causal (past data only). Designed for live camera feed.

Entry point: `predictTrajectoryV3()` in `src/utils/trajectoryPipelineV3.ts`

---

## Physics Model

```
x(t) = ax + bx·t + cx·t²              quadratic — captures drag / side spin
y(t) = ay + by·t + cy·t² + dy·t³      cubic (5+ pts) — captures spin asymmetry
```

- `t` = frame offset from segment start (integer, 1 frame = 100ms)
- Coordinates normalized [0, 1]. Y increases downward (screen coords).
- `cx`: horizontal deceleration (drag) or side spin curve
- `cy`: gravity in screen coords. Min enforced at 0.015 for prediction.
- `dy`: cubic Y term. Only fitted with 5+ points. Breaks arc symmetry for spin.

**Fit rules:**

| Points | X | Y |
|--------|---|---|
| 2      | Linear | Linear |
| 3-4    | Quadratic | Quadratic |
| 5+     | Quadratic | Cubic |

Fit method: least-squares via Cramer's rule on normal equations.

---

## Contact Detection

Detects contacts purely from ball behavior — no external JSON required.

### Three detection methods (evaluated in order)

**1. Angle-based reversal** (primary)

Compute velocity vectors before/after each detection. If angle > 45° and not a gravity apex:
- Table bounce: Y reverses (down→up), X preserved, near table surface
- Racket hit: everything else

**2. Rapid X velocity change** (racket-specific)

```
|vxOut - vxIn| > 0.035 → racket contact
```

Catches hits where angle < 45° but ball speed jumps — e.g., racket accelerates ball in roughly the same direction.

**3. Component-based** (fallback)

- X reversal: vx flips sign beyond ±0.01
- Y reversal (down→up): vy flips from > 0.003 to < -0.003
- Table bounce = Y reversal + near table + X preserved
- Racket = X reversal, or Y reversal without table proximity

### Table bounce vs racket hit

| Signal | Table bounce | Racket hit |
|--------|-------------|------------|
| Y velocity | Reverses (down→up) | May or may not |
| X velocity | **Same direction** | **Reverses or jumps** |
| X acceleration | Small (< 0.02) | Large (> 0.035) |

**Key rule**: table bounce NEVER reverses X. If X flips at a Y reversal, it's a racket hit.

### Ball-diameter rule

After 3+ detections in an arc: fit through all-but-last, predict where ball should be. If actual position is > 1 ball diameter away:
- X direction preserved → table contact (keep in arc)
- X direction changed → racket contact (start new arc)

---

## Arc Splitting

Events system classifies each contact:
- `split` = hard break (racket hit, frame gap > 3)
- `table` = table bounce (1 allowed per arc)

**Backward walk** from latest event:
- `split` → arc starts there, stop
- Count `table` events. If > 1 → split at 2nd table event
- Result: current arc has at most 1 table bounce

Sliding window: arc capped at 15 detections.

---

## Segment Splitting (within arc)

Table bounces within the arc split it into **segments**. Each segment gets its own fit → sharp V-shape at bounce points.

```
Arc: [d0, d1, ..., dBounce, ..., dN]
Segment 1: [d0 ... dBounce]      pre-bounce (includes bounce point)
Segment 2: [dBounce ... dN]      post-bounce (starts at bounce point)
```

Detection: any down→up Y reversal with X direction preserved. No table model dependency.

---

## Prediction

Uses velocity from the **last segment only**.

### Normal (last segment ≥ 3 points)

```
vx = average from last 4 detections
vy = average from last 4 detections
gravity = max(fitted cy, 0.015)

Per future frame:
  x += vx
  y += vy + gravity
  vy += 2·gravity
```

### Post-bounce (last segment < 3 points)

Inherits from pre-bounce segment:
```
vx = pre-bounce vx × frictionFactor
vy = -|pre-bounce vy| × restitution     (reflected upward)
vy capped at -0.03
```

### Bounce simulation

If predicted Y reaches table surface within table X bounds → bounce. Prediction stops after 1 bounce (shows contact point).

---

## Spin Classification

From cubic coefficient `dy` (5+ points only):

| dy | Shape | Class | Restitution | Friction |
|----|-------|-------|-------------|----------|
| > 0.00005 | Descent steeper | Topspin | 0.75 | 1.05 |
| < -0.00005 | Ascent steeper | Backspin | 0.90 | 0.80 |
| small | Symmetric | Flat | 0.85 | 0.95 |

---

## Table Surface Model

Built from ALL past detections. Finds every down→up Y reversal in the history.

```
tableY(x) = slope·x + intercept
```

- Linear fit through screen (x, y) of bounce points → captures camera angle
- Table X boundaries from bounce positions + 30% margin
- Single bounce: flat model, width ~30% screen

Used for: prediction bounce sim, table line overlay.
NOT used for: segment splitting (Y reversal alone is sufficient).

---

## Data Flow

```
Frame arrives → extract ball (conf ≥ 0.1)
  ↓
Build table model (all past Y reversals)
  ↓
Detect events: gaps, angle reversals, X accel, component reversals
  ↓
Backward walk → arc start (max 1 table bounce allowed)
  ↓
Ball-diameter check on last detection
  ↓
Split arc into segments at table bounces
  ↓
Fit each segment (quadratic X, cubic/quadratic Y)
  ↓
Classify spin from dy
  ↓
Prediction velocity:
  ≥3 pts → average from last segment
  <3 pts → inherited from pre-bounce (reflected)
  ↓
Extrapolate: linear X, quadratic Y + gravity
  ↓
Bounce check → stop at contact if within table bounds
  ↓
Return: pastSegments, predictedPositions, tableSurface, spinClass, bounces
```

---

## Thresholds

| Parameter | Value | Purpose |
|-----------|-------|---------|
| BOUNCE_VY_THRESHOLD | 0.003 | Min vy for bounce detection |
| X_REVERSAL_THRESHOLD | 0.01 | Min vx for X reversal |
| REVERSAL_ANGLE_THRESHOLD | 45° | Min angle between velocity vectors |
| RACKET_X_ACCEL_THRESHOLD | 0.035 | Min \|dvx\| for racket contact |
| MIN_REVERSAL_SPEED | 0.005 | Noise filter for reversal detection |
| MAX_FRAME_GAP | 3 | Frames missing → hard split |
| MAX_ARC_LENGTH | 15 | Sliding window size |
| MIN_GRAVITY | 0.015 | Prediction gravity floor |
| MAX_BOUNCE_VY | 0.03 | Max upward velocity after bounce |
| MIN_BALL_SPEED | 0.004 | Below = stationary, no trajectory |
| MAX_PREDICTION_BOUNCES | 1 | Prediction stops after N bounces |
| SPIN_DY_THRESHOLD | 0.00005 | Min \|dy\| for spin classification |
| Confidence filter | 0.1 | Detection exclusion threshold |

Tuned for 10fps YOLO data, normalized [0,1] coordinates.

---

## Types

```
ParabolicFitV3 {
  ax, bx, cx: number       x(t) = ax + bx·t + cx·t²
  ay, by, cy: number       y(t) = ay + by·t + cy·t² + dy·t³
  dy: number               cubic Y (0 when ≤4 points)
}

PredictiveTrajectoryV3 {
  pastPositions: []         flat array of all positions
  pastSegments: [][]        per-segment positions (for sharp V rendering)
  predictedPositions: []    extrapolated future
  fit: ParabolicFitV3       last segment's fit
  segmentStartFrame: number
  detectionCount: number
  tableSurface: TableSurfaceModel
  predictedBounces: PredictedBounce[]
  spinClass: SpinClass      'topspin' | 'backspin' | 'flat'
}

TableSurfaceModel {
  bouncePoints: []          observed bounce (x, y, radiusPx, frameIndex)
  slope, intercept: number  tableY(x) = slope·x + intercept
  xMin, xMax: number        estimated table boundaries
  isValid: boolean
}

FittedPositionV3 { frameIndex, x, y, source: 'DETECTED' | 'INTERPOLATED' }
PredictedBounce { x, y, dt }
```

---

## Rendering

Component: `TrajectoryV3Overlay.tsx` (SVG, viewBox 0-1)

| Element | Style | Purpose |
|---------|-------|---------|
| Segment paths | Solid cyan (#22d3ee), 3px | Past arc per segment (Catmull-Rom spline) |
| Predicted path | Dashed cyan, 2.5px | Future extrapolation |
| White dots | r=0.006 | Detected positions |
| Cyan dots (fading) | r=0.005, decreasing opacity | Predicted positions |
| Orange diamonds | r=0.009 | Observed bounce points from table model |
| Cyan triangles | r=0.01 | Predicted bounce contact point |
| Table line | Dashed cyan, 50% opacity | Table surface (clipped to xMin-xMax) |
| Spin label | Red (topspin) / Blue (backspin) | Above arc midpoint |
| Info text | Bottom-left | Point count, bounces, spin, table obs |

Each segment rendered as a separate SVG path → sharp V at table bounce junctions.
Splines: Catmull-Rom → cubic bezier conversion.

---

## Files

| File | Purpose |
|------|---------|
| `src/utils/trajectoryPipelineV3.ts` | V3 pipeline: fitting, detection, prediction, table model |
| `src/utils/trajectoryPipeline.ts` | Shared math: `fitLinear`, `fitQuadratic`, `fitCubic` |
| `src/components/TrajectoryV3Overlay.tsx` | SVG rendering |
| `src/App.tsx` | State, frame handler, wiring |

---

## Limitations

| Factor | Handled? | Notes |
|--------|----------|-------|
| Gravity | Yes | cy term, min 0.015 |
| Topspin/backspin | Partially | dy cubic + spin-adjusted bounce |
| Side spin | Partially | cx term |
| Air drag | Partially | cx captures it, not used in prediction |
| Table bounce | Yes | Spin-dependent restitution + friction |
| Camera perspective | Yes | Linear table model through bounce coords |
| Racket contact | Yes | Angle + X accel + ball-diameter |
| Multiple bounces | No | Prediction stops after 1 |
| Net interaction | No | Not detected |
