# Phase 1 — Desktop Pose Pipeline (RTMPose) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export COCO-17 pose JSON (schema v2) from videos via RTMPose-m on the Mac, and render it in poses_viewer alongside the legacy MediaPipe-33 format.

**Architecture:** A new Python export script (`scripts/poses/export_poses_rtmpose.py`) mirrors the existing `export_poses.py` CLI/JSON conventions but emits schema v2 (`topology: "coco17"`, landmarks with `x/y/score`). poses_viewer's pose-JSON normalization is extracted into a testable util, taught to detect topology, and the skeleton renderer picks the edge list per topology.

**Tech Stack:** Python 3.13 (`.venv`), **rtmlib + onnxruntime** (runs official RTMPose-m ONNX; full MMPose rejected — `mmcv` has no Python 3.13 wheels and is painful on macOS arm64), opencv-python, React/TypeScript/vitest in poses_viewer.

**Spec deviation (accepted):** rtmlib's `Body(mode='balanced')` pairs RTMPose-m with a **YOLOX** person detector instead of RTMDet-nano. The detector choice doesn't affect the keypoint output schema; RTMDet-nano matters only for the Android/ncnn port (Phase 3). Noted here so nobody "fixes" it into an mmcv dependency.

**Schema v2 (canonical definition lives in `docs/pose_json_schema_v2.md`, written in Task 2):**

```json
{
  "schemaVersion": 2,
  "topology": "coco17",
  "model": "rtmpose-m",
  "videoName": "video_2.mp4",
  "intervalMs": 100,
  "totalFrames": 72,
  "videoDurationMs": 7115,
  "videoWidth": 712,
  "videoHeight": 1280,
  "exportTimestamp": 1780000000000,
  "frames": [
    {
      "frameIndex": 0,
      "timestampMs": 0,
      "landmarks": [
        { "index": 0, "x": 0.69163567, "y": 0.38953972, "score": 0.93 }
      ]
    }
  ]
}
```

- `x`, `y` normalized `[0,1]` (pixel / videoWidth|videoHeight), rounded to 8 decimals — same convention as legacy, so the viewer's `lm.x * CANVAS_W` scaling works unchanged.
- `score` = raw model keypoint confidence `[0,1]`. No `z`, no `visibility`/`presence`.
- Legacy files (no `schemaVersion`/`topology`) are implicitly v1 / `mediapipe33` and stay valid.
- COCO-17 indices: 0 nose, 1 l-eye, 2 r-eye, 3 l-ear, 4 r-ear, 5 l-shoulder, 6 r-shoulder, 7 l-elbow, 8 r-elbow, 9 l-wrist, 10 r-wrist, 11 l-hip, 12 r-hip, 13 l-knee, 14 r-knee, 15 l-ankle, 16 r-ankle.

**Testing note:** `scripts/` has no Python test infra (each script is a thin I/O wrapper; convention is inline-documented deps + manual verification — see `export_poses.py`). Python tasks verify via exact commands with expected output. All testable logic added to poses_viewer is TDD'd with vitest.

---

### Task 1: Python environment + rtmlib smoke test

**Files:** none created (env only)

- [ ] **Step 1: Install dependencies into the project venv**

```bash
cd /Users/itsurkan/Dev/personal/TT_Coach
source .venv/bin/activate
pip install rtmlib onnxruntime opencv-python
```

Expected: all three install cleanly on Python 3.13 (onnxruntime ≥1.20 has 3.13 wheels). If `rtmlib` pulls a conflicting opencv, let pip resolve — any opencv ≥4.x works.

- [ ] **Step 2: Smoke-test RTMPose-m on one frame of a real video**

