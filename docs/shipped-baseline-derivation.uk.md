# Виведення шаблонного базлайну — ShippedBaselines.FOREHAND_ANDRII

*Переклад англійського оригіналу [shipped-baseline-derivation.md](shipped-baseline-derivation.md); у разі розбіжностей — оригінал є авторитетним.*

Дата: 2026-07-28

## Команда

```
.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4 --model lite --interval 17
```

`--interval 17` відображає закомічений full-fps фікстур `andrii_1_poses_rtm.json` (вихідне
відео — 59.3 fps ≈ 16.86 мс/кадр, округлено до 17 мс) — див. «Історія виведення» нижче про те,
чому це замінило дефолтне значення `--interval` скрипта, 100 мс. Прапорець приймає будь-яке
ціле число (без нижньої межі, закладеної в argparse `export_poses_mediapipe.py`), тож 17 було
використано напряму.

Результат: `Videos/andrii_1/andrii_1_poses_mediapipe_lite.json` (схема v2, COCO-17,
`intervalMs: 17`, 1106 кадрів, у 1106 виявлено позу — 720x1280, `videoDurationMs=18795`),
force-доданий у git за прецедентом `andrii_1_poses_rtm.json` — `.gitignore` за замовчуванням
блокує `/Videos/**`. Той самий шлях, що й у першому проході — скрипт експорту перезаписує файл
на місці, тому git відстежує один файл через обидва проходи виведення.

## Виклик виведення

`DrillCalibrator.calibrate(sequence, drillType = "forehand_drive", createdAtMs = 1L,
handedness = Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f)` — див.
`ShippedBaselineDerivationHarness.kt` (незмінний між обома проходами виведення; змінився лише
`intervalMs` вхідного JSON).

**Ворота по yaw свідомо послаблено**: реальний per-rep |yaw| на цих кадрах пробігає
**30.0°–90.0°** по 15 виявлених повтореннях (медіана ≈43.5°) — вище за звичайні ~30° воріт
розміщення камери (`CameraAngleEstimator` насичується на своїй стелі 90° на цих
непротокольних кадрах, див. `docs/DESIGN_LIMITATIONS.md` L-25). `cameraYawDeg = 0f`
трактує геометрію фікстури як референс лише для цього одноразового редакційного виведення —
не як прецедент для live-сесій. (Це обґрунтування і виміряний діапазон yaw практично незмінні
між проходами 100мс і 17мс — yaw читається у до-ударній стійці готовності, нечутливий до
фази пікового кадру.)

## Історія виведення

**Перший прохід (інтервал 100 мс, дефолт скрипта)** дав сильно мультимодальний розподіл
`elbow_angle` (кластери ~27–38°, ~70–100°, ~116–155° по 15 `stationary`-повторенням), який
пережив 2σ відсіювання викидів `BaselineDeriver` майже без змін — виключено лише 1 з 4
повторень верхнього кластера (через `coil_ratio`, не сам `elbow_angle`). Початкова гіпотеза —
забруднення відновлювальними замахами (recovery swings). Візуальний перегляд 15 PNG пікових
кадрів цього проходу (усі 15 переглянуті агентом виведення, 8 із 15 незалежно передивився
оркестратор) спростував це: усі 15 — той самий справжній forehand (узгоджена стійка,
`knee_bend`, `torso_lean`, `stroke_speed` — `ForwardStrokeFilter` уже відкинув 6 реальних
відновлювальних замахів до цього моменту), а розкид ліктя коваріював із `shoulder_angle` по
повтореннях, а не формував дві чітко розділені популяції повторень. Ця коваріація — це ознака
**фазового джитера пікового кадру**, а не двох різних рухів: вікно медіани ±70мс у
`DrillMetrics.extractAtPeak` вузьке відносно 100-мс інтервалу семплювання, тож який саме
момент ~180-мс прямого удару вибирається як «пік» варіює від повторення до повторення, а кут
ліктя у forehand безперервно змінюється в цьому вікні (задовго до повного розгинання біля
контакту проти вже майже контакту). Ручне виключення повторень із високим ліктем означало б
вручну підібрати суміш фаз, а не виправити базову проблему семплювання, і конвенція `shared/`
проєкту вже трактує ефекти інтервалу семплювання як питання пайплайну, а не редакційне (див.
власні мс-орієнтовані вікна тюнінгу `StrokeDetector2D`, обрані саме для незалежності від fps).

