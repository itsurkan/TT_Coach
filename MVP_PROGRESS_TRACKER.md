# 🏓 AI Coach MVP - Трекер Прогресу

**Проект:** Мобільний додаток для аналізу техніки настільного тенісу  
**Цільовий термін:** 4-6 тижнів  
**Дата початку:** 24 грудня 2025  
**Статус:** 🟡 В розробці

---

## 📊 Загальний Прогрес

**Фактичний прогрес MVP:** 68% ✅  
**Останнє оновлення:** 3 січня 2026

| Модуль | Прогрес | Статус | Дедлайн |
|--------|---------|--------|---------|
| 0. Архітектура (Manager Pattern) | 85% | 🟢 Майже завершено | - |
| 1. Налаштування проекту | 90% | 🟢 Майже завершено | 31.12.2025 |
| 2. Вибір активності | 100% | 🟢 Завершено | 24.12.2025 |
| 3. Визначення позиції (MediaPipe) | 70% | 🟡 В процесі | - |
| 4. Аналіз руху | 75% | 🟡 В процесі | - |
| 5. Миттєвий фідбек | 60% | 🟡 В процесі | - |
| 6. Тестування та оптимізація | 15% | 🟡 В процесі | - |

**Легенда статусів:**
- ⚪ Не розпочато
- 🟡 В процесі
- 🟢 Завершено
- 🔴 Проблеми

---

## 🏗️ Модуль 0: Архітектура та Manager Pattern

**Дедлайн:** -  
**Прогрес:** 85%  
**Статус:** 🟢 Майже завершено

### Реалізовані компоненти:

- [x] **0.1. Manager Classes (9 файлів)**
  - [x] TrainingStateManager - управління станом тренування
  - [x] SettingsManager - управління налаштуваннями через SharedPreferences
  - [x] TrainingUIController - управління UI екрану тренування
  - [x] CameraUIController - управління UI камери
  - [x] SettingsUIController - управління UI налаштувань
  - [x] VideoPlayerManager - відтворення відео з pose detection
  - [x] CameraManager - інтеграція CameraX
  - [x] GalleryMediaProcessor - обробка медіа з галереї
  - [x] GalleryUIController - управління UI галереї
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: Повна архітектура з розділенням відповідальності.

- [x] **0.2. MediaPipe Helpers**
  - [x] PoseLandmarkerHelper - основний інтерфейс до MediaPipe
  - [x] PoseLandmarkerConfig - конфігурація landmarker
  - [x] PoseLandmarkerProcessor - обробка результатів
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: 3 режими роботи (IMAGE, VIDEO, LIVE_STREAM).

- [x] **0.3. Model System**
  - [x] ExerciseParameters з factory methods (forehandDrive, backhandDrive)
  - [x] AnalysisResult з 15+ полями
  - [x] StrokePhase enum (5 фаз)
  - [x] TechniqueErrors (8 типів помилок)
  - [x] TechniqueRecommendations (7 типів рекомендацій)
  - Дата виконання: 24 грудня 2025
  - Нотатки: Повна система моделей даних.

- [x] **0.4. Supporting Infrastructure**
  - [x] BaseActivity + LocaleHelper для багатомовності
  - [x] OverlayView для візуалізації скелета
  - [x] ViewBinding у всіх Activity
  - Дата виконання: 24 грудня 2025
  - Нотатки: Базова інфраструктура готова.

### Залишилось:
- [ ] Додати логування/аналітику
- [ ] Покращити error handling

---

## 🎯 Модуль 1: Налаштування Проекту

**Дедлайн:** 31 грудня 2025  
**Прогрес:** 90%  
**Статус:** 🟢 Майже завершено

### Завдання:

- [x] **1.1. Створити Android Native проект**
  - [x] Проект вже існує (базується на MediaPipe Pose Landmarker example)
  - [x] Налаштувати структуру пакетів (activities, adapters, utils)
  - [x] Gradle налаштовано з усіма залежностями
  - Дата виконання: 24 грудня 2025
  - Нотатки: Використано Kotlin для Android. Базова структура готова.

- [x] **1.2. Налаштувати залежності**
  - [x] MediaPipe Tasks Vision (0.10.29) - вже є в build.gradle
  - [x] CameraX (1.4.2) - вже є в build.gradle
  - [ ] Android TextToSpeech для аудіо фідбеку (системний API)
  - [x] SharedPreferences для локального сховища (системний API)
  - Дата виконання: 24 грудня 2025
  - Нотатки: Більшість залежностей вже налаштовано в базовому проекті. TTS буде використовувати системний Android API.