```bash
python - <<'EOF'
import cv2
from rtmlib import Body
cap = cv2.VideoCapture('Videos/video_2/video_2.mp4')
cap.set(cv2.CAP_PROP_POS_MSEC, 1000)
ok, frame = cap.read()
assert ok, 'could not read frame'
body = Body(mode='balanced', backend='onnxruntime', device='cpu')
keypoints, scores = body(frame)
print('keypoints', keypoints.shape, 'scores', scores.shape)
print('first kpt (px):', keypoints[0][0], 'score:', scores[0][0])
EOF
```

Expected: first run downloads two ONNX models (a YOLOX detector and an RTMPose-m pose model — URLs printed by rtmlib), then prints `keypoints (N, 17, 2) scores (N, 17)` where N ≥ 1. Keypoints are **pixels**, not normalized — the export script divides by width/height.

If the printed pose-model URL is not an `rtmpose-m` variant, check rtmlib's mode table (`python -c "import rtmlib, inspect; print(inspect.getsource(rtmlib.Body))"`) and pass explicit `pose=<rtmpose-m onnx url>` / `pose_input_size` instead of `mode`. Record what was used in the script's docstring in Task 3.

- [ ] **Step 3: Note where models were cached**

rtmlib caches downloads (typically under `~/.cache/rtmlib` — the download log prints the path). No commit for this task (no repo changes).

---

### Task 2: Schema v2 document

**Files:**
- Create: `docs/pose_json_schema_v2.md`

- [ ] **Step 1: Write the schema doc**

Content: the exact JSON example, field semantics, and COCO-17 index table from this plan's header (copy them verbatim), plus two sentences: (a) v1 = legacy MediaPipe-33 (`x/y/z/visibility/presence`, no `schemaVersion` field) remains valid and unconverted; (b) consumers (poses_viewer now, the KMP loader in Phase 2) must dispatch on `topology`, defaulting to `mediapipe33` when absent.

- [ ] **Step 2: Commit**

```bash
git add docs/pose_json_schema_v2.md
git commit -m "docs: define pose JSON schema v2 (coco17 topology)"
```

---

### Task 3: `export_poses_rtmpose.py`

**Files:**
- Create: `scripts/poses/export_poses_rtmpose.py`
- Reference (conventions to mirror): `scripts/poses/export_poses.py`

- [ ] **Step 1: Write the script**

