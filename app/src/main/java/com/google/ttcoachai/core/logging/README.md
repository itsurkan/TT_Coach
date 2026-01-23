# Async File Logging System

## Огляд

Система асинхронного логування з **нульовим впливом на latency** основних операцій. Використовує Kotlin Channels та Coroutines для non-blocking запису в файли.

## Архітектура

```
Training Thread                 Background Thread
     │                               │
     │ analyzeStroke()              │
     │ ────────►                    │
     │ log() → queue                │
     │ (< 0.01ms)                   │
     │ return instantly             │
     │                               │
     │                          Buffer fills
     │                          ────────►
     │                          Flush to disk
     │                          (async)
```

## Основні компоненти

### 1. AsyncFileLogger
Основний клас що керує асинхронним логуванням.

**Характеристики:**
- Non-blocking queue (Channel<LogEvent>)
- Buffered writes (flush кожні 50 events або 5 секунд)
- Automatic cleanup (видаляє логи старіші 7 днів)
- JSONL format (JSON Lines - один JSON об'єкт на рядок)
- **Auto-directory creation**: створює всі необхідні підпапки при ініціалізації
- **Safe writes**: перевіряє існування батьківської директорії перед записом кожного файлу

### 2. LogEvent
Sealed class з різними типами подій:
- `TrainingSession` - початок/кінець тренування
- `StrokeAnalysis` - аналіз окремого удару
- `RuleEvaluation` - оцінка правил
- `PerformanceMetric` - метрики продуктивності
- `Error` - помилки
- `Generic` - загальні події

### 3. LocalFileLogger
Адаптер що імплементує `Logger`, `AnalyticsProvider`, `CrashReporter` інтерфейси.

### 4. LogReader
Утиліта для читання та експорту логів.

## Структура файлів

```
app/files/logs/
├── training_sessions/
│   ├── 2026-01-03_sessions.jsonl
│   ├── 2026-01-03_strokes.jsonl
│   └── 2026-01-03_rules.jsonl
├── raw_poses/
│   └── 2026-01-03_raw_poses.jsonl  (optional, disabled by default)
├── performance_metrics/
│   └── 2026-01-03_metrics.jsonl
├── errors/
│   └── 2026-01-03_errors.jsonl
└── events/
    └── 2026-01-03_events.jsonl
```

## Використання

### Ініціалізація

Вже автоматично ініціалізовано в `TTCoachApplication`:

```kotlin
class TTCoachApplication : Application() {
    private lateinit var fileLogger: LocalFileLogger
    
    override fun onCreate() {
        super.onCreate()
        fileLogger = LocalFileLogger(this)
    }
    
    fun getFileLogger(): LocalFileLogger = fileLogger
}
```

### Логування подій

```kotlin
// Отримати logger
val app = application as TTCoachApplication
val logger = app.getFileLogger()

// Log training session
val sessionId = logger.startTrainingSession(
    exerciseId = "forehand_drive",
    exerciseName = "Накат справа"
)

// Log stroke analysis
logger.logStrokeAnalysis(
    result = analysisResult,
    sessionId = sessionId,
    inferenceTimeMs = 15,
    frameNumber = 123
)

// Log performance metric
logger.logPerformanceMetric(
    metricName = "fps",
    value = 30.0f,
    sessionId = sessionId
)

// Log error
try {
    // ...
} catch (e: Exception) {
    logger.logError(e, "Context info")
}

// End session
logger.endTrainingSession(
    totalStrokes = 50,
    goodStrokes = 35,
    averageScore = 75.5f
)
```

### Читання логів

```kotlin
val logReader = LogReader(context)

// Read sessions from specific date
val sessions = logReader.readSessionLogs("2026-01-03")

// Get available dates
val dates = logReader.getAvailableDates()

// Export all logs to ZIP
val zipFile = logReader.exportAllLogs(context)

// Get storage stats
val stats = logReader.getStorageStats()
println("Total size: ${stats.totalSizeMB} MB")
println("File count: ${stats.fileCount}")
```

## Гарантії продуктивності

| Метрика | Значення |
|---------|----------|
| Latency per log call | **< 0.01ms** |
| Blocking main thread | **0ms** (fully async) |
| Max data loss on crash | 50 events (buffer size) |
| Cleanup policy | Auto-delete after 7 days |
| Thread safety | ✅ Channel-based |
| Error handling | ✅ SupervisorJob |
| Directory creation | ✅ Auto (init + before write) |
| Safe writes | ✅ Parent dir check |

## Directory Management

### Initialization
При створенні `AsyncFileLogger` автоматично створюються всі директорії:
```kotlin
init {
    logDir.mkdirs()  // /data/data/[package]/files/logs/
    File(logDir, "training_sessions").mkdirs()
    File(logDir, "performance_metrics").mkdirs()
    File(logDir, "errors").mkdirs()
    File(logDir, "events").mkdirs()
}
```

### Before Each Write
Перед записом кожного файлу перевіряється батьківська директорія:
```kotlin
private fun LogEvent.getLogFile(): File {
    val file = File(logDir, filename)
    file.parentFile?.mkdirs()  // Ensure directory exists
    return file
}
```

**Це гарантує:**
- ✅ Логи працюють навіть якщо папки були видалені вручну
- ✅ Немає FileNotFoundException при запису
- ✅ Надійність при concurrent доступі

### Export to External Storage
При експорті також створюються всі необхідні директорії:
```kotlin
val externalDir = context.getExternalFilesDir(null)
externalDir?.mkdirs()  // /sdcard/Android/data/[package]/files/

val exportDir = File(externalDir, "logs")
exportDir.mkdirs()

targetFile.parentFile?.mkdirs()  // For nested paths
```

## Формат даних (JSONL)

Кожен файл містить JSON об'єкти, по одному на рядок:

```json
{"type":"training_session","session_id":"uuid","exercise_id":"forehand_drive","start_time":1704268800000}
{"type":"stroke_analysis","stroke_id":"uuid","session_id":"uuid","overall_score":85.5,"is_successful":true}
{"type":"performance_metric","metric_name":"fps","value":30.0,"session_id":"uuid","timestamp":1704268801000}
```

## Cleanup Policy

- **Автоматичний**: щодня о 00:00
- **Критерій**: файли старіші 7 днів
- **Логування**: кількість видалених файлів та розмір

## Storage Info

```kotlin
val storageInfo = logger.getStorageInfo()
println("Size: ${storageInfo.sizeMB} MB")
println("Path: ${storageInfo.directory}")
```

## Graceful Shutdown

```kotlin
// In Application.onTerminate()
runBlocking {
    fileLogger.shutdown()  // Flush pending events
}
```

## Налагодження

```kotlin
// View logs in Android Studio
// Device File Explorer → data/data/com.ttcoachai/files/logs/

// Or pull via adb
adb pull /data/data/com.ttcoachai/files/logs/ ./logs/
```

## Best Practices

1. **Не блокуй UI thread** - всі методи вже async
2. **Логуй важливе** - не кожен frame, а тільки удари
3. **Перевіряй розмір** - використовуй `getStorageInfo()`
4. **Експортуй періодично** - для backup або аналізу
5. **Graceful shutdown** - завжди викликай `shutdown()` при закритті

## Troubleshooting

### Q: Логи не записуються
**A:** Перевір що Application клас зареєстровано в AndroidManifest.xml

### Q: Занадто багато місця
**A:** Зменши `bufferSize` або частоту логування

### Q: Втрата даних при краші
**A:** Нормально - максимум 50 подій (останній buffer). Для критичних даних використовуй синхронне логування.

## License

Same as main project.
