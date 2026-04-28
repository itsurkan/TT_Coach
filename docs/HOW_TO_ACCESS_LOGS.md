# 📱 Як отримати доступ до логів на телефоні

## ❓ Чому я не бачу логи в файловому менеджері?

**Коротка відповідь:** Android захищає системні папки додатків від прямого доступу.

### Технічні деталі:
```
/data/data/com.ttcoachai/files/logs/
     └─── ця папка НЕДОСТУПНА через звичайний файловий менеджер
```

**Чому?**
- ✋ Це **системна папка** Android (внутрішнє сховище додатку)
- 🔒 Захищена стандартною безпекою Android
- 📵 Не можна відкрити без root прав
- ✅ Це **нормально і правильно** для захисту даних

## ✅ 3 способи отримати логи

### 🎯 Спосіб 1: Швидкий експорт через ADB (РЕКОМЕНДОВАНО)

**На комп'ютері:**
```powershell
cd d:\Desktop\TT_Coach_AI
.\scripts\quick_export.ps1
```

**Результат:**
```
✓ Логи завантажені на комп'ютер
📁 Локація: d:\Desktop\TT_Coach_AI\logs_export\
   - sessions.jsonl
   - events.jsonl
   - strokes.jsonl (якщо є)
```

**Що потрібно:**
- ✅ USB кабель підключено
- ✅ USB Debugging увімкнено
- ✅ ADB встановлено (`adb devices` показує пристрій)

---

### 📲 Спосіб 2: Експорт в External Storage (доступна папка на телефоні)

**TODO - Додати в наступній версії:**

Буде кнопка в **Settings → Debug → Export Logs** яка створить файл:
```
/sdcard/Android/data/com.ttcoachai/files/logs_export.zip
```

Цю папку **МОЖНА** відкрити в будь-якому файловому менеджері! 📂

**⚙️ Технічні деталі:**
- Папка створюється автоматично при першому експорті
- `getExternalFilesDir()` автоматично створює необхідні директорії
- Перед записом кожного файлу перевіряється існування батьківської директорії
- Якщо папка була видалена, вона буде відновлена при наступному експорті

**Як це буде працювати:**
1. Відкрити Settings → Debug
2. Натиснути "Export Logs"
3. Відкрити файловий менеджер на телефоні
4. Перейти: `Внутрішнє сховище → Android → data → com.ttcoachai → files`
5. Знайти `logs_export_[timestamp].zip` або папку `logs/`
6. Поділитись через Email/Drive/Telegram

---

### 🖥️ Спосіб 3: Вручну через ADB Shell

**Для досвідчених користувачів:**

```powershell
# 1. Подивитись список файлів
adb shell "run-as com.ttcoachai ls /data/data/com.ttcoachai/files/logs/training_sessions/"

# 2. Витягнути конкретний файл
adb exec-out "run-as com.ttcoachai cat /data/data/com.ttcoachai/files/logs/training_sessions/2026-01-03_sessions.jsonl" > sessions.json

# 3. Переглянути вміст
adb shell "run-as com.ttcoachai cat /data/data/com.ttcoachai/files/logs/training_sessions/2026-01-03_sessions.jsonl"
```

---

## 🆚 Порівняння способів

| Спосіб | Складність | Швидкість | Потрібен комп'ютер |
|--------|------------|-----------|-------------------|
| 1️⃣ Quick export script | ⭐ Легко | ⚡ Швидко | ✅ Так |
| 2️⃣ Export в External Storage | ⭐⭐ Дуже легко | ⚡ Швидко | ❌ Ні |
| 3️⃣ ADB shell вручну | ⭐⭐⭐ Складно | 🐌 Повільно | ✅ Так |

**Рекомендація:** Використовуйте Спосіб 1 для швидкого доступу, або чекайте Спосіб 2 в наступній версії.

---

## 📚 Додаткова інформація

### Де зберігаються логи в Android

Android має 2 типи сховища:

1. **Internal Storage (приватне)** 🔒
   ```
   /data/data/[package_name]/files/
   ├── Недоступне для користувача
   ├── Недоступне для інших додатків
   └── Видаляється при деінсталяції
   ```

2. **External Storage (спільне)** 📂
   ```
   /sdcard/Android/data/[package_name]/files/
   ├── Доступне через файловий менеджер ✅
   ├── Можна поділитись файлами ✅
   └── Видаляється при деінсталяції
   ```

**Наші логи зараз:** Internal Storage (тому недоступні)  
**Планується:** Додати експорт в External Storage

### Альтернативи для перегляду логів

**Logcat (реал-тайм логи):**
```powershell
adb logcat -s PoseAnalysisProcessor:* AsyncFileLogger:* TrainingActivity:*
```

**Android Studio Device File Explorer:**
1. View → Tool Windows → Device File Explorer
2. Навігація: `/data/data/com.ttcoachai/files/logs/`
3. Right-click → Save As... (експортувати файл)

---

## 🆘 Troubleshooting

### "Permission denied" при ADB команді
```powershell
# Перевірте чи підключено пристрій
adb devices

# Має показати: List of devices attached
#                [serial]    device
```

### "No such file or directory"
Це означає що:
- 🔸 Тренування ще не запускалось (файли не створені)
- 🔸 Додаток не логував події
- 🔸 Файл ще не був збережений на диск

**Вирішення:** Запустіть тренування в додатку, почекайте 10 секунд, спробуйте знову.

### "Device unauthorized"
```powershell
# 1. На телефоні з'явиться діалог "Allow USB debugging?"
# 2. Натисніть "Allow"
# 3. (Опціонально) Поставте галочку "Always allow from this computer"
```

---

## ✅ Швидкий чеклист

- [ ] USB Debugging увімкнено на телефоні
- [ ] Телефон підключено до комп'ютера
- [ ] `adb devices` показує пристрій
- [ ] Запущено тренування в додатку (хоча б раз)
- [ ] Запущено `.\scripts\quick_export.ps1`
- [ ] Логи з'явились в `d:\Desktop\TT_Coach_AI\logs_export\`
- [ ] Відкрито файли в VS Code / текстовому редакторі

---

**Оновлено:** 3 січня 2026  
**Статус:** ✅ Експорт через ADB працює  
**TODO:** Додати експорт в External Storage для доступу без комп'ютера