```python
#!/usr/bin/env python3
"""
export_poses_rtmpose.py

Runs RTMPose-m (via rtmlib + ONNX Runtime) on a video file and exports a
*_poses_rtm.json with COCO-17 keypoints — pose JSON schema v2.
Schema reference: docs/pose_json_schema_v2.md

Usage:
    python scripts/poses/export_poses_rtmpose.py <video_path> [--interval 100] [--out-dir <dir>]

Output:
    <video_name>_poses_rtm.json  written next to the video (or to --out-dir)

Requirements:
    pip install rtmlib onnxruntime opencv-python
"""

import argparse
import json
import os
import sys
import time

try:
    import cv2
except ImportError:
    print("ERROR: opencv-python not installed. Run: pip install opencv-python", file=sys.stderr)
    sys.exit(1)

try:
    import numpy as np
    from rtmlib import Body
except ImportError:
    print("ERROR: rtmlib not installed. Run: pip install rtmlib onnxruntime", file=sys.stderr)
    sys.exit(1)

SCHEMA_VERSION = 2
TOPOLOGY = "coco17"
MODEL_NAME = "rtmpose-m"
NUM_KEYPOINTS = 17


def best_person(keypoints, scores):
    """Pick the detection with the highest mean keypoint score (single-player videos)."""
    if keypoints is None or len(keypoints) == 0:
        return None, None
    idx = int(np.argmax(scores.mean(axis=1)))
    return keypoints[idx], scores[idx]


def export_poses(video_path: str, interval_ms: int, out_dir: str | None) -> str:
    video_name = os.path.basename(video_path)
    base = video_name.rsplit(".", 1)[0]

    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {video_path}", file=sys.stderr)
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration_ms = int(frame_count / fps * 1000)
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    print(f"Video: {video_name}  {width}x{height}  {duration_ms} ms  ({fps:.1f} fps)")

    body = Body(mode="balanced", backend="onnxruntime", device="cpu")

    frames = []
    frame_index = 0
    pos_ms = 0

    while pos_ms <= duration_ms:
        cap.set(cv2.CAP_PROP_POS_MSEC, pos_ms)
        ret, frame = cap.read()
        if not ret:
            break

        keypoints, scores = body(frame)  # rtmlib takes BGR (cv2) frames; returns pixel coords
        person_kpts, person_scores = best_person(keypoints, scores)

        landmarks = []
        if person_kpts is not None:
            for i in range(NUM_KEYPOINTS):
                landmarks.append({
                    "index": i,
                    "x": round(float(person_kpts[i][0]) / width, 8),
                    "y": round(float(person_kpts[i][1]) / height, 8),
                    "score": round(float(person_scores[i]), 8),
                })

        frames.append({
            "frameIndex": frame_index,
            "timestampMs": pos_ms,
            "landmarks": landmarks,
        })

        if frame_index % 10 == 0:
            print(f"  frame {frame_index:3d}  t={pos_ms:6d} ms  landmarks={len(landmarks)}")

        frame_index += 1
        pos_ms += interval_ms

    cap.release()

    data = {
        "schemaVersion":   SCHEMA_VERSION,
        "topology":        TOPOLOGY,
        "model":           MODEL_NAME,
        "videoName":       video_name,
        "intervalMs":      interval_ms,
        "totalFrames":     frame_index,
        "videoDurationMs": duration_ms,
        "videoWidth":      width,
        "videoHeight":     height,
        "exportTimestamp": int(time.time() * 1000),
        "frames":          frames,
    }

    dest_dir = out_dir if out_dir else os.path.dirname(os.path.abspath(video_path))
    out_path = os.path.join(dest_dir, base + "_poses_rtm.json")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    n_detected = sum(1 for fr in frames if fr["landmarks"])
    print(f"\n-> {out_path}")
    print(f"  {frame_index} frames, {n_detected} with pose detected")
    return out_path


def main():
    parser = argparse.ArgumentParser(description="Export RTMPose COCO-17 detections from a video (schema v2).")
    parser.add_argument("video", help="Path to the input video file")
    parser.add_argument("--interval", type=int, default=100,
                        help="Sampling interval in milliseconds (default: 100)")
    parser.add_argument("--out-dir", default=None,
                        help="Output directory (default: same folder as video)")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        print(f"ERROR: file not found: {args.video}", file=sys.stderr)
        sys.exit(1)

    export_poses(args.video, args.interval, args.out_dir)


if __name__ == "__main__":
    main()
```

(If Task 1 Step 2 required explicit model URLs instead of `mode="balanced"`, apply the same here and document it in the docstring.)

- [ ] **Step 2: Run it on the reference video**

```bash
source .venv/bin/activate
python scripts/poses/export_poses_rtmpose.py Videos/video_2/video_2.mp4
```

Expected: progress lines every 10 frames, then `-> .../Videos/video_2/video_2_poses_rtm.json` with ~72 frames, most with `landmarks=17`.

- [ ] **Step 3: Validate the output JSON**

```bash
python - <<'EOF'
import json
d = json.load(open('Videos/video_2/video_2_poses_rtm.json'))
assert d['schemaVersion'] == 2 and d['topology'] == 'coco17'
detected = [f for f in d['frames'] if f['landmarks']]
assert detected, 'no frames with pose'
for f in detected:
    assert len(f['landmarks']) == 17
    for lm in f['landmarks']:
        assert set(lm) == {'index', 'x', 'y', 'score'}
        assert -0.5 <= lm['x'] <= 1.5 and -0.5 <= lm['y'] <= 1.5  # near-frame tolerance
print(f"OK: {len(detected)}/{len(d['frames'])} frames detected")
EOF
```

