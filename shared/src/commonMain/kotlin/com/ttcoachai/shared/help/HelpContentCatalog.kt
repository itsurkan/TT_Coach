package com.ttcoachai.shared.help

import com.ttcoachai.shared.drill.FeedbackLang

/**
 * A single Help & Support FAQ item: a question and its plain-language answer.
 */
data class FaqEntry(
    val question: String,
    val answer: String
)

/**
 * FAQ content for the in-app Help & Support screen. Lives in shared/commonMain
 * (zero external deps, no java.* imports) so the same content serves Android
 * now and iOS later.
 *
 * Content describes only features that exist today: personal-baseline
 * calibration, side-camera placement for the forehand drive drill, real-time
 * feedback at 3-5s cadence compared against the player's own baseline,
 * score-gated low-confidence frames, and the existing subscription screen.
 */
object HelpContentCatalog {

    fun faq(lang: FeedbackLang): List<FaqEntry> = TABLE.getValue(lang)

    private val TABLE: Map<FeedbackLang, List<FaqEntry>> = mapOf(
        FeedbackLang.EN to listOf(
            FaqEntry(
                question = "What does TT Coach AI actually do?",
                answer = "TT Coach AI watches your table tennis strokes through the camera and gives you feedback on your technique — things like elbow angle, shoulder tilt, knee bend, and torso lean — compared against your own personal baseline, not a generic ideal."
            ),
            FaqEntry(
                question = "How does calibration work, and why does my personal baseline matter?",
                answer = "Before coaching starts, you record a short set of reps in Calibration. The app measures your own joint angles across those reps and builds a personal baseline from them. Feedback afterward compares each new stroke to that baseline — so the app learns your technique instead of pushing you toward someone else's textbook form."
            ),
            FaqEntry(
                question = "How should I place the camera?",
                answer = "For the forehand drive drill, set the camera to the side, at roughly waist height, far enough back to see your full stroke. Try to keep the camera angle steady between reps — if you turn or drift too far off that side view, the app can't reliably read the stroke and will skip feedback for that rep rather than guess."
            ),
            FaqEntry(
                question = "What does the real-time feedback mean, and why does a stroke get flagged?",
                answer = "During a drill, the app checks your stroke every few seconds (about every 3-5 seconds) and compares key angles to your personal baseline. A stroke gets flagged when one of those angles drifts noticeably from your own typical range — it's telling you this rep looked different from how you usually hit it, not that it's objectively wrong."
            ),
            FaqEntry(
                question = "Why does the app sometimes give no feedback at all?",
                answer = "Two common reasons: the camera couldn't get a confident read on your pose for that moment (low-confidence frames are skipped rather than guessed at), or the camera angle for that rep drifted too far from the side view the drill expects. In both cases the app stays quiet rather than coach off shaky data."
            ),
            FaqEntry(
                question = "How is my score or accuracy calculated?",
                answer = "Your score reflects how closely your stroke's measured angles matched your personal baseline for that drill, across the reps that had a confident camera read. It's a comparison to your own consistent technique, not a comparison to another player."
            ),
            FaqEntry(
                question = "What's included in the subscription?",
                answer = "The subscription unlocks continued access to AI coaching and the drill tools in the app. You can check or change your current plan from Profile, and see plan details on the Subscribe screen."
            ),
            FaqEntry(
                question = "How do I contact support?",
                answer = "Use the \"Contact support\" option below to open an email to our support address with your app version and device details already filled in — just add a description of what happened."
            )
        ),
        FeedbackLang.UA to listOf(
            FaqEntry(
                question = "Що саме робить TT Coach AI?",
                answer = "TT Coach AI аналізує твої удари в настільний теніс через камеру й дає зворотний зв'язок щодо техніки — кут ліктя, нахил плечей, згин коліна, нахил корпусу — порівнюючи їх із твоїм особистим еталоном, а не з якимось загальним ідеалом."
            ),
            FaqEntry(
                question = "Як працює калібрування і чому мій особистий еталон важливий?",
                answer = "Перед початком тренувань ти записуєш кілька повторів у режимі калібрування. Додаток вимірює кути твоїх суглобів у цих повторах і на їх основі будує особистий еталон. Далі кожен новий удар порівнюється саме з ним — тобто додаток підлаштовується під твою техніку, а не намагається переучити тебе на чужу."
            ),
            FaqEntry(
                question = "Як правильно розташувати камеру?",
                answer = "Для вправи \"форхенд драйв\" постав камеру збоку, приблизно на висоті пояса, достатньо далеко, щоб було видно весь удар. Намагайся тримати кут камери стабільним між повторами — якщо ти сильно розвертаєшся або відходиш від бокового ракурсу, додаток не може надійно прочитати удар і пропустить фідбек для цього повтору, а не вгадуватиме."
            ),
            FaqEntry(
                question = "Що означає фідбек у реальному часі і чому удар позначається як проблемний?",
                answer = "Під час вправи додаток перевіряє твій удар кожні кілька секунд (приблизно кожні 3-5 секунд) і порівнює ключові кути з твоїм особистим еталоном. Удар позначається, коли якийсь із кутів помітно відхиляється від твого звичного діапазону — це означає, що цей повтор виглядав інакше, ніж зазвичай, а не що він об'єктивно неправильний."
            ),
            FaqEntry(
                question = "Чому іноді додаток взагалі не дає фідбек?",
                answer = "Дві основні причини: камера не змогла впевнено розпізнати позу в цей момент (кадри з низькою впевненістю пропускаються, а не вгадуються), або кут камери для цього повтору занадто відхилився від очікуваного бокового ракурсу. В обох випадках додаток краще промовчить, ніж коучитиме на основі неточних даних."
            ),
            FaqEntry(
                question = "Як рахується мій рахунок або точність?",
                answer = "Твій рахунок відображає, наскільки виміряні кути удару відповідали твоєму особистому еталону для цієї вправи, серед повторів із впевненим розпізнаванням камери. Це порівняння з твоєю власною стабільною технікою, а не з іншим гравцем."
            ),
            FaqEntry(
                question = "Що входить у підписку?",
                answer = "Підписка відкриває постійний доступ до AI-коучингу та інструментів вправ у додатку. Перевірити або змінити поточний план можна в розділі \"Профіль\", а деталі плану — на екрані підписки."
            ),
            FaqEntry(
                question = "Як зв'язатися з підтримкою?",
                answer = "Скористайся кнопкою \"Зв'язатися з підтримкою\" нижче, щоб відкрити лист на нашу адресу підтримки з уже заповненою версією додатка та даними пристрою — просто додай опис того, що сталося."
            )
        )
    )
}
