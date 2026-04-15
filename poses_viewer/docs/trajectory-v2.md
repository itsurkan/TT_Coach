# Trajectory V2 Pipeline

**Feature**: 002-ball-tracking | **Date**: 2026-03-28  
**File**: `src/utils/trajectoryPipelineV2.ts`  
**Overlay**: `src/components/TrajectoryV2Overlay.tsx` (SVG, vector quality at any zoom)

## Overview

V2 reuses V1's parabolic fitting (`fitTrajectory`, `evaluateFit`) but adds:
1. **Table surface model** — perspective-aware table Y estimation from observed bounces
2. **Bounce simulation** — predicted trajectory reflects off the table
3. **6 arc splitting strategies** — aggressive contact/bounce detection
4. **Average velocity prediction** — handles perspective deceleration
5. **Minimum speed gate** — no prediction for stationary balls
6. **Table X bounds** — prediction clipped to estimated table area

## Entity Diagram

```
┌─────────────────────────┐
│   TableSurfaceModel      │
├─────────────────────────┤
│ bouncePoints: {x,y,fi}[]│ ◄── Observed bottom→top Y reversals
│ slope: number            │ ◄── tableY(x) = slope * x + intercept
│ intercept: number        │     (perspective: near side has higher Y)
│ isValid: boolean         │
│ tableXMin: number        │ ◄── Estimated table left edge
│ tableXMax: number        │ ◄── Estimated table right edge
└─────────────────────────┘

┌─────────────────────────┐
│  PredictiveTrajectoryV2  │
├─────────────────────────┤
│ pastPositions: []        │ ◄── Fitted from arc start to now
│ predictedPositions: []   │ ◄── Extrapolated with bounce simulation
│ fit: ParabolicFit        │
│ segmentStartFrame: number│
│ detectionCount: number   │
│ tableSurface: TSM        │ ◄── Learned table model
│ predictedBounces: []     │ ◄── Where prediction bounces off table
└─────────────────────────┘
```

## Processing Pipeline

```
YOLO detections ──► Filter (conf ≥ 0.1) ──► Arc Splitting ──► Fit latest arc
                                                                    │
                                           ┌────────────────────────┘
                                           ▼
                                    Residual check ──► Re-split if needed
                                           │
                                           ▼
                                   Average velocity ──► Speed gate
                                           │
                                           ▼
                              Prediction loop (bounce simulation)
                                           │
                                           ▼
                              SVG rendering (TrajectoryV2Overlay)
```

## Arc Splitting (6 strategies)

V2 uses 6 complementary strategies to detect when the ball's trajectory changes. All split the detection array into sub-arcs; only the **latest** sub-arc is fitted.

| # | Strategy | Condition | Detects |
|---|----------|-----------|---------|
| 1 | **Position jump** | Distance > `0.08 × frameGap` | Ball teleported (player hit, different ball) |
| 2 | **Time gap** | 3+ frames between detections | Ball out of view (behind player/net) |
| 3 | **3-pt Y reversal** | vy: >0.003 → <−0.003 | Table bounces (interior points) |
| 4 | **3-pt X reversal** | vx sign flips (±0.01) | Paddle contacts (interior points) |
| 5 | **2-pt reversal** | X or Y reversal at last 2 velocity pairs | Immediate (no 3rd frame needed) |
| 6 | **Residual split** | Last 3 detections deviate >0.02 from fit | Same-direction paddle contacts |

Strategies 1-5 run on the raw detection array. Strategy 6 runs after fitting — if the recent detections don't match the fitted curve, the arc is re-split and re-fit.

## Table Surface Model

Built from **all** past detections (not just current arc), so it accumulates knowledge across the entire video:

1. **Bounce detection**: Find bottom→top Y reversals (ball going down then up)
2. **Vertex interpolation**: Parabolic fit through 3 frames to find true bounce Y  
   (at 10fps, the detection frame is already above the actual table surface)