- [x] **1.3. Налаштувати дозволи**
  - [x] AndroidManifest.xml - камера вже налаштована
  - [ ] Додати дозвіл для TTS якщо потрібно
  - Дата виконання: 24 грудня 2025 (частково)
  - Нотатки: Android тільки (без iOS). Дозвіл камери вже є в AndroidManifest.

- [x] **1.4. Створити базову навігацію**
  - [x] Головний екран (WelcomeActivity)
  - [x] Екран вибору вправи (ExerciseSelectionActivity)
  - [x] Екран тренування (TrainingActivity)
  - [x] Екран налаштувань (SettingsActivity)
  - [x] BaseActivity для locale management
  - [x] LocaleHelper для багатомовності
  - Дата виконання: 24 грудня 2025
  - Нотатки: Створено всі 4 екрани з відповідними layout файлами. Реалізовано повну навігацію між екранами. Додано AndroidManifest з усіма Activity.

- [x] **1.5. ViewBinding та Material Design**
  - [x] ViewBinding увімкнено у всіх Activity
  - [x] Material Components для UI
  - [x] ConstraintLayout для адаптивних layout
  - Дата виконання: 24 грудня 2025
  - Нотатки: Сучасний підхід до UI без findViewById.

### Проблеми та блокери:
- ⚠️ TextToSpeech інтеграція не завершена (UI готовий, потрібна реалізація)

**Виконано 24-31 грудня 2025:**
- ✅ 6 Activity (Welcome, ExerciseSelection, Training, Settings, Base, Camera)
- ✅ 9 Manager classes
- ✅ 3 MediaPipe Helpers
- ✅ Повна система моделей (ExerciseParameters, AnalysisResult)
- ✅ ViewBinding у всіх екранах
- ✅ LocaleHelper для багатомовності
- ✅ AndroidManifest з усіма Activity та дозволами
- ⏳ TTS потребує реалізації (тільки UI готовий)

---

## 🎮 Модуль 2: Вибір Активності

**Дедлайн:** 31 грудня 2025  
**Прогрес:** 100%  
**Статус:** 🟢 Завершено

### Завдання:

- [x] **2.1. Створити UI для вибору вправи**
  - [x] RecyclerView з карточками вправ (ExerciseSelectionActivity)
  - [x] Дизайн екрану з Material Design
  - [x] Описи вправ з індикацією складності та тривалості
  - Дата виконання: 24 грудня 2025
  - Нотатки: 4 вправи (1 активна, 3 заблоковані). ExerciseAdapter для RecyclerView.

- [x] **2.2. Створити модель параметрів вправи**
  - [x] Data class `Exercise` (id, name, description, difficulty, duration)
  - [ ] Окремий клас ExerciseParameters для технічних параметрів (кути)
  - [x] Дефолтні значення в SettingsActivity
  - Дата виконання: 24 грудня 2025 (частково)
  - Нотатки: Exercise model створено. ExerciseParameters для кутів буде додано окремо.