Expected: `OK: <n>/<total> frames detected` with n close to total.

- [ ] **Step 4: Commit**

```bash
git add scripts/poses/export_poses_rtmpose.py
git commit -m "feat(scripts): add RTMPose COCO-17 video export (schema v2)"
```

Note: `Videos/` JSON artifacts follow whatever the repo already does with them (they appear committed for some videos) — commit the script only in this step; the exported JSON can be committed in Task 8 if Ivan wants it as a fixture.

---

### Task 4: Extract poses_viewer normalization into a testable util (no behavior change)

**Files:**
- Create: `poses_viewer/src/utils/normalizePoses.ts`
- Create: `poses_viewer/src/utils/__tests__/normalizePoses.test.ts`
- Modify: `poses_viewer/src/App.tsx:29-118` (delete moved functions, add import)

- [ ] **Step 1: Write the failing test (legacy behavior, against the not-yet-existing module)**

`poses_viewer/src/utils/__tests__/normalizePoses.test.ts`:

```typescript
import { describe, it, expect } from 'vitest'
import { normalizeData } from '../normalizePoses'

const legacyFile = {
  videoName: 'video_2.mp4',
  intervalMs: 100,
  totalFrames: 1,
  videoDurationMs: 100,
  videoWidth: 712,
  videoHeight: 1280,
  exportTimestamp: 123,
  frames: [
    {
      frameIndex: 0,
      timestampMs: 0,
      landmarks: [
        { index: 0, x: 0.5, y: 0.4, z: -0.1, visibility: 0.9, presence: 0.8 },
      ],
    },
  ],
}

describe('normalizeData (legacy v1)', () => {
  it('preserves mediapipe landmark fields', () => {
    const d = normalizeData(legacyFile)
    expect(d.frames).toHaveLength(1)
    const lm = d.frames[0].landmarks[0]
    expect(lm).toEqual({ index: 0, x: 0.5, y: 0.4, z: -0.1, visibility: 0.9, presence: 0.8 })
    expect(d.videoWidth).toBe(712)
  })

  it('accepts tuple landmarks and a top-level frame array', () => {
    const d = normalizeData([{ landmarks: [[0.1, 0.2, 0.3, 0.95, 0.9]] }])
    const lm = d.frames[0].landmarks[0]
    expect(lm.x).toBeCloseTo(0.1)
    expect(lm.visibility).toBeCloseTo(0.95)
  })
})
```

- [ ] **Step 2: Run the test, verify it fails**

```bash
cd poses_viewer && npx vitest run src/utils/__tests__/normalizePoses.test.ts
```

Expected: FAIL — `Cannot find module '../normalizePoses'` (or equivalent resolve error).

- [ ] **Step 3: Create the module by moving code verbatim**

`poses_viewer/src/utils/normalizePoses.ts` — move `toNumber`, `normalizeLandmarks`, `normalizeData` from `App.tsx:29-118` **unchanged**, adding imports/exports:

```typescript
import { Landmark, PosesBallData } from '../types'

function toNumber(value: unknown, fallback = 0): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : fallback
}

export function normalizeLandmarks(input: unknown): Landmark[] {
  // ... body exactly as App.tsx:34-66 ...
}

export function normalizeData(input: unknown): PosesBallData {
  // ... body exactly as App.tsx:68-118 ...
}
```

In `App.tsx`: delete lines 29–118 (`toNumber`, `normalizeLandmarks`, `normalizeData`) and add to the imports:

```typescript
import { normalizeData } from './utils/normalizePoses'
```

(`normalizeData` is called at App.tsx:433, :507, :521 — all keep working via the import. `toNumber` is not used elsewhere in App.tsx; if `tsc` says otherwise, re-export it from the util.)

- [ ] **Step 4: Run tests + typecheck, verify green**

