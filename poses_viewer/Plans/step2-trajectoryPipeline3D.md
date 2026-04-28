# Step 2: `trajectoryPipeline3D.ts` — Ball 2D→3D via Marked Homography

## Context
We have a working homography (`tableHomography.ts`) that maps screen pixels ↔ table cm, and ball detections from `_ball_yolo.json`. Goal: project ball positions onto the table surface in cm coordinates, reuse V3's bounce detection, and produce a 3D trajectory.

**Critical constraint**: The homography maps the *table plane* (z=0). Airborne ball projections are distorted by parallax — the projected cm position is shifted toward the far end. Only bounce frames (z=0) give accurate (x_cm, y_cm).

## New File
`D:/Desktop/poses_viewer/src/utils/trajectoryPipeline3D.ts`

## Types (already added to `types.ts`)
```typescript
interface BallPosition3D {
  frameIndex: number
  x_cm: number        // 0=left, 152.5=right  (accurate only at bounces)
  y_cm: number        // 0=far end, 274=near end (accurate only at bounces)
  z_cm: number        // height above table (0=on surface, parabolic between bounces)
  screenX: number     // original normalized screen x
  screenY: number     // original normalized screen y
  confidence: number
}

interface Bounce3D {
  frameIndex: number
  x_cm: number
  y_cm: number
  z_cm: number  // 0 for table surface bounces
}

interface Trajectory3DResult {
  positions: BallPosition3D[]
  bounces: Bounce3D[]
}
```

## Pipeline Logic

### Input
- `ballFrames`: ball detections from `_ball_yolo.json` (all frames up to current)
- `homography`: `TableHomography` from Grid(Marked) — computed from `_table_labels.json`
- `frameIndex`: current frame
- `videoWidth`, `videoHeight`

### Steps

1. **Detect bounces in 2D screen space** — reuse V3's 3-point Y reversal
   - V3 already has robust bounce detection: `vyIn > threshold && vyOut < -threshold`
   - This works in screen coordinates because perspective foreshortening causes visible Y-reversal at bounces
   - Do NOT detect bounces in cm space (parallax distorts airborne velocity)

2. **Project bounce frames through homography** → accurate `(x_cm, y_cm, z_cm=0)`
   - Only at bounce frames is the ball on the table surface, so homography is exact
   - Classify each bounce as `far` (y_cm < 137) or `near` (y_cm > 137) side of net

3. **Project all ball frames through homography** → "shadow" `(x_cm, y_cm)`
   - Airborne frames get a phantom/shadow position (where the ball would be on the table plane)
   - This is useful for the top-down mini view (shows approximate lateral trajectory)
   - Mark these as less reliable than bounce positions

4. **Estimate z_cm (height) via parabolic interpolation between bounces**:
   - At bounce frames: `z_cm = 0`
   - Between consecutive bounces: fit `z(t) = -½g·t² + v0·t` where z(0)=0, z(T)=0
   - Peak height from time between bounces: `z_peak = g·T²/8` (with g ≈ 981 cm/s², T in seconds)
   - Before first bounce / after last bounce: extrapolate from nearest bounce

5. **Return** `{ positions, bounces }`

### Reuse
- V3's bounce detection (3-point Y reversal in screen space) — import or reimplement the core logic
- `screenToTable()` from `tableHomography.ts`
- `computeHomography()` from `tableHomography.ts`

## Wiring in App.tsx
- Get marked homography: `computeHomography(corners_from_tableLabels, videoWidth, videoHeight)`
- Call `computeTrajectory3D(ballFrames, homography, frameIndex, videoWidth, videoHeight)` on frame change
- Pass result to Step 3's overlay component

## Key Files
| File | Action |
|------|--------|
| `src/utils/trajectoryPipeline3D.ts` | **NEW** — main pipeline |
| `src/types.ts` | ✅ 3D types already added |
| `src/utils/tableHomography.ts` | Read-only, reuse `screenToTable()` |
| `src/App.tsx` | Wire up: compute marked homography, call pipeline, pass to overlay |

## Verification
1. Open poses_viewer with a video that has both `_ball_yolo.json` and `_table_labels.json`
2. Console.log 3D positions — bounce cm coordinates should be within table bounds (0-152.5 x, 0-274 y)
3. Bounce positions should match visually observed landing spots in the video
4. z_cm should be 0 at bounces and peak realistically between them (~15-30cm for typical shots)
5. Airborne shadow projections should show reasonable lateral movement pattern in top-down view