Компактний запис виведеного результату цього замінного проходу (вхідні дані для діагнозу вище):

```
repCount=11 excludedRepIndices=[0, 1, 6, 8] qualityScore=0.6695165073025089
"elbow_angle" to MetricStats(mean=71.64542215520686, std=39.114602922054395, min=27.338851928710938, max=119.0262680053711, sampleCount=11),
"shoulder_angle" to MetricStats(mean=39.125756523825906, std=21.13768114821029, min=10.877771377563477, max=70.30072021484375, sampleCount=11),
"knee_bend" to MetricStats(mean=175.5822615189986, std=3.2270200698360165, min=170.2755126953125, max=179.75291442871094, sampleCount=11),
"torso_lean" to MetricStats(mean=4.349920131943443, std=2.3482826393594873, min=1.4852596521377563, max=7.665465831756592, sampleCount=11),
"follow_through_angle_2d" to MetricStats(mean=122.67502000596788, std=47.786254229559475, min=62.71063995361328, max=169.2542266845703, sampleCount=9),
"stroke_speed" to MetricStats(mean=8.614285165613348, std=0.46126920336117117, min=7.65094518661499, max=9.282928466796875, sampleCount=11),
"coil_ratio" to MetricStats(mean=0.7931778187101538, std=0.17916409753423934, min=0.43392249941825867, max=1.1075656414031982, sampleCount=11),
```

**Виправлення: повторний експорт із повною часовою роздільністю** (`--interval 17`, за
конвенцією закомміченого full-fps фікстура `*_rtm.json`), щоб вікно ±70мс `extractAtPeak`
покривало ~8 реальних кадрів навколо справжнього піку замість ~1, семплюючи справжню вершину
удару, а не той 100-мс бакет, що випадково опинився поруч. Повторний запуск того самого
(незмінного) harness проти нового експорту стягнув std `elbow_angle` з 39.1° до **29.9°**
(mean 71.6°→64.5°) і змінив, які/скільки повторень відкидає 2σ-відсіювання
(`[0,1,6,8]`→`[0,11,12]`, `repCount` 11→12, `qualityScore` 0.670→0.741). Розкид не стягнувся
повністю в одну щільну моду — два повторення (rep 7 на 120.0° та виключений rep 11 на 127.8°)
все ще вище 110° — але це тепер правдоподібно справжня per-rep варіація кута контакту на
непротокольних кадрах, а не артефакт семплювання, і одне з цих двох повторень (11) уже
виключається автоматично. Це фінальне, шаблонне (shipped) виведення; прохід 100 мс
збережено тут лише як історію відтворюваності, а не як альтернативного кандидата.

## Відбір повторень (фінальний — прохід 17мс/full-fps)

- Виявлено сирих: 22 · після ForwardStrokeFilter: 15 · після RepFilter: 15 (додаткового
  усунення бандингом немає) · після LocomotionFilter (stationary): 15 · залишено фінально
  (після 2σ-відсіювання): **12** · виключено як викиди: **[0, 11, 12]**.
- Візуальна перевірка (skill `visualize-pose`, пікові кадри): усі 15 пікових кадрів цього
  проходу було **відрендерено** у
  `tmp/shipped_baseline_review/fullfps/rep_<i>_frame_<peakFrame>.png`, але фактично **відкрито
  й переглянуто** лише 3 — повторення 1, 7 і 11 (rep 7 незалежно перевідкрив і оркестратор) —
  прицільно на два повторення, чий `elbow_angle` усе ще вище 110° (7 і 11), плюс одне
  повторення з низьким ліктем (1) для порівняння. Цей прохід **не** повторює свіжий повний
  перегляд усіх 15 кадрів; такий повний перегляд уже було зроблено один раз, на PNG проходу
  100мс (див. «Історія виведення» вище), які показують те саме вихідне відео й ту саму
  постановку камери — висновок, що не перевідкриті кадри цього проходу поділяють ту саму
  майже-фронтальну постановку, спирається саме на цю базу «те саме вихідне відео», а не на
  другий незалежний перегляд. З 3 фактично відкритих кадрів: піковий кадр rep 11 явно показує
  ракетку, виведену вбік на рівні стегна, з розмиттям від руху — інша фаза удару, ніж поза
  «ракетка біля обличчя», яку видно в rep 1 і в більшості кадрів проходу 100мс — тоді як
  піковий кадр rep 7 показує ракетку біля обличчя, подібно до повторень із низьким/середнім
  ліктем, попри високе числове значення elbow_angle. Rep 11 уже виключено автоматичним 2σ
  пайплайном; rep 7 залишається в `metricStats` як правдоподібний реальний варіант контакту
  під високим кутом, без ручного «латання» (згідно з інструкцією брифу «один відтворюваний
  запуск»).
