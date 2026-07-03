---
name: rtmpose-export
description: Use when exporting pose keypoints from a video — running RTMPose, generating *_poses_rtm.json, schema-v2 pose JSON, COCO-17/Halpe26 keypoints, or when a new clip lands in Videos/.
---

# RTMPose pose export (video → schema-v2 JSON)

## Command

Always use the repo venv — **not** system `python3`, and never `pip install` fresh (rtmlib, onnxruntime, opencv are already in `.venv`):

```bash
.venv/bin/python scripts/poses/export_poses_rtmpose.py Videos/<base>/<base>.mp4
```

Flags:
- `--feet` — Halpe26 topology (26 kp: COCO-17 + head/neck/hip-mid + 6 foot). Without it: COCO-17.
- `--interval 100` — sampling interval ms (100 is the default and what all existing exports use).
- `--out-dir <dir>` — override output dir (default: next to the video).

## Output

`Videos/<base>/<base>_poses_rtm.json` — pose JSON **schema v2** (contract: [docs/pose_json_schema_v2.md](../../../docs/pose_json_schema_v2.md)):
- `schemaVersion: 2`, `topology: "coco17" | "halpe26"`, `model`, `intervalMs`, `videoWidth/Height`
- frames: `landmarks[].{index, x, y, score}`, normalized `x/videoWidth`, `y/videoHeight`
- Empty `landmarks` = no person detected in that frame. **No z coordinate — RTMPose is 2D.**

Videos live in per-clip dirs: `Videos/<base>/<base>.mp4`. If given a bare video file, create the dir first so poses_viewer can find it.

## Verify

1. Sanity: `python3 -c "import json; d=json.load(open('Videos/<base>/<base>_poses_rtm.json')); print(d['topology'], d['totalFrames'])"`
2. Visual QA in poses_viewer — REQUIRED before using the export for anything (see the `viewer-qa` skill).

## Common mistakes

| Mistake | Reality |
|---------|---------|
| `python3 scripts/...` or `pip install rtmlib` | Use `.venv/bin/python`; deps already installed |
| Expecting MediaPipe-33 layout | Schema v2 is COCO-17/Halpe26, `x,y,score` — no z, no visibility |
| Changing the JSON shape in the script | The schema is a 3-consumer contract: update [docs/pose_json_schema_v2.md](../../../docs/pose_json_schema_v2.md), the KMP parser, and poses_viewer together |
