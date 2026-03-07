# 🎯 Raw Pose Data Logging

## Що таке Raw Pose Data?

**Raw Pose Data** - це **сирі координати 33 keypoints** безпосередньо з MediaPipe, без обробки.

### Різниця між типами логів:

| Файл | Що логується | Формат | Розмір |
|------|--------------|--------|--------|
| **strokes.jsonl** | Оброблені дані (кути, валідації, помилки) | Processed | ~300 bytes/frame |
| **raw_poses.jsonl** | Сирі координати 33 landmarks | Raw | ~1.5 KB/frame |

---

## Структура даних

### Processed Data (strokes.jsonl)
```json
{
  "type": "stroke_analysis",
  "overall_score": 85,
  "detected_wrist_angle": 180,
  "detected_body_rotation": 50,
  "is_wrist_valid": true,
  "errors": []
}
```

### Raw Pose Data (raw_poses.jsonl)
```json
{
  "type": "raw_pose",
  "session_id": "abc-123",
  "timestamp": 1704268800000,
  "frame_number": 30,
  "inference_time_ms": 15,
  "landmarks": [
    {"x": 0.50, "y": 0.30, "z": -0.50, "visibility": 0.98, "presence": 0.99},  // 0: NOSE
    {"x": 0.51, "y": 0.28, "z": -0.48, "visibility": 0.97, "presence": 0.98},  // 1: LEFT_EYE_INNER
    {"x": 0.52, "y": 0.28, "z": -0.46, "visibility": 0.96, "presence": 0.97},  // 2: LEFT_EYE
    {"x": 0.53, "y": 0.29, "z": -0.44, "visibility": 0.95, "presence": 0.96},  // 3: LEFT_EYE_OUTER
    {"x": 0.48, "y": 0.28, "z": -0.48, "visibility": 0.97, "presence": 0.98},  // 4: RIGHT_EYE_INNER
    {"x": 0.47, "y": 0.28, "z": -0.46, "visibility": 0.96, "presence": 0.97},  // 5: RIGHT_EYE
    {"x": 0.46, "y": 0.29, "z": -0.44, "visibility": 0.95, "presence": 0.96},  // 6: RIGHT_EYE_OUTER
    {"x": 0.54, "y": 0.32, "z": -0.40, "visibility": 0.94, "presence": 0.95},  // 7: LEFT_EAR
    {"x": 0.45, "y": 0.32, "z": -0.40, "visibility": 0.94, "presence": 0.95},  // 8: RIGHT_EAR
    {"x": 0.52, "y": 0.35, "z": -0.35, "visibility": 0.93, "presence": 0.94},  // 9: MOUTH_LEFT
    {"x": 0.48, "y": 0.35, "z": -0.35, "visibility": 0.93, "presence": 0.94},  // 10: MOUTH_RIGHT
    {"x": 0.55, "y": 0.50, "z": -0.30, "visibility": 0.95, "presence": 0.96},  // 11: LEFT_SHOULDER ⭐
    {"x": 0.45, "y": 0.50, "z": -0.30, "visibility": 0.95, "presence": 0.96},  // 12: RIGHT_SHOULDER ⭐
    {"x": 0.58, "y": 0.65, "z": -0.25, "visibility": 0.92, "presence": 0.93},  // 13: LEFT_ELBOW ⭐
    {"x": 0.42, "y": 0.65, "z": -0.25, "visibility": 0.92, "presence": 0.93},  // 14: RIGHT_ELBOW ⭐
    {"x": 0.60, "y": 0.80, "z": -0.20, "visibility": 0.90, "presence": 0.91},  // 15: LEFT_WRIST ⭐
    {"x": 0.40, "y": 0.80, "z": -0.20, "visibility": 0.90, "presence": 0.91},  // 16: RIGHT_WRIST ⭐
    ...
    {"x": 0.52, "y": 0.70, "z": -0.15, "visibility": 0.93, "presence": 0.94},  // 23: LEFT_HIP ⭐
    {"x": 0.48, "y": 0.70, "z": -0.15, "visibility": 0.93, "presence": 0.94}   // 24: RIGHT_HIP ⭐
  ],
  "world_landmarks": [
    {"x": 0.12, "y": -0.15, "z": 0.05, "visibility": 0.98, "presence": 0.99},  // 3D coordinates in meters
    ...
  ]
}
```

---

## Landmark Index Reference

**Важливі для настільного тенісу:**

| Index | Landmark | Ukrainian | Use Case |
|-------|----------|-----------|----------|
| 11 | LEFT_SHOULDER | Ліве плече | Body rotation |
| 12 | RIGHT_SHOULDER | Праве плече | Body rotation |
| 13 | LEFT_ELBOW | Лівий лікоть | Arm position |
| 14 | RIGHT_ELBOW | Правий лікоть | Arm position |
| 15 | LEFT_WRIST | Ліве зап'ястя | Wrist angle |
| 16 | RIGHT_WRIST | Праве зап'ястя | Wrist angle |
| 23 | LEFT_HIP | Ліве стегно | Body rotation |
| 24 | RIGHT_HIP | Праве стегно | Body rotation |