- Відхилення від чистого автоматичного відсіювання: **жодних** — повторне виведення на
  full-fps закрило потребу в ньому; жодного ручного виключення повторень поверх власного
  виводу `DrillCalibrator` не застосовувалося.

## Числа (фінальні — прохід 17мс/full-fps)

```
=== ShippedBaseline derivation: andrii_1 (MediaPipe-lite) ===
createdAtMs candidate (paste literal): 1785249184303
raw detected=22 forward=15 banded=15 stationary=15
rep[0] peakFrame=67 startFrame=29 endFrame=109 yaw=90.0 shoulder_angle=26.0, knee_bend=177.6, torso_lean=4.9, elbow_angle=32.9, follow_through_angle_2d=167.0, stroke_speed=11.2, coil_ratio=1.1
rep[1] peakFrame=145 startFrame=123 endFrame=183 yaw=41.3 elbow_angle=33.7, shoulder_angle=23.3, knee_bend=174.6, torso_lean=3.4, follow_through_angle_2d=166.3, stroke_speed=10.3, coil_ratio=2.3
rep[2] peakFrame=221 startFrame=204 endFrame=251 yaw=50.8 shoulder_angle=49.3, knee_bend=176.1, torso_lean=5.8, elbow_angle=101.9, follow_through_angle_2d=169.4, stroke_speed=10.5, coil_ratio=1.5
rep[3] peakFrame=289 startFrame=273 endFrame=326 yaw=49.2 shoulder_angle=29.0, knee_bend=176.7, torso_lean=3.7, elbow_angle=40.4, follow_through_angle_2d=167.7, stroke_speed=9.8, coil_ratio=1.0
rep[4] peakFrame=356 startFrame=342 endFrame=394 yaw=46.5 shoulder_angle=24.6, knee_bend=176.3, torso_lean=2.9, elbow_angle=37.5, follow_through_angle_2d=166.6, stroke_speed=9.7, coil_ratio=1.0
rep[5] peakFrame=424 startFrame=410 endFrame=438 yaw=43.7 shoulder_angle=34.2, knee_bend=172.4, torso_lean=2.8, elbow_angle=52.7, follow_through_angle_2d=67.5, stroke_speed=9.3, coil_ratio=0.9
rep[6] peakFrame=496 startFrame=470 endFrame=535 yaw=43.5 elbow_angle=52.2, shoulder_angle=39.1, knee_bend=173.3, torso_lean=3.4, follow_through_angle_2d=147.4, stroke_speed=10.0, coil_ratio=1.1
rep[7] peakFrame=572 startFrame=552 endFrame=606 yaw=45.7 shoulder_angle=42.3, knee_bend=176.9, torso_lean=2.4, elbow_angle=120.0, follow_through_angle_2d=157.2, stroke_speed=9.9, coil_ratio=1.6
rep[8] peakFrame=639 startFrame=616 endFrame=654 yaw=40.4 elbow_angle=74.1, shoulder_angle=40.2, knee_bend=169.6, torso_lean=6.0, follow_through_angle_2d=79.5, stroke_speed=9.8, coil_ratio=2.0
rep[9] peakFrame=710 startFrame=688 endFrame=728 yaw=42.5 elbow_angle=106.4, shoulder_angle=52.2, knee_bend=171.6, torso_lean=6.4, follow_through_angle_2d=59.2, stroke_speed=9.7, coil_ratio=1.5
rep[10] peakFrame=786 startFrame=761 endFrame=830 yaw=42.0 elbow_angle=69.5, shoulder_angle=44.4, knee_bend=176.6, torso_lean=2.9, follow_through_angle_2d=151.5, stroke_speed=10.1, coil_ratio=1.2
rep[11] peakFrame=856 startFrame=840 endFrame=873 yaw=40.5 elbow_angle=127.8, shoulder_angle=48.3, knee_bend=167.8, torso_lean=6.2, follow_through_angle_2d=54.8, stroke_speed=10.3, coil_ratio=2.1
rep[12] peakFrame=929 startFrame=913 endFrame=939 yaw=45.6 elbow_angle=91.8, shoulder_angle=51.9, knee_bend=170.3, torso_lean=10.4, follow_through_angle_2d=63.7, stroke_speed=11.7, coil_ratio=0.6
rep[13] peakFrame=1002 startFrame=988 endFrame=1041 yaw=30.0 elbow_angle=39.4, shoulder_angle=40.6, knee_bend=176.3, torso_lean=3.4, follow_through_angle_2d=166.7, stroke_speed=10.3, coil_ratio=1.0
rep[14] peakFrame=1071 startFrame=1058 endFrame=1084 yaw=36.2 shoulder_angle=36.1, knee_bend=176.3, torso_lean=2.7, elbow_angle=46.8, follow_through_angle_2d=88.6, stroke_speed=10.2, coil_ratio=0.8
=== Derived PersonalBaseline ===
repCount=12 excludedRepIndices=[0, 11, 12] qualityScore=0.7412837894387136
--- metricStats (paste into ShippedBaselines.FOREHAND_ANDRII.metricStats) ---
"elbow_angle" to MetricStats(mean=64.53417587280273, std=29.928821717007263, min=33.66743850708008, max=119.95394134521484, sampleCount=12),
"shoulder_angle" to MetricStats(mean=37.94060389200846, std=9.023364910517047, min=23.295503616333008, max=52.16059112548828, sampleCount=12),
"knee_bend" to MetricStats(mean=174.72368621826172, std=2.431677913758513, min=169.59434509277344, max=176.87957763671875, sampleCount=12),
"torso_lean" to MetricStats(mean=3.8143043319384256, std=1.3922928139415498, min=2.423185110092163, max=6.360828638076782, sampleCount=12),
"follow_through_angle_2d" to MetricStats(mean=132.2988166809082, std=44.34245701413729, min=59.16209411621094, max=169.4313201904297, sampleCount=12),
"stroke_speed" to MetricStats(mean=9.967092275619507, std=0.33454233928057225, min=9.307697296142578, max=10.531898498535156, sampleCount=12),
"coil_ratio" to MetricStats(mean=1.322065035502116, std=0.4782544112683285, min=0.7653630375862122, max=2.3219175338745117, sampleCount=12),
--- phaseDurationsMs (paste into ShippedBaselines.FOREHAND_ANDRII.phaseDurationsMs) ---
"forward_swing_ms" to MetricStats(mean=320.1666666666667, std=80.01117346213513, min=221.0, max=442.0, sampleCount=12),
"stroke_total_ms" to MetricStats(mean=828.75, std=230.27143074680762, min=442.0, max=1173.0, sampleCount=12),
```

