package com.ttcoachai.shared.drill

import com.ttcoachai.shared.drill.MessageTemplates.TemplateKey

/**
 * The Phase 2 forehand-drive UA+EN phrase set, expressed as a [MessageTemplates]
 * instance. Strings copied verbatim from the original [FeedbackMessageCatalog]
 * `when` dispatch — this object is now their single source of truth, with
 * [FeedbackMessageCatalog] delegating to [TEMPLATES].
 */
object CoreMessageTemplates {

    val TEMPLATES: MessageTemplates = MessageTemplates(
        templates = mapOf(
            TemplateKey(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Elbow straighter than your usual — bend it a bit more",
            TemplateKey(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Лікоть пряміший, ніж зазвичай — зігни трохи більше",
            TemplateKey(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Elbow more bent than your usual — open it up a bit",
            TemplateKey(DrillMetrics.METRIC_ELBOW_ANGLE, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Лікоть зігнутий більше, ніж зазвичай — розігни трохи",

            TemplateKey(DrillMetrics.METRIC_SHOULDER_ANGLE, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Upper arm higher than your usual — drop the elbow a bit",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_ANGLE, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Плече вище, ніж зазвичай — опусти лікоть трохи",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_ANGLE, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Upper arm lower than your usual — lift the elbow a bit",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_ANGLE, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Плече нижче, ніж зазвичай — підніми лікоть трохи",

            TemplateKey(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Legs straighter than your usual — bend the knees more",
            TemplateKey(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Ноги пряміші, ніж зазвичай — зігни коліна більше",
            TemplateKey(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Knees more bent than your usual stance — rise a little",
            TemplateKey(DrillMetrics.METRIC_KNEE_BEND, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Коліна зігнуті більше, ніж зазвичай — підведись трохи",

            TemplateKey(DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Leaning further than your usual — straighten up a bit",
            TemplateKey(DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Нахил більший, ніж зазвичай — випрямся трохи",
            TemplateKey(DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "More upright than your usual — keep your normal lean",
            TemplateKey(DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Корпус пряміший, ніж зазвичай — тримай свій звичний нахил",

            TemplateKey(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Shoulders more tilted than your usual — level them",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Плечі нахилені більше, ніж зазвичай — вирівняй їх",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Shoulder line flatter than your usual — let the playing shoulder drop a touch",
            TemplateKey(DrillMetrics.METRIC_SHOULDER_TILT, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Лінія плечей рівніша, ніж зазвичай — дай ігровому плечу трохи опуститись",

            TemplateKey(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Follow-through straighter than your usual — let the arm fold in sooner",
            TemplateKey(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Завершення пряміше, ніж зазвичай — дай руці скластися раніше",
            TemplateKey(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Shorter follow-through than your usual — swing through and finish higher",
            TemplateKey(DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Завершення коротше, ніж зазвичай — проведи крізь мʼяч і закінчи вище",

            TemplateKey(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Swinging faster than your usual — ease off and stay smooth",
            TemplateKey(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Замах швидший, ніж зазвичай — стримай і тримай плавність",
            TemplateKey(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Slower than your usual — commit and swing through a bit quicker",
            TemplateKey(DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Повільніше, ніж зазвичай — сміливіше і трохи швидше крізь мʼяч",

            TemplateKey(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_HIGH, FeedbackLang.EN) to
                "Rotating more than your usual — stay a touch more compact through the ball",
            TemplateKey(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_HIGH, FeedbackLang.UA) to
                "Скрутка більша, ніж зазвичай — тримайся трохи компактніше крізь мʼяч",
            TemplateKey(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_LOW, FeedbackLang.EN) to
                "Less rotation than your usual — open up and rotate through the ball",
            TemplateKey(DrillMetrics.METRIC_COIL_RATIO, CueDirection.TOO_LOW, FeedbackLang.UA) to
                "Скрутка менша, ніж зазвичай — розкрийся і прокрути корпус крізь мʼяч"
        ),
        positiveMessages = mapOf(
            FeedbackLang.EN to "Good rep — keep that rhythm",
            FeedbackLang.UA to "Гарний повтор — так тримати"
        ),
        // Unknown metric (e.g. future rotational cues): qualitative-only, never degrees.
        fallbackHigh = mapOf(
            FeedbackLang.EN to "A bit more than your usual on that move — ease off",
            FeedbackLang.UA to "Трохи більше, ніж зазвичай у цьому русі — стримай"
        ),
        fallbackLow = mapOf(
            FeedbackLang.EN to "A bit less than your usual on that move",
            FeedbackLang.UA to "Трохи менше, ніж зазвичай у цьому русі"
        )
    )
}
