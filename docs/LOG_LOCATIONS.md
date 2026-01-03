# 📊 Де знаходяться логи з pose detection

## 🎯 Швидка відповідь

Логи зберігаються **на Android пристрої** в форматі **JSONL** (JSON Lines):

```
/data/data/com.google.mediapipe.examples.poselandmarker/files/logs/
├── training_sessions/
│   ├── 2026-01-03_sessions.jsonl  (start/end тренувань)
│   └── 2026-01-03_strokes.jsonl   (аналіз кожного удару) ⚠️ створюється при логуванні
├── performance_metrics/
│   └── 2026-01-03_metrics.jsonl   (FPS, inference time)
├── errors/
│   └── 2026-01-03_errors.jsonl    (помилки)
└── events/
    └── 2026-01-03_events.jsonl    (загальні події)
```

## 📥 Як отримати логи

### Метод 1: Скрипт PowerShell (рекомендовано)

```powershell
# Перегляд логів (без завантаження)
.\scripts\view_logs.ps1

# Експорт всіх логів на комп'ютер
.\scripts\export_logs.ps1
```

### Метод 2: Вручну через ADB

```powershell
# 1. Переглянути список файлів
adb shell "run-as com.google.mediapipe.examples.poselandmarker ls -la /data/data/com.google.mediapipe.examples.poselandmarker/files/logs/training_sessions/"

# 2. Витягнути конкретний файл
adb exec-out "run-as com.google.mediapipe.examples.poselandmarker cat /data/data/com.google.mediapipe.examples.poselandmarker/files/logs/training_sessions/2026-01-03_strokes.jsonl" > strokes.json

# 3. Експортувати всю папку (швидко)
$exportDir = "d:\Desktop\TT_Coach_AI\logs_export"
New-Item -ItemType Directory -Path $exportDir -Force
adb exec-out "run-as com.google.mediapipe.examples.poselandmarker cat /data/data/com.google.mediapipe.examples.poselandmarker/files/logs/training_sessions/2026-01-03_sessions.jsonl" > "$exportDir\sessions.jsonl"
```

### Метод 3: Через додаток (TODO - майбутня функція)

В Settings → Debug → Export Logs буде кнопка для експорту логів на email/cloud.

## 📄 Формат файлів

### JSONL (JSON Lines)
Кожний рядок - окремий JSON об'єкт:

```jsonl
{"type":"training_session","session_id":"abc-123","exercise_id":"forehand_drive","start_time":1767451716822}
{"type":"stroke_analysis","session_id":"abc-123","frame":1,"wrist_angle":180,"score":95}
{"type":"stroke_analysis","session_id":"abc-123","frame":2,"wrist_angle":175,"score":90}
```

### Приклад stroke analysis
```json
{
  "type": "stroke_analysis",
  "session_id": "d1528d34-8a33-4995-88c5-a7374e7bf5ae",
  "timestamp": 1767451716822,
  "frame_number": 123,
  "inference_time_ms": 15,
  "result": {
    "phase": "CONTACT",
    "overall_score": 85,
    "is_successful": true,
    "detected_wrist_angle": 180,
    "detected_body_rotation": 50,
    "detected_follow_through": 120,
    "detected_contact_height": 0.85,
    "detected_elbow_distance": 0.25,
    "is_wrist_valid": true,
    "is_rotation_valid": true,
    "is_follow_through_valid": true,
    "is_contact_height_valid": true,
    "is_elbow_distance_valid": true,
    "errors": [],
    "recommendations": []
  }
}
```

## 🔍 Як аналізувати логи

### 1. Відкрити в VS Code
```powershell
code d:\Desktop\TT_Coach_AI\logs_export
```

### 2. Конвертувати JSONL в масив JSON
```powershell
# PowerShell
Get-Content .\strokes.jsonl | ConvertFrom-Json | ConvertTo-Json -Depth 10 > strokes_pretty.json
```

### 3. Аналіз в Python
```python
import json

# Читання JSONL
strokes = []
with open('strokes.jsonl', 'r') as f:
    for line in f:
        strokes.append(json.loads(line))

# Статистика
print(f"Total strokes: {len(strokes)}")
print(f"Average score: {sum(s['result']['overall_score'] for s in strokes) / len(strokes)}")

# Фільтрація помилок
errors = [s for s in strokes if not s['result']['is_successful']]
print(f"Strokes with errors: {len(errors)}")
```

### 4. Візуалізація в Jupyter
```python
import pandas as pd
import matplotlib.pyplot as plt

# Завантажити дані
df = pd.read_json('strokes.jsonl', lines=True)

# Графік рахунку по часу
plt.plot(df['frame_number'], df['result'].apply(lambda x: x['overall_score']))
plt.xlabel('Frame')
plt.ylabel('Score')
plt.title('Stroke Quality Over Time')
plt.show()
```

## 🛠️ Налагодження

### Логи не створюються?

1. **Перевірте чи додаток запущено:**
```powershell
adb shell "ps -A | grep poselandmarker"
```

2. **Перевірте дозволи:**
```powershell
adb shell "run-as com.google.mediapipe.examples.poselandmarker ls -la /data/data/com.google.mediapipe.examples.poselandmarker/files/"
```

3. **Подивіться logcat для помилок:**
```powershell
adb logcat -s AsyncFileLogger:* LocalFileLogger:* PoseAnalysisProcessor:*
```

### Файл strokes.jsonl не існує?

Це нормально якщо:
- Тренування ще не запускалось
- Pose detection не виявив жодного удару
- Аналіз не був викликаний

Перевірте чи є записи в `events.jsonl` та `sessions.jsonl`.

### Занадто багато логів (диск заповнюється)?

Логи автоматично видаляються через **7 днів**. Можна вручну очистити:

```powershell
# Видалити всі логи
adb shell "run-as com.google.mediapipe.examples.poselandmarker rm -rf /data/data/com.google.mediapipe.examples.poselandmarker/files/logs/*"

# Або через додаток: Settings → Debug → Clear Logs
```

## 📊 Метрики продуктивності

Логування має **нульовий вплив на latency**:
- Запис в чергу: **< 0.01 ms**
- Flush на диск: асинхронно кожні 5 секунд
- Буфер: 50 подій

Детальніше: [`app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/logging/README.md`](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/logging/README.md)

## 🔗 Пов'язані файли

- **Система логування:** [`AsyncFileLogger.kt`](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/logging/AsyncFileLogger.kt)
- **Адаптер:** [`LocalFileLogger.kt`](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/core/logging/providers/LocalFileLogger.kt)
- **Процесор:** [`PoseAnalysisProcessor.kt`](../app/src/main/java/com/google/mediapipe/examples/poselandmarker/processors/PoseAnalysisProcessor.kt)
- **Скрипти:** [`scripts/view_logs.ps1`](../scripts/view_logs.ps1), [`scripts/export_logs.ps1`](../scripts/export_logs.ps1)

## ✅ Швидкий чеклист

- [ ] Додаток запущено на Android пристрої
- [ ] ADB підключено (`adb devices`)
- [ ] Виконано хоча б одне тренування
- [ ] Запущено `.\scripts\view_logs.ps1` для перегляду
- [ ] Запущено `.\scripts\export_logs.ps1` для експорту
- [ ] Логи завантажені в `d:\Desktop\TT_Coach_AI\logs_export\`
- [ ] Відкрито в VS Code / Jupyter для аналізу

---

**Оновлено:** 3 січня 2026  
**Статус:** ✅ Логування працює  
**Формат:** JSONL (JSON Lines)  
**Розташування:** `/data/data/com.google.mediapipe.examples.poselandmarker/files/logs/`