- [x] **2.3. Реалізувати екран налаштувань**
  - [x] UI для редагування параметрів (SeekBar для кутів зап'ястя, ротації, follow-through)
  - [x] Аудіо налаштування (гучність, швидкість TTS)
  - [x] Налаштування камери (роздільна здатність, FPS, показ скелета)
  - [x] Збереження в SharedPreferences
  - Дата виконання: 24 грудня 2025
  - Нотатки: SettingsActivity з усіма параметрами готова. Кнопки збереження та скидання працюють.

- [x] **2.4. Інтеграція з rule-based системою**
  - [x] Створити клас MotionAnalyzer для аналізу техніки
  - [x] Створити клас FeedbackGenerator для генерації фідбеку
  - [x] Завантаження параметрів з SharedPreferences в TrainingActivity
  - [x] Передача параметрів до аналізатора
  - [x] Симуляція аналізу з реальними параметрами
  - Дата виконання: 24 грудня 2025
  - Нотатки: MotionAnalyzer готовий до інтеграції з MediaPipe. Поки що використовується симуляція для тестування логіки.

### Ключові параметри для MVP (Накат справа):
```json
{
  "exercise": "forehand_drive",
  "ideal_wrist_angle": 180,
  "min_body_rotation": 45,
  "contact_point_height": "table_level",
  "follow_through_angle": 120
}
```

### Проблеми та блокери:
- Немає

**Виконано 24 грудня 2025:**
- ✅ ExerciseSelectionActivity з RecyclerView
- ✅ Exercise data class
- ✅ ExerciseParameters клас з валідацією параметрів
- ✅ AnalysisResult модель для результатів аналізу
- ✅ MotionAnalyzer - rule-based аналіз техніки
- ✅ FeedbackGenerator - генерація фідбеку та мотиваційних повідомлень
- ✅ Інтеграція аналітики в TrainingActivity
- ✅ Симуляція аналізу з реальними параметрами користувача

**Модуль 2 завершено на 100%!** ✅

---

## 🎥 Модуль 3: Визначення Позиції (MediaPipe)

**Дедлайн:** 7 січня 2026  
**Прогрес:** 70%  
**Статус:** 🟡 В процесі

### Завдання:

- [x] **3.1. Інтегрувати MediaPipe Pose**
  - [x] Налаштувати MediaPipe Tasks API (PoseLandmarkerHelper)
  - [x] Отримати 33 keypoints (фокус на верхню частину тіла)
  - [x] 3 режими роботи (IMAGE, VIDEO, LIVE_STREAM)
  - [x] Конфігурація (PoseLandmarkerConfig)
  - [x] Обробка результатів (PoseLandmarkerProcessor)
  - [x] Автоматичне завантаження моделей через Gradle
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: Повна інтеграція MediaPipe готова. 3 моделі (lite, full, heavy).

- [x] **3.2. Візуалізація keypoints**
  - [x] OverlayView для малювання скелета
  - [x] Візуалізація поверх камери
  - [x] CameraFragment з підтримкою LandmarkerListener
  - [x] Показ/приховування скелета через налаштування
  - Дата виконання: 24 грудня 2025
  - Нотатки: Реал-тайм візуалізація працює.

- [x] **3.3. Реалізувати реал-тайм обробку**
  - [x] Camera streaming через CameraX (CameraFragment)
  - [x] Обробка кожного фрейму через MediaPipe
  - [x] Background executor для MediaPipe операцій
  - [x] Lifecycle management (onCreate/onPause/onDestroy)
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: CameraX інтегровано, FPS залежить від пристрою.

- [x] **3.4. Розрахунок кутів**
  - [x] calculateAngle() в MotionAnalyzer (векторна математика)
  - [x] Кут лікоть-зап'ястя (wrist angle)
  - [x] Кут плече-лікоть-зап'ястя (follow-through)
  - [x] Ротація корпусу (плечі відносно стегон)
  - [x] Висота контакту відносно підлоги
  - [x] Відстань лікоть-тіло
  - Дата виконання: 24 грудня 2025
  - Нотатки: Всі необхідні розрахунки реалізовані в MotionAnalyzer.

- [ ] **3.5. Калібрування (неповне)**
  - [x] UI кнопка калібрування в TrainingActivity
  - [ ] Збереження референсної пози
  - [ ] Індикатор "готовності" (зелений/червоний)
  - Дата виконання: Частково 24 грудня 2025
  - Нотатки: Поки що 3-секундна симуляція. Потрібно зберігати реальну позу.

- [ ] **3.6. З'єднання з аналізом (критичний геп)**
  - [x] TrainingActivity.onResults() callback метод існує
  - [ ] Виклик MotionAnalyzer.analyzeStroke() з resultBundle
  - [ ] Передача результатів до FeedbackGenerator
  - [ ] Оновлення UI з реальним фідбеком
  - Дата виконання: Заплановано на 5-7 січня 2026
  - Нотатки: ⚠️ **КРИТИЧНО**: Це основний геп! Всі частини є, потрібно з'єднати.

### Ключові точки для настільного тенісу:
- Плече (shoulder): #11, #12
- Лікоть (elbow): #13, #14
- Зап'ястя (wrist): #15, #16
- Стегна (hip): #23, #24

### Проблеми та блокери:
- ⚠️ TrainingActivity.onResults() не викликає MotionAnalyzer (критичний геп)
- ⚠️ Калібрування використовує симуляцію замість реальної пози

---

## 🔍 Модуль 4: Аналіз Руху

**Дедлайн:** 10 січня 2026  
**Прогрес:** 75%  
**Статус:** 🟡 В процесі

### Завдання:

- [ ] **4.1. Детекція руху (stroke detection) - TODO**
  - [ ] Буферизація фреймів (останні 1-2 сек)
  - [ ] Алгоритм виявлення початку удару (швидкість зап'ястя > поріг)
  - [ ] Алгоритм виявлення кінця удару (повернення до бази)
  - Дата виконання: Заплановано на 7-10 січня 2026
  - Нотатки: ⚠️ Поки що аналіз викликається симуляцією, не автоматично.

- [x] **4.2. Rule-based аналіз техніки**
  - [x] Порівняння кутів з ідеальними параметрами (MotionAnalyzer)
  - [x] Виявлення відхилень та валідація
  - [x] Класифікація помилок (TechniqueErrors)
  - [x] Генерація рекомендацій (TechniqueRecommendations)
  - Дата виконання: 24 грудня 2025
  - Нотатки: Rule-based логіка готова. Очікує інтеграції з MediaPipe для реальних даних.

- [x] **4.3. Аналіз фаз руху**
  - [x] StrokePhase enum (PREPARATION, BACKSWING, FORWARD, CONTACT, FOLLOW_THROUGH)
  - [x] Відстеження поточної фази в AnalysisResult
  - [x] Параметри для кожної фази в ExerciseParameters
  - Дата виконання: 24 грудня 2025
  - Нотатки: Структура для фаз готова, детальна логіка буде після stroke detection.

- [x] **4.4. Генерація результатів аналізу**
  - [x] Структура даних AnalysisResult (Kotlin data class)
  - [x] Всі параметри техніки (кути, висота, відстані)
  - [x] Прапорці валідності для кожного параметру
  - [x] Список помилок (TechniqueErrors)
  - [x] Список рекомендацій (TechniqueRecommendations)
  - [x] overallScore (0-100%) та isSuccessful() helper
  - Дата виконання: 24 грудня 2025
  - Нотатки: Повна модель даних готова.

- [ ] **4.5. Опціонально: Аналіз м'яча**
  - [ ] YOLO для виявлення м'яча
  - [ ] Kalman фільтр для траєкторії
  - [ ] Optic flow для спіну
  - [ ] Розрахунок швидкості та попадання
  - Дата виконання: _________
  - Нотатки: _________

### Критерії аналізу для "Накат справа":
```
✓ Зап'ястя пряме (170-190°) - реалізовано ✅
✓ Ротація корпусу (45°+) - реалізовано ✅
✓ Контакт на рівні столу (0.7-1.1 від висоти стегна) - реалізовано ✅
✓ Follow-through вгору-вперед (100-140°) - реалізовано ✅
✓ Лікоть близько до тіла (<0.3m) - реалізовано ✅
```

### Фактично реалізовані розрахунки в MotionAnalyzer:
- ✅ calculateWristAngle() - кут лікоть-зап'ястя-вказівний палець
- ✅ calculateBodyRotation() - різниця кутів плечей та стегон
- ✅ calculateFollowThroughAngle() - кут плече-лікоть-зап'ястя
- ✅ calculateContactHeight() - висота зап'ястя відносно підлоги
- ✅ calculateElbowBodyDistance() - відстань лікоть-центр тіла
- ✅ Валідація всіх параметрів з toleranceRatio
- ✅ Класифікація помилок (8 типів)
- ✅ Генерація рекомендацій (7 типів)

### Проблеми та блокери:
- ⚠️ Детекція початку/кінця удару не реалізована (поки що симуляція)
- ⚠️ Немає з'єднання з реальними даними MediaPipe (критичний геп)

---

## 💬 Модуль 5: Миттєвий Фідбек

**Дедлайн:** 12 січня 2026  
**Прогрес:** 60%  
**Статус:** 🟡 В процесі

### Завдання:

- [x] **5.1. FeedbackGenerator клас з методами генерації**
  - [x] Короткий фідбек для TTS (generateShortFeedback)
  - [x] Детальний фідбек з описом помилок (generateDetailedFeedback)
  - [x] Фідбек по параметрах (generateParameterFeedback)
  - [x] Мотиваційні повідомлення (generateMotivationalMessage)
  - [x] Підсумок сесії з рекомендаціями
  - [x] Підказки для покращення техніки
  - [x] Мова: українська
  - [x] Пул позитивних повідомлень (випадковий вибір)
  - Дата виконання: 24 грудня 2025
  - Нотатки: ✅ Повна система генерації фідбеку готова! 160 рядків коду.

- [x] **5.1.2. TrainingStateManager**
  - [x] Відстеження стану тренування (isTrainingActive)
  - [x] Історія фідбеків (останні 10)
  - [x] Список результатів аналізу
  - [x] Лічильник послідовних хороших ударів
  - [x] Статистика (середній рахунок, кількість ударів)
  - [x] Генерація підсумку тренування
  - [x] Поради для покращення
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: ✅ Повне управління станом. 94 рядки коду.

- [ ] **5.2. Опціонально: LLM фідбек**
  - [ ] Інтеграція з OpenAI API (або Gemini)
  - [ ] Промпт для генерації персоналізованого фідбеку
  - [ ] Fallback на локальні шаблони якщо немає інтернету
  - Дата виконання: _________
  - Нотатки: _________

- [ ] **5.3. Аудіо фідбек (TTS)**
  - [ ] Налаштування Android TextToSpeech
  - [ ] Озвучення фідбеку <200ms після аналізу
  - [ ] Вибір голосу (налаштування вже є в Settings)
  - Дата виконання: _________
  - Нотатки: UI для налаштувань TTS готова. Потрібна реалізація в TrainingActivity.

- [x] **5.4. UI для фідбеку**
  - [x] TrainingUIController (124 рядки)
  - [x] Текст на екрані (поточний фідбек з кольором)
  - [x] Історія останніх фідбеків (ScrollView)
  - [x] Кольорові індикатори (зелений ✅, жовтий ⚠️, червоний ❌)
  - [x] Статистика в реальному часі (stroke count, accuracy)
  - [x] Діалог підсумку з опціями "Завершити"/"Продовжити"
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: ✅ Повний UI контролер готовий. Працює з симуляцією.

- [ ] **5.5. Оптимізація латентності**
  - [ ] Вимірювання end-to-end латентності
  - [ ] Оптимізація до <500ms (аналіз + фідбек)
  - [x] Background executor для MediaPipe
  - [ ] Профілювання bottlenecks
  - Дата виконання: Заплановано після інтеграції
  - Нотатки: Background executor вже використовується, але потрібне вимірювання.

### Приклади фідбеку (реалізовані в FeedbackGenerator):
- ✅ "Чудово! Ідеальна техніка!"
- ✅ "Відмінно! Всі параметри в нормі!"
- ⚠️ "Більше ротації корпусу!"
- ⚠️ "Тримай зап'ястя рівно!"
- ⚠️ "Контакт надто високо - опусти точку удару"
- ⚠️ "Проведи рух до кінця!"
- 🎉 Мотиваційні повідомлення на віхах (5, 10, 20, 50 ударів)

### Проблеми та блокери:
- ⚠️ Android TextToSpeech не реалізовано (тільки UI налаштувань готовий)
- ⚠️ Поки що працює з симуляцією, не з реальними даними MediaPipe

---

## 🧪 Модуль 6: Тестування та Оптимізація

**Дедлайн:** 15 січня 2026  
**Прогрес:** 15%  
**Статус:** 🟡 В процесі

### Завдання:

- [x] **6.0. Unit тестування**
  - [x] TrainingStateManagerTest (12+ тестів)
  - [x] Тести старту/зупинки тренування
  - [x] Тести історії фідбеків
  - [x] Тести статистики (середній рахунок, кількість ударів)
  - [x] Тести підсумку
  - Дата виконання: 24-31 грудня 2025
  - Нотатки: ✅ Перші unit тести готові. Покриття: ~15%.

- [ ] **6.1. Функціональне тестування**
  - [ ] Тестування на реальних відео тенісу
  - [ ] Перевірка точності MediaPipe (візуальна оцінка)
  - [ ] Тестування детекції ударів (false positives/negatives)
  - [ ] Валідація розрахунків кутів з відомими позами
  - Дата виконання: Заплановано після інтеграції
  - Нотатки: Потрібні тестові відео з правильною технікою.

- [ ] **6.2. Тестування продуктивності**
  - [ ] Вимірювання FPS на різних пристроях
  - [ ] Латентність аналізу (target: <500ms)
  - [ ] Споживання батареї (10 хв тренування)
  - [ ] Тепловиділення (thermal throttling)
  - Дата виконання: _________
  - Нотатки: _________

- [ ] **6.3. Тестування на пристроях**
  - [ ] Android (mid-range: Snapdragon 6xx+)
  - [ ] Android (high-end: Snapdragon 8xx)
  - [ ] iOS (iPhone 12+)
  - Дата виконання: _________
  - Нотатки: _________

- [ ] **6.4. UX тестування**
  - [ ] Користувацьке тестування (5+ користувачів)
  - [ ] Збір фідбеку по UI/UX
  - [ ] Корекції на основі фідбеку
  - Дата виконання: _________
  - Нотатки: _________

- [ ] **6.5. Оптимізація**
  - [ ] Оптимізація MediaPipe (зменшення розміру моделі якщо потрібно)
  - [ ] Кешування обчислень
  - [ ] Код-ревю та рефакторинг
  - Дата виконання: _________
  - Нотатки: _________

- [ ] **6.6. Документація**
  - [ ] README.md з інструкціями
  - [ ] Документація API (якщо є backend)
  - [ ] User guide (як використовувати додаток)
  - Дата виконання: _________
  - Нотатки: _________

### Критерії успіху MVP:
- ✅ Точність виявлення позиції: 80%+
- ✅ Латентність фідбеку: <500ms
- ✅ FPS: 30+ на mid-range пристроях
- ✅ Користувачі розуміють фідбек і можуть покращити техніку
- ✅ Додаток стабільний (без критичних крешів)

### Проблеми та блокери:
- _________

---

## 📝 Нотатки та Рішення

### Технічні рішення:
- **Платформа:** Android Native (Kotlin)
- **Мінімальний SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **CV фреймворк:** MediaPipe Pose Tasks API (0.10.29, on-device)
- **Камера:** CameraX (1.4.2)
- **TTS:** Android TextToSpeech API (системний)
- **Сховище:** SharedPreferences (системний Android API)
- **UI:** Material Components, ConstraintLayout, RecyclerView
- **Опціонально:** OpenAI API для складного фідбеку

### Архітектурні рішення:
```
app/src/main/java/com/google/mediapipe/examples/poselandmarker/
├── Activities (6 файлів)
│   ├── WelcomeActivity.kt ✅
│   ├── ExerciseSelectionActivity.kt ✅
│   ├── TrainingActivity.kt ✅ (implements LandmarkerListener)
│   ├── SettingsActivity.kt ✅
│   ├── BaseActivity.kt ✅ (locale management)
│   └── MainActivity.kt (legacy)
├── Managers (9 файлів) ✅
│   ├── TrainingStateManager.kt
│   ├── SettingsManager.kt
│   ├── TrainingUIController.kt
│   ├── CameraUIController.kt
│   ├── SettingsUIController.kt
│   ├── VideoPlayerManager.kt
│   ├── CameraManager.kt
│   ├── GalleryMediaProcessor.kt
│   └── GalleryUIController.kt
├── Helpers (3 файли) ✅
│   ├── PoseLandmarkerHelper.kt (3 running modes)
│   ├── PoseLandmarkerConfig.kt
│   ├── PoseLandmarkerProcessor.kt
│   └── LocaleHelper.kt
├── Models (2 файли) ✅
│   ├── ExerciseParameters.kt (152 рядки, з валідацією)
│   └── AnalysisResult.kt (113 рядків, з StrokePhase, Errors, Recommendations)
├── Services (2 файли) ✅
│   ├── MotionAnalyzer.kt (244 рядки, rule-based аналіз)
│   └── FeedbackGenerator.kt (160 рядків, 4 типи фідбеку)
├── Fragment (3 файли) ✅
│   ├── CameraFragment.kt (реалізує LandmarkerListener)
│   ├── GalleryFragment.kt
│   └── PermissionsFragment.kt
├── Supporting (5 файлів) ✅
│   ├── ExerciseAdapter.kt
│   ├── OverlayView.kt (візуалізація скелета)
│   ├── MainViewModel.kt
│   └── PoseLandmarkerApp.kt
└── Test (4 файли) ⚠️
    ├── TrainingStateManagerTest.kt ✅ (12+ тестів)
    ├── ExerciseParametersTest.kt
    ├── MotionAnalyzerTest.kt
    └── ExampleUnitTest.kt

app/src/main/res/
├── layout/ (10+ XML файлів) ✅
│   ├── activity_welcome.xml
│   ├── activity_exercise_selection.xml
│   ├── activity_training.xml
│   ├── activity_settings.xml
│   ├── item_exercise.xml
│   ├── fragment_camera.xml
│   ├── fragment_gallery.xml
│   └── activity_main.xml (legacy)
└── values/ ✅
    ├── strings.xml
    ├── colors.xml
    └── arrays.xml

app/src/main/assets/ ✅
└── pose_landmarker_*.task (3 моделі, автоматично завантажуються)
```

### Manager Pattern:
Проект використовує Manager Pattern для розділення відповідальності:
- **State Managers**: TrainingStateManager, SettingsManager
- **UI Controllers**: TrainingUIController, CameraUIController, SettingsUIController, GalleryUIController
- **Media Processors**: VideoPlayerManager, CameraManager, GalleryMediaProcessor
- **Helpers**: PoseLandmarkerHelper, LocaleHelper

Кожна Activity ініціалізує свої manager класи в `onCreate()` через `initializeManagers()`.

---

## 🚨 Критичні Гепи (Priority 1)

### 1. MediaPipe → MotionAnalyzer Bridge ВІДСУТНІЙ 🔴
**Локація:** `TrainingActivity.kt` метод `onResults()`

**Проблема:**
```kotlin
override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
    // Метод існує але не викликає аналізатор!
    // TODO: з'єднати з MotionAnalyzer
}
```

**Що потрібно:**
1. Отримати `resultBundle.results[0]` (PoseLandmarkerResult)
2. Викликати `motionAnalyzer.analyzeStroke(result, currentPhase)`
3. Передати AnalysisResult до `feedbackGenerator`
4. Оновити UI через `trainingUIController`

**Impact:** Без цього немає реал-тайм аналізу техніки! ⚠️

---

### 2. TextToSpeech Не Реалізовано 🟡
**Локація:** `TrainingActivity.kt`

**Проблема:**
- SettingsActivity має UI для TTS налаштувань ✅
- FeedbackGenerator генерує TTS-ready strings ✅
- Android TTS сервіс НЕ ініціалізовано ❌

**Що потрібно:**
1. Ініціалізувати `TextToSpeech` в `TrainingActivity.onCreate()`
2. Створити метод `speakFeedback(text: String)`
3. Викликати при генерації фідбеку
4. Враховувати налаштування (увімкнено, гучність, швидкість)

**Impact:** Немає аудіо фідбеку під час тренування.

---

### 3. Калібрування Неповне 🟡
**Локація:** `TrainingActivity.kt` метод `startCalibration()`

**Проблема:**
- Кнопка калібрування є ✅
- 3-секундна симуляція замість реальної пози ❌
- Не зберігається референсна поза для порівняння ❌

**Що потрібно:**
1. Захопити PoseLandmarkerResult під час калібрування
2. Зберегти baseline measurements (кути, позиції)
3. Використовувати для порівняння під час тренування

**Impact:** Немає персоналізованого baseline.

---

### 4. Stroke Detection Відсутня 🟡
**Локація:** Потрібен новий клас або логіка в MotionAnalyzer

**Проблема:**
- MotionAnalyzer може аналізувати один frame ✅
- Немає логіки виявлення початку/кінця удару ❌
- Аналіз викликається симуляцією, не автоматично ❌

**Що потрібно:**
1. Буферизація останніх 30-60 frames
2. Виявлення швидкості зап'ястя (velocity threshold)
3. Тригер аналізу при виявленні удару
4. State machine для фаз удару

**Impact:** Не може автоматично виявляти коли гравець виконує удар.

---

### Розширення для майбутніх версій:
- [ ] Аналіз м'яча (YOLO + Kalman)
- [ ] Підтримка більше вправ (бекхенд, сервіс, топ-спін)
- [ ] Історія тренувань та статистика
- [ ] Відео-плейбек з аннотаціями
- [ ] Порівняння з професіоналами
- [ ] Gamification (досягнення, челенджі)

---

## 🐛 Лог Проблем

| Дата | Проблема | Статус | Рішення |
|------|----------|--------|---------|
| 03.01.2026 | Виявлено розбіжність: documented progress 38% vs actual 68% | 🟢 Вирішено | Оновлено MVP_PROGRESS_TRACKER.md |
| 03.01.2026 | MediaPipe→MotionAnalyzer bridge відсутній | 🔴 Критично | Заплановано на 5-7 січня 2026 |
| 03.01.2026 | TextToSpeech не реалізовано | 🟡 Medium | Заплановано після bridge |
| 03.01.2026 | Калібрування використовує симуляцію | 🟡 Medium | Заплановано після bridge |
| 03.01.2026 | Stroke detection відсутня | 🟡 Medium | Заплановано після bridge |

---

## 📅 Щотижневий Огляд

### Тиждень 1 (24.12.2025 - 30.12.2025)
**Планові завдання:**
- Створити базову навігацію (4 Activities)
- Реалізувати вибір вправи
- Підключити MediaPipe
- Створити rule-based аналіз

**Виконано:**
- ✅ 6 Activities (Welcome, ExerciseSelection, Training, Settings, Base, legacy)
- ✅ 9 Manager classes (TrainingState, Settings, UI Controllers, Media Processors)
- ✅ 3 MediaPipe Helpers (PoseLandmarkerHelper, Config, Processor)
- ✅ ExerciseParameters з валідацією (152 рядки)
- ✅ AnalysisResult модель (113 рядків)
- ✅ MotionAnalyzer з rule-based логікою (244 рядки)
- ✅ FeedbackGenerator з 4 типами фідбеку (160 рядків)
- ✅ TrainingStateManager (94 рядки)
- ✅ LocaleHelper для багатомовності
- ✅ OverlayView для візуалізації скелета
- ✅ CameraFragment з LandmarkerListener
- ✅ VideoPlayerManager для відео
- ✅ TrainingStateManagerTest (12+ unit тестів)

**Проблеми:**
- ⚠️ MediaPipe результати не з'єднані з MotionAnalyzer
- ⚠️ TrainingActivity.onResults() існує але не викликає аналіз

**На наступний тиждень:**
- Пріоритет 1: З'єднати MediaPipe → MotionAnalyzer
- Додати TTS інтеграцію
- Реалізувати stroke detection

---

### Тиждень 2 (31.12.2025 - 6.01.2026)
**Планові завдання:**
- Інтеграція MediaPipe з аналізом
- Stroke detection
- TTS реалізація

**Виконано:**
- ⏳ В процесі (3 січня 2026)
- ✅ Оновлено MVP_PROGRESS_TRACKER.md до фактичного стану
- ✅ Виявлено 4 критичні гепи

**Проблеми:**
- 🔴 MediaPipe→MotionAnalyzer bridge - критичний геп

**На наступний тиждень:**
- 🎯 **Priority 1:** Реалізувати MediaPipe→MotionAnalyzer bridge
- Видалити симуляцію, використовувати реальні дані
- Тестування на реальних відео
- TTS інтеграція

---

### Тиждень 3 (__ - __)
**Планові завдання:**
- _________

**Виконано:**
- _________

**Проблеми:**
- _________

**На наступний тиждень:**
- _________

---

### Тиждень 4 (__ - __)
**Планові завдання:**
- _________

**Виконано:**
- _________

**Проблеми:**
- _________

**На наступний тиждень:**
- _________

---

## ✅ Критерії Готовності MVP

- [x] Користувач може вибрати вправу "Накат справа" ✅
- [x] Додаток визначає позицію тіла в реальному часі (MediaPipe інтегровано) ✅
- [ ] MediaPipe результати з'єднані з MotionAnalyzer ⚠️ **КРИТИЧНО**
- [ ] Додаток виявляє кожен удар автоматично (stroke detection)
- [x] Rule-based аналіз техніки реалізовано ✅
- [x] FeedbackGenerator генерує фідбек українською ✅
- [ ] Після кожного удару надається фідбек (<500ms)
- [x] Фідбек зрозумілий і допомагає покращити техніку (генерується) ✅
- [ ] TTS озвучує фідбек ⚠️
- [x] Додаток працює на Android (тільки Android, не iOS) ✅
- [ ] Базове тестування пройдено (5+ користувачів)
- [x] Unit тести для TrainingStateManager ✅
- [ ] Документація готова

**Прогрес:** 7/14 критеріїв (50%) | Фактичний прогрес коду: 68%

---

## 📞 Контакти та Ресурси

**Команда:**
- Розробник: _________
- Дизайнер: _________
- Тестувальник: _________

**Корисні посилання:**
- [MediaPipe Docs](https://developers.google.com/mediapipe)
- [Flutter Camera Plugin](https://pub.dev/packages/camera)
- [Flutter TTS](https://pub.dev/packages/flutter_tts)
- [Table Tennis Techniques](https://www.youtube.com/watch?v=example)

**Git репозиторій:**
- _________

---

## 📈 Підсумок Прогресу

**Фактичний прогрес:** 68% (було задокументовано 38%)  
**Модулів завершено:** 1 з 6 (Модуль 2 - 100%)  
**Критичних гепів:** 4 (1 блокуючий)

**Основні досягнення:**
- ✅ Повна Manager архітектура (9 класів)
- ✅ MediaPipe інтеграція (70% готовності)
- ✅ Rule-based аналіз (75% готовності)
- ✅ FeedbackGenerator (60% готовності)
- ✅ 12+ unit тестів

**Блокуючий геп:** MediaPipe→MotionAnalyzer bridge відсутній  
**Розрахунковий термін завершення MVP:** 12-15 січня 2026 (якщо усунути критичні гепи)

---

**Останнє оновлення:** 3 січня 2026
