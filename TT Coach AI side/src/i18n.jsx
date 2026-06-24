// i18n — language dictionary + location-based detection.
// Languages: en (default), uk (Ukrainian), es (Spanish), zh (Chinese).
// t() reads the active language from window.__LANG (set by <App/>), so a
// re-render of the tree picks up the current strings. Segmented headings
// (with inline emphasis) are stored as arrays and rendered via <RichText/>.

const LANGS = [
  { code: 'en', native: 'English', short: 'EN' },
  { code: 'uk', native: 'Українська', short: 'УК' },
  { code: 'es', native: 'Español', short: 'ES' },
  { code: 'zh', native: '中文', short: '中' },
];

const DICT = {
  // ---------- NAV ----------
  'nav.how':      { en: 'How it works', uk: 'Як це працює', es: 'Cómo funciona', zh: '工作原理' },
  'nav.features': { en: 'Features', uk: 'Можливості', es: 'Funciones', zh: '功能' },
  'nav.proof':    { en: 'Proof', uk: 'Докази', es: 'Pruebas', zh: '实证' },
  'nav.getApp':   { en: 'Get the app', uk: 'Завантажити', es: 'Descargar app', zh: '获取应用' },

  // ---------- HERO ----------
  'hero.pill':   { en: 'Live coaching · now on Android', uk: 'Тренування наживо · вже на Android', es: 'Coaching en vivo · ya en Android', zh: '实时指导 · 现已登陆 Android' },
  'hero.h1a':    { en: 'A coach', uk: 'Тренер,', es: 'Un entrenador', zh: '一位教练' },
  'hero.h1b':    { en: 'that plays', uk: 'що говорить', es: 'que te habla', zh: '在你耳边' },
  'hero.h1c':    { en: 'in your ear.', uk: 'у тебе у вусі.', es: 'al oído.', zh: '实时低语。' },
  'hero.sub':    {
    en: 'TT\u00a0Coach\u00a0AI watches every stroke you play and whispers corrections in real time — so you fix the elbow on rep 4, not rep 240.',
    uk: 'TT\u00a0Coach\u00a0AI стежить за кожним твоїм ударом і підказує виправлення в реальному часі — щоб ти виправив лікоть на 4-му повторенні, а не на 240-му.',
    es: 'TT\u00a0Coach\u00a0AI observa cada golpe que juegas y te susurra correcciones en tiempo real — para que corrijas el codo en la repetición 4, no en la 240.',
    zh: 'TT\u00a0Coach\u00a0AI 观察你的每一次击球，实时在耳边轻声纠正——让你在第 4 次就改对手肘，而不是第 240 次。',
  },
  'hero.ios':     { en: 'Get on iOS', uk: 'Для iOS', es: 'iOS', zh: 'iOS 版' },
  'hero.android': { en: 'Get on Android', uk: 'Завантажити для Android', es: 'Descargar para Android', zh: '下载 Android 版' },
  'hero.check1':  { en: "Phone in a stand, that's it", uk: 'Телефон на підставці — і все', es: 'Pon el móvil en un soporte, ya está', zh: '手机往支架上一放，就这么简单' },
  'hero.check2':  { en: 'Works on-device', uk: 'Працює на пристрої', es: 'Funciona en el dispositivo', zh: '完全在本地运行' },
  'hero.check3':  { en: 'Free for 14 days', uk: '14 днів безкоштовно', es: 'Gratis 14 días', zh: '免费试用 14 天' },
  'hero.scroll':  { en: 'Scroll to see how it works', uk: 'Гортай, щоб побачити, як це працює', es: 'Desplázate para ver cómo funciona', zh: '向下滑动了解工作原理' },

  // hero message angle — OUTCOME
  'hero.o.pill':  { en: 'Real-time form correction', uk: 'Корекція техніки в реальному часі', es: 'Corrección de técnica en tiempo real', zh: '实时动作纠正' },
  'hero.o.h1a':   { en: 'Stop grooving', uk: 'Досить закріплювати', es: 'Deja de fijar', zh: '别再把同一个' },
  'hero.o.h1b':   { en: 'the same', uk: 'ту саму', es: 'el mismo', zh: '错误动作' },
  'hero.o.h1c':   { en: 'mistake.', uk: 'помилку.', es: 'error.', zh: '练进肌肉。' },
  'hero.o.sub':   {
    en: 'Players spend years drilling errors into muscle memory. TT\u00a0Coach\u00a0AI catches the elbow, the hip, the paddle face — and fixes it the rep it happens, not 240 reps later.',
    uk: 'Гравці роками закріплюють помилки в м’язовій пам’яті. TT\u00a0Coach\u00a0AI ловить лікоть, стегно, кут ракетки — і виправляє це тим самим повтором, а не через 240.',
    es: 'Los jugadores pasan años fijando errores en la memoria muscular. TT\u00a0Coach\u00a0AI detecta el codo, la cadera, la pala — y lo corrige en la misma repetición, no 240 después.',
    zh: '球员花数年把错误练进肌肉记忆。TT\u00a0Coach\u00a0AI 抓住手肘、髋部、拍面——在出错的那一拍就纠正，而不是 240 拍之后。',
  },

  // hero message angle — PROOF
  'hero.p.pill':  { en: '4.8 ★ on Google Play', uk: '4.8 ★ у Google Play', es: '4.8 ★ en Google Play', zh: 'Google Play 4.8 ★' },
  'hero.p.h1a':   { en: 'See what your', uk: 'Побач те, чого', es: 'Ve lo que tu', zh: '看见镜子' },
  'hero.p.h1b':   { en: 'mirror', uk: 'дзеркало', es: 'espejo', zh: '看不见' },
  'hero.p.h1c':   { en: "can't.", uk: 'не покаже.', es: 'no ve.', zh: '的细节。' },
  'hero.p.sub':   {
    en: 'On-device pose tracking watches 17 joints, 120 times a second, and tells you out loud the one thing to fix next — the feedback a mirror never could give.',
    uk: 'Відстеження пози на пристрої стежить за 17 суглобами 120 разів на секунду й вголос підказує, що виправити далі — те, чого не дасть жодне дзеркало.',
    es: 'El seguimiento de pose en el dispositivo vigila 17 articulaciones 120 veces por segundo y te dice en voz alta qué corregir — lo que un espejo jamás podría.',
    zh: '本地姿态追踪每秒 120 次监测 17 个关节，并出声告诉你下一处该改什么——这是镜子永远给不了的反馈。',
  },

  // hero rating microproof
  'proof.loved':  { en: 'Loved by 2,400+ players', uk: 'Подобається 2\u202F400+ гравцям', es: 'Adorado por 2,400+ jugadores', zh: '2,400+ 名球员喜爱' },
  'proof.onPlay': { en: 'on Google Play', uk: 'у Google Play', es: 'en Google Play', zh: '来自 Google Play' },

  // credibility band
  'social.c1.l': { en: 'Google Play rating', uk: 'Рейтинг Google Play', es: 'Valoración en Google Play', zh: 'Google Play 评分' },
  'social.c2.l': { en: 'active players', uk: 'активних гравців', es: 'jugadores activos', zh: '活跃球员' },
  'social.c3.l': { en: 'strokes analyzed', uk: 'ударів проаналізовано', es: 'golpes analizados', zh: '击球已分析' },
  'social.c4.l': { en: 'cloud lag', uk: 'затримка хмари', es: 'latencia en la nube', zh: '云端延迟' },

  // testimonials
  'voices.kicker':  { en: '· From the table', uk: '· Від столу', es: '· Desde la mesa', zh: '· 来自球台' },
  'voices.heading': {
    en: 'Players stopped fighting their own muscle memory.',
    uk: 'Гравці перестали боротися з власною м’язовою пам’яттю.',
    es: 'Los jugadores dejaron de pelear con su memoria muscular.',
    zh: '球员不再与自己的肌肉记忆较劲。',
  },
  'voices.q1':      { en: 'I\u2019d been told \u201cdrop your elbow\u201d for ten years. Hearing it the exact rep it happened is what finally made it stick. Two weeks in, my loop is unrecognizable.' },
  'voices.q1.name': { en: 'Marko D.' },
  'voices.q1.role': { en: 'County-level player', uk: 'Гравець обласного рівня', es: 'Jugador de nivel provincial', zh: '地区级球员' },
  'voices.q2':      { en: 'I play twice a week with nobody to watch me. It\u2019s the first time I\u2019ve felt myself actually getting better instead of just hitting.' },
  'voices.q2.name': { en: 'Priya N.' },
  'voices.q2.role': { en: 'Recreational player', uk: 'Аматор', es: 'Jugadora aficionada', zh: '业余球员' },
  'voices.q3':      { en: 'I set the reference to my own strokes and let students drill midweek. They show up Saturday already corrected.' },
  'voices.q3.name': { en: 'Coach Lin' },
  'voices.q3.role': { en: 'Junior academy', uk: 'Юнацька академія', es: 'Academia juvenil', zh: '青少年学院' },

  // ---------- SHARED coach / strokes ----------
  'coach.speaking': { en: 'Coach · speaking now', uk: 'Тренер · говорить зараз', es: 'Entrenador · hablando ahora', zh: '教练 · 正在提示' },
  'coach.tip':      { en: '"Keep your elbow closer to the body."', uk: '«Тримай лікоть ближче до корпусу.»', es: '«Mantén la mano más cerca del codo.»', zh: '"手离手肘再近一些。"' },
  'strokes.last10': { en: 'Last 10 strokes', uk: 'Останні 10 ударів', es: 'Últimos 10 golpes', zh: '最近 10 次击球' },
  'word.good':      { en: 'good', uk: 'влучних', es: 'buenos', zh: '到位' },
  'word.off':       { en: 'off', uk: 'мимо', es: 'fallos', zh: '偏差' },

  // ---------- PHONE ----------
  'phone.live':    { en: 'Live · forehand loop', uk: 'Наживо · форхенд-луп', es: 'En vivo · loop de derecha', zh: '实时 · 正手弧圈' },
  'phone.keepGoing': { en: 'Keep going', uk: 'Так тримати', es: 'Sigue así', zh: '继续保持' },
  'phone.nextIn':  { en: 'next in 7s', uk: 'далі через 7с', es: 'siguiente en 7s', zh: '7 秒后提示' },
  'phone.strokes': { en: 'Strokes', uk: 'Удари', es: 'Golpes', zh: '击球' },
  'phone.match':   { en: 'Match', uk: 'Збіг', es: 'Coincidencia', zh: '匹配度' },
  'phone.rep':     { en: 'Forehand drive · rep 23/50', uk: 'Форхенд-драйв · повтор 23/50', es: 'Drive de derecha · rep 23/50', zh: '正手攻球 · 第 23/50 次' },
  'phone.tab.train':   { en: 'Train', uk: 'Тренування', es: 'Entrena', zh: '训练' },
  'phone.tab.drills':  { en: 'Drills', uk: 'Вправи', es: 'Ejercicios', zh: '练习' },
  'phone.tab.stats':   { en: 'Stats', uk: 'Статистика', es: 'Stats', zh: '数据' },
  'phone.tab.profile': { en: 'Profile', uk: 'Профіль', es: 'Perfil', zh: '我的' },

  // ---------- HOW IT WORKS ----------
  'how.heading': {
    en: [{ t: 'Four steps between ' }, { t: 'frustrated', em: 'navy' }, { t: ' and ' }, { t: 'fixed', em: 'sage' }, { t: '.' }],
    uk: [{ t: 'Чотири кроки від ' }, { t: 'розчарування', em: 'navy' }, { t: ' до ' }, { t: 'результату', em: 'sage' }, { t: '.' }],
    es: [{ t: 'Cuatro pasos entre la ' }, { t: 'frustración', em: 'navy' }, { t: ' y la ' }, { t: 'solución', em: 'sage' }, { t: '.' }],
    zh: [{ t: '从' }, { t: '沮丧', em: 'navy' }, { t: '到' }, { t: '搞定', em: 'sage' }, { t: '，只需四步。' }],
  },
  'how.stepLabel': { en: 'STEP', uk: 'КРОК', es: 'PASO', zh: '步骤' },
  'how.s1.tag':   { en: 'Set up', uk: 'Налаштуй', es: 'Coloca', zh: '架设' },
  'how.s1.title': { en: 'Prop your phone on the side of the table.', uk: 'Постав телефон збоку від столу.', es: 'Apoya el móvil al lado de la mesa.', zh: '把手机立在球台一侧。' },
  'how.s1.body':  { en: 'Any phone stand. Any angle, really — the model figures out where you are in about three seconds.', uk: 'Будь-яка підставка. Будь-який кут, справді — модель визначить, де ти, приблизно за три секунди.', es: 'Cualquier soporte. Cualquier ángulo, en serio — el modelo descubre dónde estás en unos tres segundos.', zh: '任何手机支架，任何角度都行——模型大约三秒就能找到你的位置。' },
  'how.s2.tag':   { en: 'Play', uk: 'Грай', es: 'Juega', zh: '开打' },
  'how.s2.title': { en: 'Play. We watch 17 joints, 120 times a second.', uk: 'Грай. Ми стежимо за 17 суглобами 120 разів на секунду.', es: 'Juega. Vigilamos 17 articulaciones 120 veces por segundo.', zh: '开打。我们每秒 120 次追踪 17 个关节。' },
  'how.s2.body':  { en: 'While you rally, TT Coach AI compares your body mechanics stroke-by-stroke against a reference you choose — your coach, a pro, or your own best session.', uk: 'Поки ти граєш, TT Coach AI порівнює механіку твого тіла удар за ударом з обраним еталоном — твоїм тренером, профі чи твоєю найкращою сесією.', es: 'Mientras peloteas, TT Coach AI compara tu mecánica golpe a golpe con la referencia que elijas — tu entrenador, un pro o tu mejor sesión.', zh: '在你来回对打时，TT Coach AI 逐拍将你的身体动作与你选定的参照对比——你的教练、职业选手，或你自己的最佳一场。' },
  'how.s3.tag':   { en: 'Listen', uk: 'Слухай', es: 'Escucha', zh: '倾听' },
  'how.s3.title': { en: 'A voice in your ear nudges you back on track.', uk: 'Голос у вусі повертає тебе на правильний шлях.', es: 'Una voz al oído te devuelve al buen camino.', zh: '耳边的声音把你拉回正轨。' },
  'how.s3.body':  { en: 'Calm, energetic, or text-only. Tune how chatty the coach is — one tip every 5 seconds, or only when something really drifts.', uk: 'Спокійний, енергійний чи лише текст. Налаштуй, наскільки балакучий тренер — підказка щоп’ять секунд або лише коли щось справді не так.', es: 'Tranquilo, enérgico o solo texto. Ajusta cuánto habla el coach — un consejo cada 5 segundos, o solo cuando algo se desvía de verdad.', zh: '平静、激励，或仅文字。调节教练的话多话少——每 5 秒一条提示，或只在真正偏离时才出声。' },
  'how.s4.tag':   { en: 'Review', uk: 'Аналізуй', es: 'Revisa', zh: '回顾' },
  'how.s4.title': { en: 'After the session, see exactly what changed.', uk: 'Після сесії побач, що саме змінилося.', es: 'Tras la sesión, ve exactamente qué cambió.', zh: '课后，清楚看到到底改变了什么。' },
  'how.s4.body':  { en: 'One thing that worked. One thing to focus on next time. Joint-level numbers if you want the detail.', uk: 'Одне, що спрацювало. Одне, на чому зосередитись наступного разу. Цифри по суглобах, якщо хочеш деталей.', es: 'Una cosa que funcionó. Una cosa para enfocar la próxima vez. Cifras por articulación si quieres el detalle.', zh: '一处做得好。一处下次重点改进。想看细节，还有逐关节的数据。' },
  'how.setup.locked': { en: 'Locked on', uk: 'Захоплено', es: 'Enfocado', zh: '已锁定' },
  'how.play.refVsYou': { en: 'REFERENCE vs YOU', uk: 'ЕТАЛОН vs ТИ', es: 'REFERENCIA vs TÚ', zh: '参照 vs 你' },
  'how.play.reference': { en: 'Reference', uk: 'Еталон', es: 'Referencia', zh: '参照' },
  'how.play.you':  { en: 'You', uk: 'Ти', es: 'Tú', zh: '你' },
  'how.listen.title': { en: 'Drill settings → Coach feedback', uk: 'Налаштування вправи → Зворотний зв’язок', es: 'Ajustes del drill → Feedback del coach', zh: '训练设置 → 教练反馈' },
  'how.listen.tipEvery': { en: 'Tip every', uk: 'Підказка кожні', es: 'Un consejo cada', zh: '提示间隔' },
  'how.listen.sec': { en: 'sec', uk: 'с', es: 'seg', zh: '秒' },
  'how.listen.chatty': { en: 'chatty', uk: 'балакучий', es: 'hablador', zh: '频繁' },
  'how.listen.quiet': { en: 'quiet', uk: 'тихий', es: 'silencioso', zh: '安静' },
  'how.listen.calm': { en: 'Calm', uk: 'Спокійний', es: 'Tranquilo', zh: '平静' },
  'how.listen.energetic': { en: 'Energetic', uk: 'Енергійний', es: 'Enérgico', zh: '激励' },
  'how.listen.textOnly': { en: 'Text only', uk: 'Лише текст', es: 'Solo texto', zh: '仅文字' },
  'how.review.matchRef': { en: 'Match vs your reference', uk: 'Збіг з еталоном', es: 'Coincidencia con tu referencia', zh: '与参照的匹配度' },
  'how.review.vsLast': { en: 'vs last session', uk: 'проти минулої сесії', es: 'vs sesión anterior', zh: '对比上次' },
  'how.review.elbow': { en: 'Elbow angle', uk: 'Кут ліктя', es: 'Ángulo del codo', zh: '手肘角度' },
  'how.review.hip': { en: 'Hip rotation', uk: 'Поворот стегон', es: 'Rotación de cadera', zh: '髋部转动' },
  'how.review.knee': { en: 'Knee bend', uk: 'Згин коліна', es: 'Flexión de rodilla', zh: '膝盖弯曲' },

  // ---------- FEATURES ----------
  'feat.pill': { en: 'Features', uk: 'Можливості', es: 'Funciones', zh: '功能' },
  'feat.heading': { en: 'Three tools, one pocket coach.', uk: 'Три інструменти — один кишеньковий тренер.', es: 'Tres herramientas, un coach de bolsillo.', zh: '三个工具，一位口袋教练。' },
  'feat.intro': {
    en: 'We shipped what a player actually uses at the table — and cut everything else. No gamification. No streaks. No trophies.',
    uk: 'Ми залишили те, чим гравець справді користується за столом — і прибрали все інше. Без гейміфікації. Без серій. Без трофеїв.',
    es: 'Incluimos lo que un jugador usa de verdad en la mesa — y quitamos todo lo demás. Sin gamificación. Sin rachas. Sin trofeos.',
    zh: '我们只保留了球员在球台前真正会用的功能——其余统统砍掉。没有游戏化。没有连胜。没有奖杯。',
  },
  'feat.f1.title': { en: 'Technique detection', uk: 'Виявлення техніки', es: 'Detección de técnica', zh: '技术检测' },
  'feat.f1.body': {
    en: 'Pose estimation on-device spots the small deviations a mirror never could — elbow drift, hip under-rotation, open paddle face — and tells you which one to fix first.',
    uk: 'Оцінка пози просто на пристрої помічає дрібні відхилення, яких не покаже жодне дзеркало — зміщення ліктя, недостатній поворот стегон, відкрита ракетка — і підказує, що виправити першим.',
    es: 'La estimación de pose en el dispositivo detecta las pequeñas desviaciones que un espejo jamás vería — codo desviado, cadera poco girada, pala abierta — y te dice cuál corregir primero.',
    zh: '本地姿态估计能捕捉镜子永远看不到的细微偏差——手肘漂移、髋部转动不足、拍面过开——并告诉你该先纠正哪一个。',
  },
  'feat.f1.chip': { en: 'Live', uk: 'Наживо', es: 'En vivo', zh: '实时' },
  'feat.f2.title': { en: 'Drill customization', uk: 'Налаштування вправ', es: 'Drills a tu medida', zh: '自定义训练' },
  'feat.f2.body': {
    en: 'Build a drill that only counts the strokes you care about. Pick a stroke, pick a reference, pick how strict the coach should be. Share it with the group chat.',
    uk: 'Створи вправу, що рахує лише потрібні тобі удари. Обери удар, еталон і наскільки суворим буде тренер. Поділись у груповому чаті.',
    es: 'Crea un drill que solo cuente los golpes que te importan. Elige un golpe, una referencia y qué tan estricto será el coach. Compártelo en el chat del grupo.',
    zh: '创建只统计你在意的击球的训练。选择一种击球、一个参照、教练的严格程度，然后分享到群聊。',
  },
  'feat.f2.chip': { en: 'Build', uk: 'Створити', es: 'Crear', zh: '创建' },
  'feat.f3.title': { en: 'Session review', uk: 'Аналіз сесії', es: 'Revisión de sesión', zh: '课后回顾' },
  'feat.f3.body': {
    en: 'Every rep is saved. Scrub the timeline, compare against your own best, and watch the exact strokes the coach flagged — with one thing working and one thing to focus on.',
    uk: 'Кожен повтор збережено. Прогортай таймлайн, порівняй із власним рекордом і переглянь саме ті удари, які виділив тренер — що вдалося й над чим попрацювати.',
    es: 'Cada repetición queda guardada. Recorre la línea de tiempo, compárate con tu mejor marca y revisa justo los golpes que el coach marcó — con algo que funciona y algo en qué enfocarte.',
    zh: '每一次挥拍都被保存。拖动时间轴，和自己的最佳表现对比，回看教练标记的那些击球——一处做得好，一处需改进。',
  },
  'feat.f3.chip': { en: 'Review', uk: 'Огляд', es: 'Revisar', zh: '回顾' },
  'feat.drill.new': { en: 'NEW DRILL', uk: 'НОВА ВПРАВА', es: 'NUEVO DRILL', zh: '新训练' },
  'feat.drill.name': { en: 'Crosscourt forehand · 50', uk: 'Форхенд по діагоналі · 50', es: 'Derecha cruzada · 50', zh: '正手斜线 · 50' },
  'feat.drill.stroke': { en: 'Stroke', uk: 'Удар', es: 'Golpe', zh: '击球' },
  'feat.drill.strokeVal': { en: 'Forehand loop', uk: 'Форхенд-луп', es: 'Loop de derecha', zh: '正手弧圈' },
  'feat.drill.reference': { en: 'Reference', uk: 'Еталон', es: 'Referencia', zh: '参照' },
  'feat.drill.refVal': { en: 'My best (Mar 8)', uk: 'Мій рекорд (8 бер.)', es: 'Mi mejor (8 mar)', zh: '我的最佳（3月8日）' },
  'feat.drill.strictness': { en: 'Strictness', uk: 'Суворість', es: 'Exigencia', zh: '严格度' },
  'feat.drill.start': { en: 'Start drill →', uk: 'Почати вправу →', es: 'Empezar drill →', zh: '开始训练 →' },
  'feat.rev.session': { en: 'SESSION · TUE 10:24', uk: 'СЕСІЯ · ВТ 10:24', es: 'SESIÓN · MAR 10:24', zh: '课程 · 周二 10:24' },
  'feat.rev.stroke27': { en: 'Stroke 27 · "Elbow drift"', uk: 'Удар 27 · «Зміщення ліктя»', es: 'Golpe 27 · «Codo desviado»', zh: '第 27 拍 · "手肘漂移"' },
  'feat.rev.working': { en: 'Working:', uk: 'Працює:', es: 'Funciona:', zh: '做得好：' },
  'feat.rev.workingVal': { en: 'hip rotation within 3°', uk: 'поворот стегон у межах 3°', es: 'rotación de cadera dentro de 3°', zh: '髋部转动误差 3° 以内' },
  'feat.rev.next': { en: 'Next:', uk: 'Далі:', es: 'Siguiente:', zh: '下一步：' },
  'feat.rev.nextVal': { en: 'lower arm on backswing', uk: 'нижче руку на замаху', es: 'baja el brazo en el backswing', zh: '引拍时手臂放低' },

  // ---------- STATS ----------
  'stats.kicker': { en: '· Under the hood', uk: '· Під капотом', es: '· Bajo el capó', zh: '· 技术内幕' },
  'stats.heading': {
    en: [{ t: 'Built by people who cared about the ' }, { t: '40ms', em: 'amber' }, { t: ' between seeing a mistake and hearing a fix.' }],
    uk: [{ t: 'Зроблено людьми, яким важливі ті ' }, { t: '40 мс', em: 'amber' }, { t: ', що між помилкою та підказкою.' }],
    es: [{ t: 'Hecho por gente a la que le importaban los ' }, { t: '40 ms', em: 'amber' }, { t: ' entre ver un error y oír la corrección.' }],
    zh: [{ t: '由在乎从看见错误到听见纠正之间那 ' }, { t: '40 毫秒', em: 'amber' }, { t: ' 的人打造。' }],
  },
  'stats.s1.label': { en: 'Joints tracked', uk: 'Суглобів відстежується', es: 'Articulaciones', zh: '追踪关节' },
  'stats.s1.sub':   { en: 'Full-body pose estimation', uk: 'Оцінка пози всього тіла', es: 'Pose de cuerpo completo', zh: '全身姿态估计' },
  'stats.s2.label': { en: 'Frames per second', uk: 'Кадрів за секунду', es: 'Cuadros por segundo', zh: '每秒帧数' },
  'stats.s2.sub':   { en: 'Nothing gets missed', uk: 'Нічого не пропустимо', es: 'No se escapa nada', zh: '不遗漏任何细节' },
  'stats.s3.label': { en: 'Cloud round-trip', uk: 'Затримка хмари', es: 'Ida y vuelta a la nube', zh: '云端往返' },
  'stats.s3.sub':   { en: 'Runs on-device', uk: 'Працює на пристрої', es: 'Funciona en el dispositivo', zh: '完全本地运行' },
  'stats.s4.label': { en: 'Free to try', uk: 'Безкоштовний тест', es: 'Prueba gratis', zh: '免费试用' },
  'stats.s4.sub':   { en: 'No card required', uk: 'Картка не потрібна', es: 'Sin tarjeta', zh: '无需信用卡' },

  // ---------- CTA ----------
  'cta.heading': { en: 'Stop guessing what went wrong.', uk: 'Досить гадати, що пішло не так.', es: 'Deja de adivinar qué salió mal.', zh: '别再猜哪里出了错。' },
  'cta.body': {
    en: "Download TT\u00a0Coach AI, prop your phone up, and play. A real fix in your first session — or we'll refund you.",
    uk: 'Завантаж TT\u00a0Coach AI, постав телефон і грай. Справжнє виправлення вже на першій сесії.',
    es: 'Descarga TT\u00a0Coach AI, apoya el móvil y juega. Una corrección real en tu primera sesión — o te devolvemos el dinero.',
    zh: '下载 TT\u00a0Coach AI，架好手机，开打。第一节课就有真实改进——否则全额退款。',
  },
  'cta.scan': { en: 'SCAN TO DOWNLOAD', uk: 'СКАНУЙ ДЛЯ ЗАВАНТАЖЕННЯ', es: 'ESCANEA PARA DESCARGAR', zh: '扫码下载' },
  'cta.tagline': { en: 'Fix the elbow.', uk: 'Виправ лікоть.', es: 'Corrige el codo.', zh: '改对手肘。' },
  'cta.privacy': { en: 'Privacy', uk: 'Конфіденційність', es: 'Privacidad', zh: '隐私' },
  'cta.terms':   { en: 'Terms', uk: 'Умови', es: 'Términos', zh: '条款' },
  'cta.support': { en: 'Support', uk: 'Підтримка', es: 'Soporte', zh: '支持' },
  'theme.system': { en: 'System', uk: 'Системна', es: 'Sistema', zh: '跟随系统' },
  'theme.light':  { en: 'Light', uk: 'Світла', es: 'Claro', zh: '浅色' },
  'theme.dark':   { en: 'Dark', uk: 'Темна', es: 'Oscuro', zh: '深色' },
};