3. **Linear fit**: `tableY(x) = slope × x + intercept`  
   With 2+ bounces at different X, this captures the perspective angle of the table
4. **X bounds**: `[min(bounceX) − margin, max(bounceX) + margin]`  
   Margin = 5% + 30% of bounce X span. Bounces are only simulated within these bounds.

## Prediction Physics

```
For each future frame:
  x_next = x + vx × dt              ◄── Linear X (no drag accumulation)
  y_next = y + vy × dt + g × dt²    ◄── Quadratic Y (gravity)
  vy_next = vy + 2g × dt            ◄── Gravity accelerates vy
  vx_next = vx                      ◄── Constant horizontal speed

  If ball crosses table surface (within table X bounds):
    y_next = reflect above table     ◄── Restitution 0.5
    vy_next = −|vy| × 0.5           ◄── Reverse + dampen
    vx = vx × 0.85                  ◄── Friction slows horizontal
```

### Why linear X instead of quadratic?

The fitted `cx` coefficient captures apparent deceleration from perspective (ball moving away from camera slows down in 2D). Using `cx·dt²` in prediction causes the trajectory to loop back. Instead, `vxNow` already includes the cx effect from the fit derivative — so linear extrapolation preserves the direction while the instantaneous velocity is accurate.

### Why average velocity instead of fit derivative?

The fit derivative `bx + 2·cx·t` can approach zero for long arcs where perspective deceleration dominates. Average velocity from the last 4 detections gives the actual recent ball motion.

## Constants

| Name | Value | Purpose |
|------|-------|---------|
| `BOUNCE_VY_THRESHOLD` | 0.003 | Min vy for Y reversal to count as bounce |
| `RESTITUTION` | 0.5 | Vertical energy retained after bounce |
| `BOUNCE_VX_DAMPING` | 0.85 | Horizontal speed retained after bounce |
| `MAX_PREDICTION_BOUNCES` | 3 | Max simulated bounces per prediction |
| `MIN_GRAVITY` | 0.005 | Gravity floor (prevents upward drift) |
| Position jump | 0.08/frame | Max allowed distance between detections |
| Residual threshold | 0.02 | Max deviation before arc re-split |
| Min speed | 0.005 | Below this = stationary, no prediction |
| Time gap | 3 frames | Missing detections → new arc |

## SVG Rendering

Uses SVG with `viewBox="0 0 1 1"` (normalized coordinates). All strokes use `vectorEffect="non-scaling-stroke"` for consistent pixel width at any zoom.

| Element | Style | Purpose |
|---------|-------|---------|
| Past arc | Solid emerald (#34d399), 3px + dark outline | Fitted curve |
| Predicted arc | Dashed emerald, 2.5px + dark outline | Future trajectory |
| Detected dots | White circles, dark border | Actual ball positions |
| Predicted dots | Fading emerald circles | Predicted positions |
| Bounce markers | Emerald triangles (▲) | Where prediction hits table |
| Observed bounces | Orange diamonds (◆) | Historical table contact points |
| Table line | Dashed emerald, clipped to X bounds | Estimated table surface |

## File Map

| File | Purpose |
|------|---------|
| `src/utils/trajectoryPipelineV2.ts` | V2 pipeline: table model, arc splitting, prediction |
| `src/utils/trajectoryPipeline.ts` | V1 pipeline: fitting, evaluation (shared by V2) |
| `src/components/TrajectoryV2Overlay.tsx` | SVG rendering overlay |
| `src/App.tsx` | State, checkbox, computation effect, sidebar info |

## Known Limitations

- **Perspective**: 2D screen-space tracking can't fully capture 3D physics. Ball moving away from camera appears to slow.
- **Spin**: No spin model. Topspin/backspin affects bounces but isn't detected or simulated.
- **Table model cold start**: Requires observed bounces. First arc has no table model.
- **Table slope**: Needs bounces at different X positions. Same-X bounces → flat model.
