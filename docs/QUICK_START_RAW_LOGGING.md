# 🎯 Швидкий старт: Raw Pose Logging

## Що таке strokes?

**Stroke (удар)** = один рух ракеткою в настільному тенісі

**strokes.jsonl** = файл з аналізом кожного фрейму (обчислені кути, помилки, рекомендації)

---

## Два типи логування

### 1️⃣ Processed Data (увімкнено за замовчуванням) ✅
**Файл:** `strokes.jsonl`  
**Що:** Оброблені дані - кути, валідації, помилки  
**Розмір:** ~300 bytes/frame  

```json
{
  "detected_wrist_angle": 180,
  "detected_body_rotation": 50,
  "overall_score": 85,
  "errors": []
}
```

### 2️⃣ Raw Pose Data (вимкнено за замовчуванням) ⚪
**Файл:** `raw_poses.jsonl`  
**Що:** Сирі координати 33 landmarks з MediaPipe  
**Розмір:** ~1.5 KB/frame (у 5 разів більше!)

```json
{
  "landmarks": [
    {"x": 0.5, "y": 0.6, "z": -0.3, "visibility": 0.95}, // 0: NOSE
    {"x": 0.55, "y": 0.5, "z": -0.3, "visibility": 0.95}, // 11: LEFT_SHOULDER
    {"x": 0.58, "y": 0.65, "z": -0.25, "visibility": 0.92}, // 13: LEFT_ELBOW
    {"x": 0.60, "y": 0.80, "z": -0.20, "visibility": 0.90}, // 15: LEFT_WRIST
    // ... 29 more landmarks
  ]
}
```

---

## Як увімкнути Raw Pose Logging

### Крок 1: Відкрити файл
[PoseAnalysisProcessor.kt](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/processors/PoseAnalysisProcessor.kt#L115)

### Крок 2: Розкоментувати рядок ~115
```kotlin
// Знайти цей рядок:
// logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)

// Розкоментувати (прибрати //):
logRawPoseLandmarks(poseLandmarkerResult, inferenceTime)
```

### Крок 3: Перекомпілювати
```powershell
.\gradlew installDebug
```

### Крок 4: Експортувати логи
```powershell
.\scripts\quick_export.ps1
```

**Результат:**
```
logs_export/
├── sessions.jsonl
├── strokes.jsonl       ← Processed data
└── raw_poses.jsonl     ← Raw landmarks ✅ NEW!
```

---

## Що логується в raw_poses.jsonl?

### 33 Landmarks (keypoints)
- 0: NOSE
- 1-10: Eyes, ears, mouth
- **11-12: SHOULDERS** ⭐ (body rotation)
- **13-14: ELBOWS** ⭐ (arm position)
- **15-16: WRISTS** ⭐ (wrist angle)
- 17-22: Hands (fingers)
- **23-24: HIPS** ⭐ (body rotation)
- 25-32: Legs, feet, ankles

**Важливі для настільного тенісу:** 11, 12, 13, 14, 15, 16, 23, 24

---

## Використання Raw Pose Data

### Python: Аналіз руху
```python
import json

# Load raw poses
with open('raw_poses.jsonl', 'r') as f:
    poses = [json.loads(line) for line in f]

# Get wrist trajectory
wrist_trajectory = [
    (pose['landmarks'][16]['x'], pose['landmarks'][16]['y'])
    for pose in poses
]

print(f"Total frames: {len(poses)}")
print(f"Wrist start: {wrist_trajectory[0]}")
print(f"Wrist end: {wrist_trajectory[-1]}")
```

### Python: Візуалізація скелета
```python
import matplotlib.pyplot as plt

pose = poses[0]  # First frame
landmarks = pose['landmarks']

# Plot all keypoints
x = [lm['x'] for lm in landmarks]
y = [lm['y'] for lm in landmarks]

plt.scatter(x, y)
plt.title('Pose Skeleton')
plt.show()
```

---

## ⚠️ Застереження

### Розмір файлів
| Duration | strokes.jsonl | raw_poses.jsonl |
|----------|---------------|-----------------|
| 10 sec   | ~90 KB        | ~450 KB         |
| 1 min    | ~540 KB       | ~2.7 MB         |
| 5 min    | ~2.7 MB       | **~13.5 MB**    |

### Рекомендації
- ✅ Використовуй для коротких сесій (<1 хв)
- ✅ Вимикай після збору даних
- ✅ Видаляй старі файли вручну якщо потрібно
- ❌ Не залишай увімкненим постійно

---

## Швидка довідка

```bash
# 1. Enable logging
# Uncomment in PoseAnalysisProcessor.kt line 115

# 2. Build and install
.\gradlew installDebug

# 3. Run training on device
# (Open app → Start training → Make strokes)

# 4. Export logs
.\scripts\quick_export.ps1

# 5. Analyze in Python
python analyze_poses.py
```

---

**Детальна документація:** [RAW_POSE_LOGGING.md](RAW_POSE_LOGGING.md)