Кандидат `createdAtMs` для Task B: `1785249184303` (це прогон, чиї числа вставлені в
`ShippedBaselines.FOREHAND_ANDRII` — замінює кандидата з проходу 100мс).

Усі 7 `DrillMetrics.ALL_KEYS` мають `sampleCount=12` у цьому проході (на відміну від проходу
100мс, де `follow_through_angle_2d` мав `sampleCount=9` через 2 повторення без значення) —
кожне залишене повторення має вимірне значення для кожної метрики на full fps.

## Замінено — числа першого проходу (інтервал 100 мс, збережено лише як запис відтворюваності)

Команда: `.venv/bin/python scripts/poses/export_poses_mediapipe.py Videos/andrii_1/andrii_1.mp4
--model lite` (без `--interval`, дефолт скрипта 100мс). Виявлено сирих: 21 · forward: 15 ·
banded: 15 · stationary: 15 · залишено: 11 · виключено: `[0, 1, 6, 8]` ·
`qualityScore=0.6695`. Компактний блок `metricStats`/`repCount`/`excludedRepIndices`/
`qualityScore` для цього проходу вбудовано в «Історію виведення» вище — цей документ
самодостатній, нічого потрібного для цього проходу не лежить лише поза ним. **Не вставляйте
ці числа в `ShippedBaselines.kt`**; використовуйте фінальні числа (17мс) вище.