```bash
cd poses_viewer && npx vitest run src/utils/__tests__/normalizePoses.test.ts && npm run build
```

Expected: tests PASS, `tsc -b` clean.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/utils/normalizePoses.ts poses_viewer/src/utils/__tests__/normalizePoses.test.ts poses_viewer/src/App.tsx
git commit -m "refactor(poses_viewer): extract pose JSON normalization into testable util"
```

---

### Task 5: Schema v2 support in normalization + types

**Files:**
- Modify: `poses_viewer/src/types.ts:1-35`
- Modify: `poses_viewer/src/utils/normalizePoses.ts`
- Modify (test): `poses_viewer/src/utils/__tests__/normalizePoses.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `normalizePoses.test.ts`:

```typescript
const v2File = {
  schemaVersion: 2,
  topology: 'coco17',
  model: 'rtmpose-m',
  videoName: 'video_2.mp4',
  intervalMs: 100,
  totalFrames: 1,
  videoDurationMs: 100,
  videoWidth: 712,
  videoHeight: 1280,
  exportTimestamp: 123,
  frames: [
    {
      frameIndex: 0,
      timestampMs: 0,
      landmarks: [{ index: 5, x: 0.31, y: 0.42, score: 0.93 }],
    },
  ],
}

describe('normalizeData (schema v2 / coco17)', () => {
  it('detects coco17 topology', () => {
    expect(normalizeData(v2File).topology).toBe('coco17')
  })

  it('defaults legacy files to mediapipe33', () => {
    expect(normalizeData(legacyFile).topology).toBe('mediapipe33')
  })

  it('maps score to visibility/presence and z to 0', () => {
    const lm = normalizeData(v2File).frames[0].landmarks[0]
    expect(lm).toEqual({ index: 5, x: 0.31, y: 0.42, z: 0, visibility: 0.93, presence: 0.93 })
  })
})
```

- [ ] **Step 2: Run tests, verify the new ones fail**

```bash
cd poses_viewer && npx vitest run src/utils/__tests__/normalizePoses.test.ts
```

Expected: 3 new tests FAIL (`topology` undefined; `visibility` is 1 instead of 0.93).

- [ ] **Step 3: Implement**

`types.ts` — add the topology type and field:

```typescript
export type PoseTopology = 'mediapipe33' | 'coco17'
```

and in `PosesBallData` add the field:

```typescript
export interface PosesBallData {
  videoUri?: string
  videoName?: string
  topology: PoseTopology
  intervalMs: number
  // ... rest unchanged
}
```

`normalizePoses.ts` — in the **object branch** of `normalizeLandmarks` (the `if (item && typeof item === 'object')` block), use `score` as the fallback for `visibility`/`presence`:

```typescript
      if (item && typeof item === 'object') {
        const lm = item as Partial<Landmark> & { score?: number }
        const score = toNumber(lm.score, 1)
        return {
          index: typeof lm.index === 'number' ? lm.index : index,
          x: toNumber(lm.x),
          y: toNumber(lm.y),
          z: toNumber(lm.z),
          visibility: lm.visibility !== undefined ? toNumber(lm.visibility, 1) : score,
          presence: lm.presence !== undefined ? toNumber(lm.presence, 1) : score,
        }
      }
```

In `normalizeData`'s return object, add:

```typescript
    topology: root.topology === 'coco17' ? 'coco17' : 'mediapipe33',
```

- [ ] **Step 4: Run tests + typecheck, verify green**

```bash
cd poses_viewer && npx vitest run && npm run build
```

Expected: all vitest suites PASS (the full run guards against regressions in drill/trajectory tests), `tsc -b` clean.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/types.ts poses_viewer/src/utils/normalizePoses.ts poses_viewer/src/utils/__tests__/normalizePoses.test.ts
git commit -m "feat(poses_viewer): pose JSON schema v2 — coco17 topology detection, score mapping"
```

---

### Task 6: COCO-17 edge list

**Files:**
- Modify: `poses_viewer/src/utils/poseConnections.ts`
- Create: `poses_viewer/src/utils/__tests__/poseConnections.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect } from 'vitest'
import { POSE_CONNECTIONS, COCO17_CONNECTIONS, getConnections } from '../poseConnections'