// Detect default language from the user's LOCATION (timezone), then browser
// languages, then fall back to English. A manual choice is stored in
// localStorage and always wins.
function detectLang() {
  try {
    const saved = localStorage.getItem('ttc_lang');
    if (saved && LANGS.some((l) => l.code === saved)) return saved;
  } catch (e) {}

  const tz = (Intl.DateTimeFormat().resolvedOptions().timeZone || '');
  if (/Kiev|Kyiv|Ukraine|Zaporozhye|Uzhgorod|Simferopol/i.test(tz)) return 'uk';
  if (/Shanghai|Chongqing|Urumqi|Harbin|Hong_Kong|Macau|Taipei|Chungking/i.test(tz)) return 'zh';
  if (/Madrid|Ceuta|Canary|Mexico|Argentina|Buenos_Aires|Bogota|Lima|Santiago|Caracas|Montevideo|Guayaquil|Costa_Rica|Tegucigalpa|El_Salvador|Panama|Asuncion|La_Paz|Managua|Havana|Santo_Domingo/i.test(tz)) return 'es';

  const navs = (navigator.languages && navigator.languages.length) ? navigator.languages : [navigator.language || 'en'];
  for (const l of navs) {
    const base = String(l).toLowerCase().split('-')[0];
    if (LANGS.some((x) => x.code === base)) return base;
  }
  return 'en';
}