**Повний список 33 landmarks:** [MediaPipe Pose Landmarks](https://developers.google.com/mediapipe/solutions/vision/pose_landmarker#pose_landmarker_model)

---

## Як увімкнути логування

### 1. Відкрити PoseAnalysisProcessor.kt

```kotlin
// Знайти processResults() метод, рядок ~115
// Розкоментувати цей рядок:
logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)
```

### 2. Перекомпілювати додаток

```powershell
.\gradlew installDebug
```

### 3. Запустити тренування

- Відкрити додаток
- Почати тренування
- Виконати удари перед камерою

### 4. Експортувати raw_poses.jsonl

```powershell
.\scripts\quick_export.ps1
```

---

## Структура файлів після увімкнення

```
/data/data/.../files/logs/
├── training_sessions/
│   ├── 2026-01-03_sessions.jsonl   (~200 bytes)
│   └── 2026-01-03_strokes.jsonl    (~9 KB for 30 frames)
├── raw_poses/
│   └── 2026-01-03_raw_poses.jsonl  (~45 KB for 30 frames!) ⚠️
├── performance_metrics/
│   └── 2026-01-03_metrics.jsonl
└── events/
    └── 2026-01-03_events.jsonl
```

**⚠️ Увага:** raw_poses.jsonl **дуже великий** (~1.5 KB на фрейм × 30 FPS = 45 KB/sec)!

---

## Розмір файлів

| FPS | Duration | strokes.jsonl | raw_poses.jsonl |
|-----|----------|---------------|-----------------|
| 30  | 10 sec   | ~90 KB        | ~450 KB         |
| 30  | 1 min    | ~540 KB       | ~2.7 MB         |
| 30  | 5 min    | ~2.7 MB       | ~13.5 MB        |

**Рекомендація:** Увімкнути raw pose logging тільки для:
- Дебагу
- Збору тренувальних даних для ML
- Короткі сесії (<1 хв)

---

## Use Cases для Raw Pose Data

### 1. Machine Learning Dataset
```python
import json
import pandas as pd

# Load raw poses
with open('raw_poses.jsonl', 'r') as f:
    poses = [json.loads(line) for line in f]

# Extract wrist coordinates over time
wrist_x = [pose['landmarks'][16]['x'] for pose in poses]
wrist_y = [pose['landmarks'][16]['y'] for pose in poses]

# Train ML model to predict stroke quality
X = [pose['landmarks'] for pose in poses]
y = [pose['overall_score'] for pose in poses]
```

### 2. Visualization
```python
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation

# Animate skeleton over time
def update(frame):
    pose = poses[frame]
    landmarks = pose['landmarks']
    
    # Plot keypoints
    x = [lm['x'] for lm in landmarks]
    y = [lm['y'] for lm in landmarks]
    plt.scatter(x, y)

ani = FuncAnimation(fig, update, frames=len(poses), interval=33)
```

### 3. Biomechanical Analysis
```python
# Calculate joint velocities
def calculate_velocity(pose1, pose2, dt=0.033):
    wrist1 = pose1['landmarks'][16]
    wrist2 = pose2['landmarks'][16]
    
    dx = wrist2['x'] - wrist1['x']
    dy = wrist2['y'] - wrist1['y']
    
    velocity = np.sqrt(dx**2 + dy**2) / dt
    return velocity

velocities = [calculate_velocity(poses[i], poses[i+1]) for i in range(len(poses)-1)]
max_velocity = max(velocities)
print(f"Max wrist velocity: {max_velocity:.2f} m/s")
```

---

## Coordinates Explanation

### Normalized Coordinates (landmarks)
- **x, y:** Normalized to image size (0.0 - 1.0)
- **z:** Depth relative to hips (meters, can be negative)
- **visibility:** Likelihood landmark is visible [0.0 - 1.0]
- **presence:** Likelihood landmark exists in frame [0.0 - 1.0]

### World Coordinates (world_landmarks)
- **x, y, z:** Real-world 3D coordinates in meters
- Origin: Center of hips
- **x:** Left/right (positive = right)
- **y:** Up/down (positive = up)
- **z:** Forward/backward (positive = forward)

---

## Cleanup Strategy

### Auto-cleanup (default)
Raw poses are deleted after **7 days** automatically.

### Manual cleanup
```powershell
# Delete all raw_poses logs
adb shell "run-as com.ttcoachai rm -rf /data/data/com.ttcoachai/files/logs/raw_poses/*"
```

---

## Performance Impact

| Feature | strokes.jsonl | raw_poses.jsonl |
|---------|---------------|-----------------|
| Latency | < 0.01ms | < 0.01ms |
| Memory | ~50 KB buffer | ~200 KB buffer |
| Storage | ~2 MB/5min | ~13 MB/5min |
| CPU | Negligible | Negligible |

**Висновок:** Raw pose logging має **нульовий вплив на latency** (асинхронний запис), але **займає багато місця**.

---

## Quick Reference

```kotlin
// Enable in PoseAnalysisProcessor.kt line ~115:
logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)

// Export logs:
.\scripts\quick_export.ps1

// Location on device:
/data/data/.../files/logs/raw_poses/2026-01-03_raw_poses.jsonl

// File size:
~1.5 KB per frame × 30 FPS = ~45 KB/sec
```

---

**Updated:** January 3, 2026  
**Status:** ✅ Implemented (disabled by default)  
**Enable by:** Uncomment line in PoseAnalysisProcessor.kt
