package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.drill.FeedbackLang
import com.ttcoachai.shared.models.CorrectionType

/**
 * "Why was this flagged?" content for the tap-to-explain bottom sheet on the
 * training screen. Distinct from [com.ttcoachai.shared.drill.FeedbackMessageCatalog]
 * (the short live coaching cue) — this is the longer, on-demand explanation a
 * player sees after tapping a feedback row: what the metric measures, why it
 * matters for the stroke, and how to fix it.
 *
 * Terminology matches the existing Android correction-type strings
 * (`correction_wrist_angle`, `correction_body_rotation`, `correction_follow_through`,
 * `correction_contact_height`, `correction_elbow_position`, `correction_stroke_speed`
 * in app/src/main/res/values{,-uk}/strings.xml) so the sheet doesn't introduce
 * conflicting vocabulary.
 *
 * Lives in shared/commonMain (zero external deps, no java.* imports) so the same
 * content serves Android now and iOS later.
 */
data class FeedbackExplanation(
    val title: String,
    val whatItChecks: String,
    val whyItMatters: String,
    val howToFix: String
)

object FeedbackExplanationCatalog {

    fun explain(type: CorrectionType, lang: FeedbackLang): FeedbackExplanation =
        TABLE.getValue(type).getValue(lang)

    private val TABLE: Map<CorrectionType, Map<FeedbackLang, FeedbackExplanation>> = mapOf(
        CorrectionType.WRIST to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Wrist Angle",
                whatItChecks = "How bent or straight your wrist is at the moment of contact with the ball.",
                whyItMatters = "A wrist that collapses or over-flexes at contact costs you racket control and consistency — the ball leaves at an unpredictable angle instead of the one you intended.",
                howToFix = "Keep the wrist firm and roughly in line with your forearm through contact, like it's an extension of it — let the arm and body generate the swing, not a flick of the wrist."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Кут зап'ястя",
                whatItChecks = "Наскільки зігнуте або пряме твоє зап'ястя в момент контакту з м'ячем.",
                whyItMatters = "Якщо зап'ястя провалюється або згинається занадто сильно в момент удару, ти втрачаєш контроль над ракеткою — м'яч летить у непередбачуваному напрямку.",
                howToFix = "Тримай зап'ястя жорстким і приблизно на одній лінії з передпліччям протягом усього удару — уяви, що це його продовження. Швидкість дає рух руки й корпусу, а не змах зап'ястям."
            )
        ),
        CorrectionType.BODY_ROTATION to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Body Rotation",
                whatItChecks = "How much your hips and shoulders turn together during the backswing and forward swing, compared with your own usual rotation.",
                whyItMatters = "Rotation is where a forehand's power actually comes from — an arm-only swing with little turn feels weak and inconsistent, and it makes timing much harder to repeat.",
                howToFix = "Turn your shoulders and hips together on the backswing like winding a spring, then unwind them into the shot — let the turn drive the racket instead of just swinging with the arm."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Ротація корпусу",
                whatItChecks = "Наскільки стегна та плечі повертаються разом під час замаху й проведення удару, порівняно з твоєю звичною ротацією.",
                whyItMatters = "Саме ротація дає силу форхенду — удар лише рукою без повороту корпусу виходить слабким і нестабільним, і його набагато важче повторювати однаково.",
                howToFix = "На замаху повертай плечі й стегна разом, ніби закручуєш пружину, а потім розкручуй їх в удар — нехай ракетку веде поворот корпусу, а не тільки рука."
            )
        ),
        CorrectionType.FOLLOW_THROUGH to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Follow-Through",
                whatItChecks = "Whether you continue the racket's path up and forward after contact, instead of stopping the swing right at the ball.",
                whyItMatters = "Cutting the swing short at contact reduces the spin and control you put on the ball and often signals you decelerated into the shot rather than through it.",
                howToFix = "Keep brushing the ball up and forward after contact and let the racket finish naturally near your opposite shoulder, instead of stopping the arm the instant it touches the ball."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Проведення (follow-through)",
                whatItChecks = "Чи продовжуєш ти рух ракетки вгору-вперед після контакту, замість того щоб зупиняти удар одразу біля м'яча.",
                whyItMatters = "Обрив руху одразу на контакті зменшує обертання й контроль над м'ячем і часто означає, що ти сповільнився в ударі, а не пройшов крізь нього.",
                howToFix = "Продовжуй \"проводити\" м'яч вгору-вперед після контакту й дай ракетці природно завершити рух біля протилежного плеча, замість того щоб різко зупиняти руку на контакті."
            )
        ),
        CorrectionType.CONTACT_HEIGHT to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Contact Height",
                whatItChecks = "How high or low the ball is relative to your body when your racket meets it, compared with your own baseline contact point.",
                whyItMatters = "Contact that's too high or too low forces awkward racket angles and timing — it's much harder to control depth and direction from an unusual contact height.",
                howToFix = "Aim to meet the ball at roughly waist height, slightly in front of your body, adjusting your footwork and timing rather than reaching up or down for it."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Висота контакту",
                whatItChecks = "Наскільки високо чи низько м'яч перебуває відносно тіла в момент удару ракеткою, порівняно з твоєю звичною точкою контакту.",
                whyItMatters = "Занадто високий або занадто низький контакт змушує тримати ракетку під незручним кутом і збиває тайминг — керувати глибиною й напрямком удару з нетипової висоти набагато важче.",
                howToFix = "Намагайся зустрічати м'яч приблизно на висоті пояса, трохи попереду тіла — підлаштовуй роботу ніг і тайминг, а не тягнись за м'ячем вгору чи вниз."
            )
        ),
        CorrectionType.ELBOW_POSITION to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Elbow Position",
                whatItChecks = "How far your elbow sits from your body during the stroke, compared with your own usual arm shape.",
                whyItMatters = "An elbow that drifts too far from the body loses connection to your core rotation, while one pressed too tightly against it restricts the swing — both make the stroke harder to repeat with the same power and shape.",
                howToFix = "Let the elbow stay a comfortable, consistent distance from your body — close enough to feel connected to the torso's rotation, loose enough that the arm can swing freely."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Позиція ліктя",
                whatItChecks = "Наскільки далеко лікоть перебуває від тіла під час удару, порівняно з твоєю звичною формою руки.",
                whyItMatters = "Лікоть, що надто відходить від тіла, втрачає зв'язок з обертанням корпусу, а притиснутий занадто щільно — обмежує розмах. Обидва варіанти ускладнюють повторення удару з однаковою силою й формою.",
                howToFix = "Тримай лікоть на зручній, стабільній відстані від тіла — достатньо близько, щоб відчувати зв'язок з обертанням корпусу, і достатньо вільно, щоб рука могла рухатись без затиснень."
            )
        ),
        CorrectionType.STROKE_SPEED to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Stroke Speed",
                whatItChecks = "How fast your racket moves through the stroke, compared with your own typical swing speed for this drill.",
                whyItMatters = "A swing that's noticeably faster or slower than your usual pace tends to break down timing and contact quality — speed and control need to scale together for the stroke to stay repeatable.",
                howToFix = "Match the pace you use on your best reps: don't rush the swing for extra power, and don't decelerate defensively — a smooth, committed swing at your own tempo is easier to control."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Швидкість удару",
                whatItChecks = "Наскільки швидко рухається ракетка протягом удару, порівняно з твоєю звичною швидкістю в цій вправі.",
                whyItMatters = "Удар, помітно швидший або повільніший за твій звичний темп, зазвичай збиває тайминг і якість контакту — швидкість і контроль мають зростати разом, щоб удар лишався стабільним.",
                howToFix = "Тримай темп, як у твоїх найкращих повторах: не прискорюй удар заради додаткової сили і не гальмуй його з обережності — плавний, впевнений рух у власному темпі легше контролювати."
            )
        ),
        CorrectionType.GENERAL to mapOf(
            FeedbackLang.EN to FeedbackExplanation(
                title = "Technique",
                whatItChecks = "Your overall stroke mechanics for this rep, compared against your personal baseline across the tracked joints and timing.",
                whyItMatters = "Small technical drifts compound — a stroke that's a little off in several places at once is much less repeatable than one small, isolated deviation.",
                howToFix = "Focus on one cue at a time rather than trying to fix everything at once, and compare each rep against how your best reps feel, not an abstract ideal."
            ),
            FeedbackLang.UA to FeedbackExplanation(
                title = "Техніка",
                whatItChecks = "Загальну механіку твого удару в цьому повторі, порівняно з твоїм особистим еталоном за відстежуваними суглобами й таймінгом.",
                whyItMatters = "Невеликі технічні відхилення накопичуються — удар, який трохи \"пливе\" одразу в кількох місцях, набагато менш стабільний, ніж одне ізольоване відхилення.",
                howToFix = "Зосереджуйся на одній підказці за раз замість того, щоб виправляти все одразу, і порівнюй кожен повтор із відчуттям своїх найкращих спроб, а не з абстрактним ідеалом."
            )
        )
    )
}
