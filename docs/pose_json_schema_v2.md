# Pose JSON Schema v2

**Purpose:** Canonical definition of pose JSON schema v2, produced by `scripts/poses/export_poses_rtmpose.py`, consumed by poses_viewer and the future shared KMP loader.

## JSON Example

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
        { "index": 0, "x": 0.6916, "y": 0.3895, "score": 0.93 }
      ]
    }
  ]
}
```

## Field Semantics

- `x`, `y` normalized `[0,1]` (pixel / videoWidth or videoHeight), rounded to **4 decimals** (1e-4 ≈ 0.13 px at 1280 px — far below model noise; legacy v1 used 8 decimals, v2 deliberately trims JSON size).
- `score` = raw model keypoint confidence `[0,1]`, rounded to 4 decimals. No `z`, no `visibility`/`presence` fields in v2.
- A frame with no detected person has `"landmarks": []`.
- When multiple people are detected, the exporter keeps only the person with the highest mean keypoint score.
- `exportTimestamp` = milliseconds since epoch.
- Top-level metadata fields (`videoName`, `intervalMs`, `totalFrames`, `videoDurationMs`, `videoWidth`, `videoHeight`) have the same meaning as in v1.

## COCO-17 Keypoint Indices

| Index | Name |
|-------|------|
| 0 | nose |
| 1 | left_eye |
| 2 | right_eye |
| 3 | left_ear |
| 4 | right_ear |
| 5 | left_shoulder |
| 6 | right_shoulder |
| 7 | left_elbow |
| 8 | right_elbow |
| 9 | left_wrist |
| 10 | right_wrist |
| 11 | left_hip |
| 12 | right_hip |
| 13 | left_knee |
| 14 | right_knee |
| 15 | left_ankle |
| 16 | right_ankle |

## Halpe26 Topology (`--feet`)

`scripts/poses/export_poses_rtmpose.py --feet` uses the RTMPose-m Halpe26 model and emits `"topology": "halpe26"`, `"model": "rtmpose-m-halpe26"` with 26 landmarks per detected frame. Indices 0–16 are identical to COCO-17 above; the additions:

| Index | Name |
|-------|------|
| 17 | head |
| 18 | neck |
| 19 | hip (mid) |
| 20 | left_big_toe |
| 21 | right_big_toe |
| 22 | left_small_toe |
| 23 | right_small_toe |
| 24 | left_heel |
| 25 | right_heel |

All 26 points are exported (standard Halpe26 ordering — consumers index positionally), but poses_viewer intentionally does not render head/neck/hip-mid (17–19); only the foot points extend the drawn skeleton.

## Compatibility

- **v1 (legacy MediaPipe-33):** The legacy MediaPipe-33 format (`x/y/z/visibility/presence` per landmark, no `schemaVersion` field) remains valid and is not converted.
- **Consumer dispatch:** Consumers (poses_viewer now, the KMP loader in Phase 2) must dispatch on `topology`, defaulting to `mediapipe33` when the field is absent.
