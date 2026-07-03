---
name: visualize-pose
description: Use when you need to SEE a pose — rendering a *_poses_rtm.json skeleton as an image, eyeballing keypoints/bones, checking which frame has a person, or producing a PNG overlay of COCO-17/Halpe26 pose data without opening the React poses_viewer.
---

# Visualize a pose JSON (schema v2 → skeleton PNG)

Turns a schema-v2 pose file into a PNG you can open or Read. Draws the skeleton
over the real video frame (grabbed from the sibling `.mp4` by timestamp) or, if
no video is found, on a blank dark canvas. For quick visual checks and for any
agent that can't drive the browser-based poses_viewer.

## Command

Always use the repo venv (`cv2`/`numpy` already installed — never `pip install`):

```bash
.venv/bin/python .claude/skills/visualize-pose/render_pose.py <pose.json> [options]
```

| Option | Effect |
|--------|--------|
| `--frame N` | render only the pose frame whose `frameIndex == N` |
| `--index I` | render the I-th frame in the file (0-based, default 0) |
| `--all` | render every frame to `<stem>_frame<idx>.png` |
| `--out PATH` | output PNG path (single-frame). Default: `<stem>_render.png` |
| `--no-video` | skeleton on a blank canvas, ignore the video |
| `--min-score F` | confidence gate (default 0.3) — joints/bones below it are dropped |

Output prints the PNG path + `(topology, N keypoints, frameIndex, on video frame|blank canvas)`.

## Read the result

After rendering, **Read the PNG** to see it. The skeleton is colour-coded:
**blue = left** side, **red = right** side, **green = center** (torso/hips),
**yellow dots = joints**. Bones/joints below `--min-score` aren't drawn — a frame
with no person (or a low-confidence detection) renders as just the background.

## How the rendering works (the algorithm, for any AI)

1. Pose JSON is **schema v2**: `landmarks[].{index, x, y, score}`, with `x`,`y`
   normalized — `x = pixel/videoWidth`, `y = pixel/videoHeight`. Pixel coords are
   `x*videoWidth`, `y*videoHeight`. (No z — RTMPose is 2D.)
2. `topology` is `"coco17"` (indices 0–16) or `"halpe26"` (adds 17–25, incl. feet).
   Indices 0–16 are identical between them.
3. **Bones** = fixed index pairs (e.g. `5→7` shoulder→elbow, `7→9` elbow→wrist,
   `11→12` hip↔hip). The full COCO-17 (16 bones) + Halpe26 foot (6 bones) lists
   live in `render_pose.py`; mirror `poses_viewer/src/utils/poseConnections.ts`.
4. **Score gate**: skip any joint or bone endpoint with `score < 0.3`. This is
   intentional — don't lower it to force a skeleton onto a no-person frame.

## Common mistakes

| Mistake | Reality |
|---------|---------|
| Empty/background-only PNG = "broken" | Frame has no person or low scores; render a higher-confidence `--frame` (find one: max mean keypoint score) |
| Drawing on raw normalized coords | Multiply by `videoWidth`/`videoHeight` first; x and y use *different* axes |
| `python3 …` / `pip install opencv` | Use `.venv/bin/python`; deps are already there |
| Feeding a legacy v1 (MediaPipe-33) JSON | Renderer exits — it requires `schemaVersion: 2` (`*_poses_rtm.json`) |
| Expecting an exact frame | Video frame is grabbed by `timestampMs` (nearest), good enough for visual QA |

For interactive frame-by-frame inspection over the video instead, use the React
`poses_viewer` (see the `viewer-qa` skill).
