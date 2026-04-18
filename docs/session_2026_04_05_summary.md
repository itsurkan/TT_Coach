# Session Summary — 2026-04-05

## What was accomplished

### 1. Table Keypoint Labeling Tool (poses_viewer)
- Added Table markup mode (T key) — click 6 points (4 corners + 2 net)
- Auto-advances, right-click to reset, Shift+Arrow to skip 100 frames
- Saves to `_table_labels.json`
- Added Dataset Browser — review/delete training samples, search by name, sorted by video

### 2. Training Dataset Preparation
- **TTHQ dataset**: downloaded 19 YouTube videos, ffmpeg-cut to match annotations, parsed CSV annotations
- **Local data**: 10 videos labeled (36 frames)
- Fixed: video cutting, fps mismatch, annotation resolution (1080p vs 720p), per-video keypoint remapping
- Script: `scripts/prepare_table_dataset.py` with `--all`, `--local-only`, `--tthq-only` flags
- Final dataset: 282 samples in YOLO keypoint format

### 3. Model Training (Colab)
- YOLOv11-nano-pose, 6 keypoints
- Multiple iterations fixing keypoint ordering consistency
- Best model: Pose mAP50=98.2%, mAP50-95=91.1% (with kobj=2.0)
- Models: `yolo_table_keypoints/`, `yolo_table_keypoints_predict/`

### 4. Table Detection Scripts
- `scripts/run_table_yolo.py`, `run_table_yolo_predict.py`
- Samples 3 frames, averages keypoints, outputs `_table_yolo_predict.json`
- Runs on all videos in Videos/ folder

### 5. Homography & Visualization
- `tableHomography.ts` — DLT homography: screen pixels <-> table cm coordinates
- Table Grid overlay (SVG) — perspective grid with cm labels
- Grid(Marked) — grid from user's manual labels
- Ball position shown in cm next to ball detection
- All table overlays moved to SVG for crisp rendering

### 6. Keypoint Order
```
pt1=farL, pt2=farR (far edge from camera)
pt3=nearR, pt4=nearL (near edge to camera)
pt5=netL, pt6=netR (net-table intersections)
```

### 7. Video Organization
- Renamed `video_2026-03-31_*` to `table_v1` through `table_v7`
- Each in own folder following `{name}/{name}.mp4` pattern

## Key Files Created
| File | Purpose |
|------|---------|
| `scripts/prepare_table_dataset.py` | Build dataset from local + TTHQ |
| `scripts/run_table_yolo_predict.py` | Run table detection on videos |
| `scripts/download_tthq_videos.py` | Download TTHQ videos from YouTube |
| `scripts/train_table_keypoints.ipynb` | Colab training notebook |
| `poses_viewer/src/utils/tableHomography.ts` | Homography math |
| `poses_viewer/src/components/TableGridOverlay.tsx` | SVG grid overlay |
| `poses_viewer/src/components/TableDetectOverlay.tsx` | SVG table detection overlay |
| `poses_viewer/src/components/TableLabelPanel.tsx` | Table labeling UI |
| `poses_viewer/src/components/DatasetBrowser.tsx` | Dataset review tool |

## Next Step
Ball 2D->3D transform — see `plan_ball_2d_to_3d.md`
