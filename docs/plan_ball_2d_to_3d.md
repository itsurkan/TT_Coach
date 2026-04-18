# Ball 2D→3D Transform using Table Homography

## Context
We have:
- `tableHomography.ts` — ✅ DONE, computes homography from 4 corners, projects screen↔table cm
- Table Grid overlay — ✅ DONE, shows perspective grid + ball cm position on video
- `trajectoryPipelineV3.ts` — existing 2D trajectory with bounce detection, spin classification
- Ball YOLO data — `_ball_yolo.json` per video, normalized (x,y) per frame
- Table keypoints — `_table_yolo_predict.json` per video, 6 keypoints

**Goal**: Project ball 2D positions to table surface cm, show trajectory from above, detect bounces in real coordinates.

## Step 1: `trajectoryPipeline3D.ts` (NEW)
**File**: `D:/Desktop/poses_viewer/src/utils/trajectoryPipeline3D.ts`

Wraps existing V3 pipeline + homography projection.

**Input**: Ball YOLO frames + table homography
**Output**: `Trajectory3D` with cm positions per frame

```typescript
interface BallPosition3D {
  frameIndex: number
  screen: { x: number; y: number }    // normalized 0-1
  table: { x_cm: number; y_cm: number } | null  // projected onto table
  isBounce: boolean                     // V3 bounce detection
  confidence: number
}

interface Trajectory3D {
  positions: BallPosition3D[]
  bounces: Array<{ x_cm: number; y_cm: number; frameIndex: number }>
  // Which side of net (y_cm < 137 = far side, > 137 = near side)
  bounceSides: Array<'far' | 'near'>
}
```

**Logic**:
1. Reuse V3's 3-point Y reversal for bounce detection (already robust)
2. For each detected ball frame, call `screenToTable(H, x, y)` → cm coordinates
3. Mark bounces from V3, record their table positions
4. Classify each bounce as far/near side of net (y_cm vs 137cm)

**Note**: Ball positions projected when airborne will show "virtual table position" (where the ball's shadow would be). This is useful for trajectory visualization even though the ball is above the table.

## Step 2: `Trajectory3DOverlay.tsx` (NEW SVG)
**File**: `D:/Desktop/poses_viewer/src/components/Trajectory3DOverlay.tsx`

Two parts:

### A. On-video labels
- Next to each ball detection, show `(x, y)cm` in small text
- At bounce points, show larger label: `BOUNCE 45,180cm (near)`
- Color-code: far side bounces = blue, near side = red

### B. Mini top-down table view (corner overlay)
- Fixed-size rectangle (e.g. 200×110px) in bottom-right corner
- Table outline with net line at center
- Ball trajectory as connected dots
- Bounce points as larger circles with side coloring
- Current ball position highlighted
- Scale: table 274×152.5cm mapped to the mini view

**Rendering**: SVG overlay (same pattern as TrajectoryV3Overlay)

## Step 3: Wire up in App.tsx
- Compute homography from table keypoints (predict or marked) — `useMemo`
- Call `computeTrajectory3D(ballYoloFrames, homography, frameIndex)`
- Pass result to `Trajectory3DOverlay`
- Add "3D" checkbox in Trajectory multiselect group

## Files to Create/Modify
| File | Action |
|------|--------|
| `src/utils/trajectoryPipeline3D.ts` | NEW — 3D projection pipeline |
| `src/components/Trajectory3DOverlay.tsx` | NEW — SVG overlay with mini top-down |
| `src/App.tsx` | Add checkbox, compute homography, wire overlay |

## Reuse
- `tableHomography.ts` → `computeHomography()`, `screenToTable()` (already done)
- `trajectoryPipelineV3.ts` → bounce detection via 3-point Y reversal (lines 220-231)
- `TrajectoryV3Overlay.tsx` → SVG rendering patterns
- `TableGridOverlay.tsx` → ball cm label pattern

## Verification
1. Open `table_v1` — has both ball YOLO and table predict data
2. Enable Ball YOLO + "3D" checkbox
3. Check: ball positions show cm coordinates on video
4. Check: bounces labeled with table position and side (far/near)
5. Check: mini top-down view shows ball path on table outline
6. Navigate through frames — current position should track