// Active-language string lookup. Returns the raw value (string OR segment
// array for rich headings). vars interpolates {token} placeholders.
function t(key, vars) {
  const lang = window.__LANG || 'en';
  const entry = DICT[key];
  if (!entry) return key;
  let val = entry[lang] != null ? entry[lang] : entry.en;
  if (vars && typeof val === 'string') {
    val = val.replace(/\{(\w+)\}/g, (m, k) => (vars[k] != null ? vars[k] : m));
  }
  return val;
}

// Render a segmented (emphasis) heading value as inline spans.
const SEG_EM = {
  navy: { color: 'var(--navy)', fontStyle: 'italic', fontWeight: 400 },
  sage: { color: 'var(--sage-2)', fontStyle: 'italic', fontWeight: 400 },
  amber: { color: '#F5B547', fontStyle: 'italic', fontWeight: 400 },
};
function RichText({ k }) {
  const segs = t(k);
  if (!Array.isArray(segs)) return <>{segs}</>;
  return (
    <>
      {segs.map((s, i) => s.em
        ? <span key={i} style={SEG_EM[s.em]}>{s.t}</span>
        : <React.Fragment key={i}>{s.t}</React.Fragment>)}
    </>
  );
}

Object.assign(window, { LANGS, DICT, detectLang, t, RichText });