describe('COCO17_CONNECTIONS', () => {
  it('has 16 edges, all indices within 0..16', () => {
    expect(COCO17_CONNECTIONS).toHaveLength(16)
    for (const [a, b] of COCO17_CONNECTIONS) {
      expect(a).toBeGreaterThanOrEqual(0)
      expect(a).toBeLessThan(17)
      expect(b).toBeGreaterThanOrEqual(0)
      expect(b).toBeLessThan(17)
    }
  })
})

describe('getConnections', () => {
  it('selects edge list by topology', () => {
    expect(getConnections('mediapipe33')).toBe(POSE_CONNECTIONS)
    expect(getConnections('coco17')).toBe(COCO17_CONNECTIONS)
  })
})
```

- [ ] **Step 2: Run it, verify it fails**

```bash
cd poses_viewer && npx vitest run src/utils/__tests__/poseConnections.test.ts
```

Expected: FAIL — `COCO17_CONNECTIONS`/`getConnections` not exported.

- [ ] **Step 3: Implement**

Append to `poseConnections.ts`:

```typescript
import type { PoseTopology } from '../types'

// COCO-17 keypoint indices (RTMPose / schema v2):
//  0=nose  1=l-eye  2=r-eye  3=l-ear  4=r-ear
//  5=l-shoulder  6=r-shoulder  7=l-elbow  8=r-elbow  9=l-wrist  10=r-wrist
// 11=l-hip  12=r-hip  13=l-knee  14=r-knee  15=l-ankle  16=r-ankle
export const COCO17_CONNECTIONS: Connection[] = [
  // Face
  [0, 1, 'left'], [0, 2, 'right'], [1, 3, 'left'], [2, 4, 'right'],
  // Torso
  [5, 6, 'center'], [5, 11, 'left'], [6, 12, 'right'], [11, 12, 'center'],
  // Left arm
  [5, 7, 'left'], [7, 9, 'left'],
  // Right arm
  [6, 8, 'right'], [8, 10, 'right'],
  // Left leg
  [11, 13, 'left'], [13, 15, 'left'],
  // Right leg
  [12, 14, 'right'], [14, 16, 'right'],
]

export function getConnections(topology: PoseTopology): Connection[] {
  return topology === 'coco17' ? COCO17_CONNECTIONS : POSE_CONNECTIONS
}
```

(The `import type` goes at the top of the file, above the existing code.)

- [ ] **Step 4: Run tests, verify green**

```bash
cd poses_viewer && npx vitest run src/utils/__tests__/poseConnections.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/utils/poseConnections.ts poses_viewer/src/utils/__tests__/poseConnections.test.ts
git commit -m "feat(poses_viewer): COCO-17 skeleton edge list + topology-based selection"
```

---### Task 7: Wire topology through PoseCanvas and App

**Files:**
- Modify: `poses_viewer/src/components/PoseCanvas.tsx:5,8-64,108-130`
- Modify: `poses_viewer/src/App.tsx:121-126` (`jsonSuffixes`) and `:1222` (`<PoseCanvas`)
- Modify: `poses_viewer/CLAUDE.md` (file map mentions App.tsx/PoseCanvas behavior)

No new unit test: PoseCanvas is untested canvas code; the logic (edge selection, score mapping) is covered by Tasks 5–6. Verification is the Task 8 visual gate.

- [ ] **Step 1: PoseCanvas — topology prop**

Change the import at `PoseCanvas.tsx:5`:

```typescript
import { getConnections, SIDE_COLORS } from '../utils/poseConnections'
```

Add to imports from types (line 2):

```typescript
import { Frame, FrameLabel, TableFrameLabel, PoseTopology } from '../types'
```

Add to `Props` (after `videoHeight`):

```typescript
  /** Which skeleton edge list to draw. Defaults to legacy MediaPipe-33. */
  topology?: PoseTopology
