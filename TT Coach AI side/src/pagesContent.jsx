// Content for the Privacy / Terms / Support sub-pages, in all four languages.
// Shape: each leaf is { en, uk, es, zh }. Legal pages are section arrays;
// support is a FAQ list + contact cards. Kept tight and on-brand:
// the whole product promise is "runs on-device", so privacy leans into that.

const PAGES = {

  // =====================================================================
  privacy: {
    eyebrow: { en: 'Legal', uk: 'Правова інформація', es: 'Legal', zh: '法律' },
    title:   { en: 'Privacy', uk: 'Конфіденційність', es: 'Privacidad', zh: '隐私' },
    updated: { en: 'Last updated · June 2026', uk: 'Оновлено · червень 2026', es: 'Actualizado · junio 2026', zh: '更新于 · 2026 年 6 月' },
    intro: {
      en: 'TT\u00a0Coach\u00a0AI was built so your coaching never has to leave your phone. The camera feed and pose analysis run entirely on-device — we designed the product around collecting as little about you as possible.',
      uk: 'TT\u00a0Coach\u00a0AI створено так, щоб твоє тренування ніколи не залишало телефон. Відеопотік і аналіз пози працюють повністю на пристрої — ми збудували продукт навколо того, щоб збирати про тебе якомога менше.',
      es: 'TT\u00a0Coach\u00a0AI se diseñó para que tu entrenamiento nunca salga del teléfono. La cámara y el análisis de pose funcionan por completo en el dispositivo — construimos el producto en torno a recoger lo mínimo posible sobre ti.',
      zh: 'TT\u00a0Coach\u00a0AI 的设计宗旨，是让你的训练数据永远留在手机里。摄像头画面与姿态分析完全在本地运行——我们围绕"尽可能少收集你的信息"来打造整个产品。',
    },
    sections: [
      {
        h: { en: 'What stays on your device', uk: 'Що залишається на пристрої', es: 'Lo que se queda en tu dispositivo', zh: '留在你设备上的内容' },
        body: [
          { p: {
            en: 'Your camera feed is never uploaded. Pose estimation — the 17 tracked joints, 120 times a second — happens on the phone and is discarded frame-by-frame. Recorded sessions and clips are stored locally unless you explicitly choose to back them up.',
            uk: 'Твій відеопотік ніколи не завантажується на сервер. Оцінка пози — 17 суглобів 120 разів на секунду — відбувається на телефоні й відкидається кадр за кадром. Записані сесії та кліпи зберігаються локально, доки ти сам не вирішиш зробити резервну копію.',
            es: 'Tu cámara nunca se sube. La estimación de pose — 17 articulaciones, 120 veces por segundo — ocurre en el teléfono y se descarta fotograma a fotograma. Las sesiones y clips grabados se guardan en local salvo que elijas respaldarlos.',
            zh: '你的摄像头画面绝不会上传。姿态估计——每秒 120 次追踪 17 个关节——在手机上完成，并逐帧丢弃。录制的课程和片段都保存在本地，除非你主动选择备份。',
          } },
        ],
      },
      {
        h: { en: 'What we collect', uk: 'Що ми збираємо', es: 'Lo que recopilamos', zh: '我们收集什么' },
        body: [
          { p: {
            en: 'To run an account and improve the app, we collect only:',
            uk: 'Щоб працював акаунт і вдосконалювався застосунок, ми збираємо лише:',
            es: 'Para gestionar tu cuenta y mejorar la app, recopilamos solo:',
            zh: '为了运行账户并改进应用，我们只收集：',
          } },
          { ul: {
            en: ['Account basics — your email and subscription status.', 'Anonymous, aggregated metrics — how often features are used, crash reports.', 'Anything you deliberately sync — drills you share, sessions you back up.'],
            uk: ['Базове про акаунт — пошту та статус підписки.', 'Анонімні зведені метрики — як часто використовуються функції, звіти про збої.', 'Те, що ти свідомо синхронізуєш — вправи, якими ділишся, сесії, які зберігаєш у хмару.'],
            es: ['Lo básico de la cuenta — tu correo y estado de suscripción.', 'Métricas anónimas y agregadas — uso de funciones, informes de fallos.', 'Lo que sincronizas a propósito — drills que compartes, sesiones que respaldas.'],
            zh: ['账户基本信息——你的邮箱与订阅状态。', '匿名汇总指标——功能使用频率、崩溃报告。', '你主动同步的内容——你分享的训练、备份的课程。'],
          } },
        ],
      },
      {
        h: { en: 'What we never do', uk: 'Чого ми ніколи не робимо', es: 'Lo que nunca hacemos', zh: '我们绝不会做的事' },
        body: [
          { ul: {
            en: ['Sell or rent your data to anyone.', 'Upload your video without an explicit tap from you.'],
            uk: ['Продавати чи здавати твої дані будь-кому.', 'Завантажувати твоє відео без явного твого дозволу.'],
            es: ['Vender ni alquilar tus datos a nadie.', 'Subir tu vídeo sin que lo toques explícitamente.'],
            zh: ['向任何人出售或出租你的数据。', '在你未明确点击的情况下上传你的视频。'],
          } },
        ],
      },
      {
        h: { en: 'Your controls', uk: 'Твій контроль', es: 'Tu control', zh: '你的掌控' },
        body: [
          { p: {
            en: 'You can export or delete everything from Profile → Data at any time. Deleting your account removes your cloud-synced data within 30 days. On-device data is gone the moment you uninstall.',
            uk: 'Будь-коли можеш експортувати або видалити все в розділі Профіль → Дані. Видалення акаунта стирає синхронізовані в хмарі дані протягом 30 днів. Дані на пристрої зникають у мить видалення застосунку.',
            es: 'Puedes exportar o borrar todo desde Perfil → Datos cuando quieras. Eliminar tu cuenta borra los datos sincronizados en la nube en un plazo de 30 días. Los datos en el dispositivo desaparecen al desinstalar.',
            zh: '你随时可在"我的 → 数据"中导出或删除全部内容。删除账户会在 30 天内清除云端同步数据。卸载应用的那一刻，本地数据即被清除。',
          } },
        ],
      },
      {
        h: { en: 'Contact', uk: 'Контакти', es: 'Contacto', zh: '联系我们' },
        body: [
          { p: {
            en: 'Questions about privacy? Email ttcoachai@gmail.com and a human will reply.',
            uk: 'Питання щодо конфіденційності? Напиши на ttcoachai@gmail.com — відповість жива людина.',
            es: '¿Dudas sobre privacidad? Escribe a ttcoachai@gmail.com y te responderá una persona.',
            zh: '对隐私有疑问？发邮件至 ttcoachai@gmail.com，会有真人回复。',
          } },
        ],
      },
    ],
  },

  // =====================================================================
  terms: {
    eyebrow: { en: 'Legal', uk: 'Правова інформація', es: 'Legal', zh: '法律' },
    title:   { en: 'Terms', uk: 'Умови', es: 'Términos', zh: '条款' },
    updated: { en: 'Last updated · June 2026', uk: 'Оновлено · червень 2026', es: 'Actualizado · junio 2026', zh: '更新于 · 2026 年 6 月' },
    intro: {
      en: 'These terms cover your use of TT\u00a0Coach\u00a0AI. They are written to be read — plain language, no surprises. Using the app means you agree to them.',
      uk: 'Ці умови регулюють твоє користування TT\u00a0Coach\u00a0AI. Вони написані, щоб їх читали — простою мовою, без сюрпризів. Користуючись застосунком, ти з ними погоджуєшся.',
      es: 'Estos términos cubren tu uso de TT\u00a0Coach\u00a0AI. Están escritos para leerse — lenguaje claro, sin sorpresas. Usar la app significa que los aceptas.',
      zh: '本条款涵盖你对 TT\u00a0Coach\u00a0AI 的使用。它们用平实的语言写成，没有意外。使用本应用即表示你同意这些条款。',
    },
    sections: [
      {
        h: { en: 'The service', uk: 'Послуга', es: 'El servicio', zh: '服务内容' },
        body: [
          { p: {
            en: 'TT\u00a0Coach\u00a0AI provides real-time technique feedback for table tennis using on-device pose analysis. It is a training aid, not medical or professional advice. Listen to your body and a qualified coach where it matters.',
            uk: 'TT\u00a0Coach\u00a0AI надає зворотний зв’язок щодо техніки настільного тенісу в реальному часі за допомогою аналізу пози на пристрої. Це тренувальний помічник, а не медична чи професійна порада. Прислухайся до свого тіла та кваліфікованого тренера, коли це важливо.',
            es: 'TT\u00a0Coach\u00a0AI ofrece feedback de técnica en tiempo real para tenis de mesa mediante análisis de pose en el dispositivo. Es una ayuda de entrenamiento, no consejo médico ni profesional. Escucha a tu cuerpo y a un entrenador cualificado cuando importe.',
            zh: 'TT\u00a0Coach\u00a0AI 通过本地姿态分析，为乒乓球提供实时技术反馈。它是训练辅助工具，并非医疗或专业建议。重要时刻，请聆听你的身体和合格教练的意见。',
          } },
        ],
      },
      {
        h: { en: 'Free trial & subscriptions', uk: 'Безкоштовний період і підписки', es: 'Prueba gratis y suscripciones', zh: '免费试用与订阅' },
        body: [
          { p: {
            en: 'The first 14 days are free, no card required. After that a subscription continues access; you can cancel anytime from your app-store account and keep access until the period ends. If your first paid session does not give you a real, usable fix, email us within 14 days for a full refund.',
            uk: 'Перші 14 днів безкоштовні, картка не потрібна. Далі доступ продовжує підписка; скасувати можна будь-коли в акаунті магазину застосунків, зберігши доступ до кінця періоду. Якщо перша платна сесія не дала справжнього, корисного виправлення — напиши нам протягом 14 днів і отримай повне повернення коштів.',
            es: 'Los primeros 14 días son gratis, sin tarjeta. Después, una suscripción mantiene el acceso; puedes cancelar cuando quieras desde tu cuenta de la tienda y conservar el acceso hasta el fin del periodo. Si tu primera sesión de pago no te da una corrección real y útil, escríbenos en 14 días para un reembolso completo.',
            zh: '前 14 天免费，无需信用卡。之后通过订阅继续使用；你可随时在应用商店账户中取消，并保留访问权至当期结束。若你的首次付费课程未能带来真实可用的改进，请在 14 天内来信，我们将全额退款。',
          } },
        ],
      },
      {
        h: { en: 'Acceptable use', uk: 'Допустиме використання', es: 'Uso aceptable', zh: '可接受的使用' },
        body: [
          { p: {
            en: 'Use the app for your own training. Please don’t reverse-engineer it, resell access, or record people who haven’t agreed to be filmed. Drills you share publicly should be your own.',
            uk: 'Користуйся застосунком для власних тренувань. Будь ласка, не займайся зворотним інжинірингом, не перепродавай доступ і не знімай людей, які не погодилися на зйомку. Вправи, якими ділишся публічно, мають бути твоїми власними.',
            es: 'Usa la app para tu propio entrenamiento. Por favor, no hagas ingeniería inversa, no revendas el acceso ni grabes a personas que no han aceptado ser filmadas. Los drills que compartas en público deben ser tuyos.',
            zh: '请将本应用用于你自己的训练。请勿逆向工程、转售访问权限，或拍摄未同意被录制的人。你公开分享的训练应为你原创。',
          } },
        ],
      },
      {
        h: { en: 'Liability', uk: 'Відповідальність', es: 'Responsabilidad', zh: '责任限制' },
        body: [
          { p: {
            en: 'We work hard to keep the app accurate and available, but it is provided “as is”. To the extent the law allows, we aren’t liable for indirect losses, and our total liability is limited to what you paid us in the last 12 months.',
            uk: 'Ми докладаємо зусиль, щоб застосунок був точним і доступним, але він надається «як є». У межах, дозволених законом, ми не відповідаємо за непрямі збитки, а наша сукупна відповідальність обмежена сумою, сплаченою тобою за останні 12 місяців.',
            es: 'Trabajamos para que la app sea precisa y esté disponible, pero se ofrece “tal cual”. En la medida que la ley lo permita, no respondemos por daños indirectos, y nuestra responsabilidad total se limita a lo que nos pagaste en los últimos 12 meses.',
            zh: '我们竭力保持应用准确可用，但其按"现状"提供。在法律允许的范围内，我们不对间接损失负责，且我们的总责任以你在过去 12 个月内支付给我们的金额为限。',
          } },
        ],
      },
      {
        h: { en: 'Changes', uk: 'Зміни', es: 'Cambios', zh: '条款变更' },
        body: [
          { p: {
            en: 'If we change these terms in a way that affects you, we’ll let you know in-app before it takes effect. Continuing to use TT\u00a0Coach\u00a0AI after that means you accept the update.',
            uk: 'Якщо ми змінимо ці умови так, що це вплине на тебе, ми повідомимо в застосунку до набуття чинності. Подальше користування TT\u00a0Coach\u00a0AI означає, що ти приймаєш оновлення.',
            es: 'Si cambiamos estos términos de un modo que te afecte, te avisaremos en la app antes de que entren en vigor. Seguir usando TT\u00a0Coach\u00a0AI después significa que aceptas la actualización.',
            zh: '若我们以影响到你的方式更改本条款，会在生效前于应用内通知你。此后继续使用 TT\u00a0Coach\u00a0AI 即表示你接受更新。',
          } },
        ],
      },
      {
        h: { en: 'Contact', uk: 'Контакти', es: 'Contacto', zh: '联系我们' },
        body: [
          { p: {
            en: 'Anything unclear? Email ttcoachai@gmail.com.',
            uk: 'Щось незрозуміло? Напиши на ttcoachai@gmail.com.',
            es: '¿Algo no queda claro? Escribe a ttcoachai@gmail.com.',
            zh: '有任何不清楚之处？发邮件至 ttcoachai@gmail.com。',
          } },
        ],
      },
    ],
  },

  // =====================================================================
  support: {
    eyebrow: { en: 'Help', uk: 'Допомога', es: 'Ayuda', zh: '帮助' },
    title:   { en: 'Support', uk: 'Підтримка', es: 'Soporte', zh: '支持' },
    intro: {
      en: 'Stuck on something? Most answers are below. If not, a real person on the team replies — usually within a day.',
      uk: 'Щось не виходить? Більшість відповідей нижче. Якщо ні — відповість жива людина з команди, зазвичай протягом доби.',
      es: '¿Atascado en algo? La mayoría de respuestas están abajo. Si no, te contesta una persona del equipo — normalmente en un día.',
      zh: '遇到问题了？大多数答案都在下面。如果没有，团队里的真人会回复你——通常在一天之内。',
    },
    faqHeading: { en: 'Common questions', uk: 'Поширені питання', es: 'Preguntas frecuentes', zh: '常见问题' },
    faq: [
      {
        q: { en: 'What phone do I need?', uk: 'Який телефон потрібен?', es: '¿Qué teléfono necesito?', zh: '我需要什么手机？' },
        a: {
          en: 'Any Android phone from the last ~4 years with a rear camera. Pose analysis runs on-device, so a recent chip helps, but we tune the frame rate automatically to keep things smooth. iOS is coming soon.',
          uk: 'Будь-який Android-телефон за останні ~4 роки із задньою камерою. Аналіз пози працює на пристрої, тож свіжий чип допомагає, але ми автоматично підлаштовуємо частоту кадрів для плавності. iOS скоро.',
          es: 'Cualquier Android de los últimos ~4 años con cámara trasera. El análisis de pose corre en el dispositivo, así que un chip reciente ayuda, pero ajustamos la tasa de fotogramas automáticamente. iOS llega pronto.',
          zh: '任何近 4 年内、带后置摄像头的安卓手机均可。姿态分析在本地运行，较新的芯片更流畅，但我们会自动调节帧率以保持顺畅。iOS 版即将推出。',
        },
      },
      {
        q: { en: 'How should I position the phone?', uk: 'Як розташувати телефон?', es: '¿Cómo coloco el teléfono?', zh: '手机该怎么摆放？' },
        a: {
          en: 'Prop it on any stand at the side of the table, roughly waist-to-chest height, so your whole body is in frame. The model finds your angle in about three seconds — you don’t need to measure anything.',
          uk: 'Постав на будь-яку підставку збоку від столу, приблизно на висоті від пояса до грудей, щоб усе тіло було в кадрі. Модель знайде твій кут приблизно за три секунди — нічого міряти не треба.',
          es: 'Apóyalo en cualquier soporte al lado de la mesa, a la altura de la cintura o el pecho, con todo tu cuerpo en cuadro. El modelo encuentra tu ángulo en unos tres segundos — no tienes que medir nada.',
          zh: '用任意支架立在球台一侧，大约腰到胸的高度，让全身入镜。模型约三秒就能找到你的角度——你无需测量任何东西。',
        },
      },
      {
        q: { en: 'The coach talks too much (or too little).', uk: 'Тренер говорить забагато (або замало).', es: 'El coach habla demasiado (o muy poco).', zh: '教练话太多（或太少）。' },
        a: {
          en: 'Open a drill’s settings and move the “Tip every N seconds” slider, or switch the voice to Calm, Energetic, or Text-only. You can also have it speak only when something really drifts.',
          uk: 'Відкрий налаштування вправи й посунь повзунок «Підказка кожні N секунд» або переключи голос на Спокійний, Енергійний чи Лише текст. Можна також, щоб він говорив лише коли щось справді не так.',
          es: 'Abre los ajustes de un drill y mueve el control “Un consejo cada N segundos”, o cambia la voz a Tranquilo, Enérgico o Solo texto. También puede hablar solo cuando algo se desvía de verdad.',
          zh: '打开某个训练的设置，拖动"每 N 秒一条提示"滑块，或将语音切换为平静、激励或仅文字。也可设为只在真正偏离时才出声。',
        },
      },
      {
        q: { en: 'Where are my recordings stored?', uk: 'Де зберігаються мої записи?', es: '¿Dónde se guardan mis grabaciones?', zh: '我的录像存在哪里？' },
        a: {
          en: 'On your device, by default. Nothing is uploaded unless you tap to back up or share a clip. See the Privacy page for the full picture.',
          uk: 'На твоєму пристрої за замовчуванням. Нічого не завантажується, доки ти не натиснеш «зберегти в хмару» чи «поділитися». Деталі — на сторінці Конфіденційності.',
          es: 'En tu dispositivo, por defecto. Nada se sube salvo que toques para respaldar o compartir un clip. Mira la página de Privacidad para el detalle completo.',
          zh: '默认存在你的设备上。除非你点击备份或分享片段，否则不会上传任何内容。完整说明见隐私页面。',
        },
      },
      {
        q: { en: 'How do I cancel or get a refund?', uk: 'Як скасувати або повернути кошти?', es: '¿Cómo cancelo o pido reembolso?', zh: '如何取消或退款？' },
        a: {
          en: 'Cancel anytime from your app-store subscription settings — you keep access until the period ends. If your first paid session didn’t give you a usable fix, email support within 14 days and we’ll refund you.',
          uk: 'Скасуй будь-коли в налаштуваннях підписки магазину застосунків — доступ збережеться до кінця періоду. Якщо перша платна сесія не дала корисного виправлення, напиши в підтримку протягом 14 днів — повернемо кошти.',
          es: 'Cancela cuando quieras en los ajustes de suscripción de tu tienda — conservas el acceso hasta el fin del periodo. Si tu primera sesión de pago no te dio una corrección útil, escribe a soporte en 14 días y te reembolsamos.',
          zh: '随时在应用商店的订阅设置中取消——你保留访问权至当期结束。若首次付费课程未带来可用改进，请在 14 天内联系支持，我们将退款。',
        },
      },
      {
        q: { en: 'Can I share a drill with my club?', uk: 'Чи можна поділитися вправою з клубом?', es: '¿Puedo compartir un drill con mi club?', zh: '我能把训练分享给俱乐部吗？' },
        a: {
          en: 'Yes. Build a drill, hit share, and send the link to your group chat. Anyone with the app can load it with your stroke, reference, and strictness settings intact.',
          uk: 'Так. Створи вправу, натисни «поділитися» й надішли посилання в груповий чат. Будь-хто із застосунком завантажить її з твоїми налаштуваннями удару, еталона й суворості.',
          es: 'Sí. Crea un drill, pulsa compartir y envía el enlace a tu chat de grupo. Cualquiera con la app puede cargarlo con tu golpe, referencia y exigencia intactos.',
          zh: '可以。创建训练后点击分享，把链接发到群聊。任何装有应用的人都能加载它，并保留你的击球、参照与严格度设置。',
        },
      },
    ],
    contactHeading: { en: 'Still need a hand?', uk: 'Все ще потрібна допомога?', es: '¿Aún necesitas ayuda?', zh: '还需要帮助？' },
    contacts: [
      {
        icon: 'mail',
        label: { en: 'Email support', uk: 'Напиши на пошту', es: 'Soporte por correo', zh: '邮件支持' },
        value: 'ttcoachai@gmail.com',
        href: 'mailto:ttcoachai@gmail.com',
        note: { en: 'A human replies, usually within a day.', uk: 'Відповідає людина, зазвичай протягом доби.', es: 'Responde una persona, normalmente en un día.', zh: '真人回复，通常一天内。' },
      },
      {
        icon: 'chat',
        label: { en: 'Community', uk: 'Спільнота', es: 'Comunidad', zh: '社区' },
        value: { en: 'Players’ group chat', uk: 'Чат гравців', es: 'Chat de jugadores', zh: '球员群聊' },
        href: 'https://t.me/+HZLQEMgVw6szM2Ey',
        note: { en: 'Swap drills and tips with other players.', uk: 'Обмінюйся вправами й порадами з іншими гравцями.', es: 'Intercambia drills y consejos con otros jugadores.', zh: '与其他球员交流训练和技巧。' },
      },
    ],
  },
};

Object.assign(window, { PAGES });
