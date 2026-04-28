# TT Coach AI

AI-powered table tennis coaching app for Android. Combines pose detection, ball tracking, and audio analysis to provide real-time training feedback.

## Architecture

```
Camera (120fps) → Pose Detection (MediaPipe) → Stroke Analysis → Voice Feedback
                → Ball Detection (YOLOv11)    → Trajectory     → Contact Sync
Video Audio     → Contact Detection (librosa) → Table/Racket Classification
```

## Detection Pipeline

### Ball Detection — YOLOv11-nano (recommended)

Trained on 969 labeled 320x320 crops from TT match videos. Evaluated on IMG_6330 (345 frames, 243 ball-present, 82 no-ball).

| Metric | V5 Regressor | YOLO |
|---|---|---|
| Accuracy | 6.6% | **86.3%** |
| Precision | 10.3% | **89.3%** |
| Recall | 13.6% | **92.6%** |
| F1 | 11.7% | **90.9%** |
| Mean position error | 0.309 | **0.006** |

**Key findings:**
- Must crop frame to top half before inference — full-frame inference drops to 26.8% (ball too small at 320px downscale)
- YOLO confidence threshold 0.25 works well; 24/82 FP on no-ball frames
- Position accuracy is essentially pixel-perfect (median error 0.26% of frame)
- V5 regressor (MobileNetV3-Small) is deprecated — couldn't discriminate ball/no-ball (conf always high)

**Models tried for paddle/table detection:**
- COCO YOLOv11n — only detects persons, TT table/paddle too small/unusual
- YOLO-World (open vocabulary, "table tennis paddle/table") — also only finds persons
- Conclusion: paddle/table detection requires fine-tuning with custom annotations

### Audio Contact Detection

Detects ball-table and ball-racket hits from the audio track using onset detection + spectral classification.

- **Table hits**: lower spectral centroid (1-3 kHz), higher low/high band ratio
- **Racket hits**: higher spectral centroid (3-8 kHz), lower low/high band ratio
- Sensitivity presets: low, medium, high
- Filters: sharpness ratio, energy threshold, silence rejection

### Pose Detection

MediaPipe Pose Landmarker for body tracking. Used for stroke analysis, coaching feedback, and contact filtering (wrist velocity near audio contacts).

## Android App

### Ball Detectors

| Class | Model | Status |
|---|---|---|
| `BallDetector` | OpenCV color/shape | Legacy |
| `BallDetectorV2` | OpenCV improved | Legacy |
| `BallDetectorV3` | Motion + OpenCV | Legacy |
| `BallDetectorV5` | Motion + MobileNetV3 TFLite regressor | Deprecated (6.6% accuracy) |
| `BallDetectorV6` | YOLOv11-nano TFLite + GPU delegate | **Current** (86.3% accuracy) |

### Settings

- **Ball Detection FPS**: 10 / 30 / 60 / 120 (configurable in Settings)
- Default 30 FPS (33ms interval), 120 FPS needs GPU delegate (~8ms budget)
- GPU delegate auto-enabled with CPU fallback

### Build

```bash
./gradlew :app:assembleDebug
```

Requires Android SDK 24+, tested on Samsung Galaxy S23 (Adreno 740 GPU).

## Scripts

All scripts in `scripts/`, videos in `app/src/main/assets/Videos/`.

### YOLO Ball Detector

Requires `trained/best_yolo.pt` (YOLOv11-nano).

```bash
# Detection + JSON export
python scripts/run_ball_yolo.py                                     # all videos
python scripts/run_ball_yolo.py IMG_6330                             # single video
python scripts/run_ball_yolo.py IMG_6330 --evaluate                  # + compare with labels
python scripts/run_ball_yolo.py IMG_6330 --frames 30 50              # frame range
python scripts/run_ball_yolo.py IMG_6330 --region top                # top half (default)
python scripts/run_ball_yolo.py IMG_6330 --region bottom|center|full # other regions

# Debug — annotated frames with bboxes + result montage
python scripts/run_ball_yolo_debug.py IMG_6330                          # ball model, top region
python scripts/run_ball_yolo_debug.py IMG_6330 --frames 30 50           # frame range
python scripts/run_ball_yolo_debug.py IMG_6330 --region full            # full frame
python scripts/run_ball_yolo_debug.py IMG_6330 --coco                   # COCO 80-class model
python scripts/run_ball_yolo_debug.py IMG_6330 --world                  # YOLO-World open vocabulary
```

### Audio Contact Detection

```bash
python scripts/detect_contacts.py app/src/main/assets/Videos/IMG_6330/IMG_6330.MOV
python scripts/detect_contacts.py <video> --sensitivity high           # more contacts
python scripts/detect_contacts.py <video> --interval 33                # 30fps frame mapping

# Filter contacts by wrist velocity from pose data
python scripts/filter_contacts_by_pose.py app/src/main/assets/Videos/IMG_6330
```

### V5 Regressor (legacy)

```bash
python scripts/run_ball_detector_v5.py IMG_6330 --evaluate
python scripts/run_ball_detector_v5_debug_frames.py IMG_6330 --frames 30 50
```

### Training (Google Colab)

Upload `data_regressor.zip` to Google Drive, then run:

- `scripts/train_ball_yolo.ipynb` — YOLOv11-nano (100 epochs, ~10min on T4 GPU)
- `scripts/train_ball_regressor.ipynb` — MobileNetV3-Small regressor (legacy)

Training data: 969 train + 243 val images (320x320 motion crops with center-point labels).

## Poses Viewer

Visualization tool at `../poses_viewer/` (React + Vite).

```bash
cd ../poses_viewer && npm run dev     # http://localhost:5780
```

Overlay toggles:
- **Poses** (blue) — skeleton from `_poses.json`
- **Ball** (yellow) — primary ball detection
- **Ball V5** (cyan) — regressor results from `_ball_v5.json`
- **Ball YOLO** (lime) — YOLO results from `_ball_yolo.json`
- **Contacts** (orange) — audio contacts from `_contacts.json`
- **Labels** (green) — ground truth labels from `_labels.json`

Labeling: click to place corrected ball positions, export training data.

## Data Format

Per-video folder in `app/src/main/assets/Videos/<name>/`:

| File | Content |
|---|---|
| `<name>.MOV` | Source video |
| `<name>_poses.json` | Pose landmarks per frame |
| `<name>_ball_v5.json` | V5 regressor ball detections |
| `<name>_ball_yolo.json` | YOLO ball detections |
| `<name>_contacts.json` | Audio contact events (table/racket) |
| `<name>_labels.json` | Ground truth ball labels (no_ball / corrected position) |

## Trained Models

| File | Model | Size | Use |
|---|---|---|---|
| `trained/best_yolo.pt` | YOLOv11-nano | ~5 MB | Python inference |
| `trained/best_model.pth` | MobileNetV3-Small | ~10 MB | Legacy Python |
| `app/src/main/assets/ball_yolo.tflite` | YOLOv11-nano TFLite | ~5 MB | Android V6 detector |
| `app/src/main/assets/ball_regressor.tflite` | MobileNetV3 TFLite | ~4 MB | Android V5 detector |