```

Add `topology = 'mediapipe33'` to the destructured params, and replace the loop header at line 111:

```typescript
      for (const [a, b, side] of getConnections(topology)) {
```

- [ ] **Step 2: App — pass topology, prefer RTMPose JSON when present**

At `App.tsx:1222`, add the prop to the `<PoseCanvas` element:

```tsx
                <PoseCanvas
                  topology={data?.topology ?? 'mediapipe33'}
```

Replace `jsonSuffixes` (App.tsx:121-126) so RTMPose exports win when present (debug tool: newest pipeline first; delete/rename the `_poses_rtm.json` to fall back to MediaPipe):

```typescript
function jsonSuffixes(wantPoses: boolean, wantBall: boolean): string[] {
  if (wantPoses && wantBall)  return ['_poses_ball.json', '_poses_rtm.json', '_poses.json', '_ball.json']
  if (wantPoses && !wantBall) return ['_poses_rtm.json', '_poses.json', '_poses_ball.json']
  if (!wantPoses && wantBall) return ['_ball.json', '_poses_ball.json']
  return ['_poses_ball.json', '_poses.json', '_ball.json']
}
```

- [ ] **Step 3: Typecheck + full test run**

```bash
cd poses_viewer && npx vitest run && npm run build
```

Expected: PASS / clean. (Other `<PoseCanvas` call sites, if any besides line 1222, compile fine — the prop is optional.)

- [ ] **Step 4: Update poses_viewer/CLAUDE.md**

In the `src/App.tsx` section, append one sentence: "Pose JSON: schema v2 (`topology: 'coco17'`, see `docs/pose_json_schema_v2.md`) supported alongside legacy MediaPipe-33; `_poses_rtm.json` preferred over `_poses.json` when present; normalization lives in `src/utils/normalizePoses.ts`."

- [ ] **Step 5: Commit**

```bash
git add poses_viewer/src/components/PoseCanvas.tsx poses_viewer/src/App.tsx poses_viewer/CLAUDE.md
git commit -m "feat(poses_viewer): render COCO-17 skeletons, prefer RTMPose exports"
```

---

### Task 8: End-to-end visual QA (Phase 1 exit gate)

**Files:** none (verification)

- [ ] **Step 1: Ensure the RTMPose export from Task 3 exists**

```bash
ls -la Videos/video_2/video_2_poses_rtm.json
```

- [ ] **Step 2: Run the viewer and inspect**

```bash
cd poses_viewer && npm run dev
```

Open http://localhost:5780/#/main, select `video_2`. Verify:
1. Skeleton renders as COCO-17 (no face mesh edges, single nose–eyes–ears fan; arms/legs/torso connected; left side blue, right side red).
2. Skeleton tracks the player through the swing with no wild joint jumps (RTMPose quality check — this is the actual gate).
3. Selecting a video that has only legacy `_poses.json` still renders the 33-landmark skeleton.

Save a screenshot to `tmp/screenshots/` for the record.

- [ ] **Step 3: Gate decision**

If RTMPose-m skeleton quality on real footage looks at least as stable as the MediaPipe overlay (flip the `_poses_rtm.json` filename away and back to compare) — Phase 1 gate passed; Phase 2 (KMP COCO-17 models + angle math) is unblocked. If not, investigate before Phase 2: try `mode='performance'`, check input resolution, or revisit detector choice.

- [ ] **Step 4: Commit exported JSON as a reference artifact (optional, Ivan's call)**

```bash
git add Videos/video_2/video_2_poses_rtm.json
git commit -m "chore: add RTMPose schema-v2 export for video_2 (reference artifact)"
```
