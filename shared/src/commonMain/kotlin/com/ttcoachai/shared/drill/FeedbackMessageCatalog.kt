package com.ttcoachai.shared.drill

import kotlin.math.abs
import kotlin.math.roundToInt

enum class FeedbackLang { EN, UA }

/**
 * UA + EN feedback strings. Lives in shared code (no Android resources) so the same
 * catalog serves Android, desktop fixture runs, and the future iOS app.
 *
 * Trust rule: the degree number is inserted ONLY for PRECISE_DEGREES cues;
 * qualitative cues get direction-only phrasing.
 */
object FeedbackMessageCatalog {

    fun format(cue: FeedbackCue, lang: FeedbackLang): String {
        val d = abs(cue.deltaFromMean).roundToInt()
        val precise = cue.precision == MetricPrecision.PRECISE_DEGREES
        val high = cue.direction == CueDirection.TOO_HIGH

        return when (cue.metricKey) {
            DrillMetrics.METRIC_ELBOW_ANGLE -> when {
                high && lang == FeedbackLang.EN -> withDeg("Elbow straighter than your usual — bend it a bit more", d, precise, lang)
                high -> withDeg("Лікоть пряміший, ніж зазвичай — зігни трохи більше", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Elbow more bent than your usual — open it up a bit", d, precise, lang)
                else -> withDeg("Лікоть зігнутий більше, ніж зазвичай — розігни трохи", d, precise, lang)
            }
            DrillMetrics.METRIC_SHOULDER_ANGLE -> when {
                high && lang == FeedbackLang.EN -> withDeg("Upper arm higher than your usual — drop the elbow a bit", d, precise, lang)
                high -> withDeg("Плече вище, ніж зазвичай — опусти лікоть трохи", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Upper arm lower than your usual — lift the elbow a bit", d, precise, lang)
                else -> withDeg("Плече нижче, ніж зазвичай — підніми лікоть трохи", d, precise, lang)
            }
            DrillMetrics.METRIC_KNEE_BEND -> when {
                high && lang == FeedbackLang.EN -> withDeg("Legs straighter than your usual — bend the knees more", d, precise, lang)
                high -> withDeg("Ноги пряміші, ніж зазвичай — зігни коліна більше", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Knees more bent than your usual stance — rise a little", d, precise, lang)
                else -> withDeg("Коліна зігнуті більше, ніж зазвичай — підведись трохи", d, precise, lang)
            }
            DrillMetrics.METRIC_TORSO_LEAN -> when {
                high && lang == FeedbackLang.EN -> withDeg("Leaning further than your usual — straighten up a bit", d, precise, lang)
                high -> withDeg("Нахил більший, ніж зазвичай — випрямся трохи", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("More upright than your usual — keep your normal lean", d, precise, lang)
                else -> withDeg("Корпус пряміший, ніж зазвичай — тримай свій звичний нахил", d, precise, lang)
            }
            DrillMetrics.METRIC_SHOULDER_TILT -> when {
                high && lang == FeedbackLang.EN -> withDeg("Shoulders more tilted than your usual — level them", d, precise, lang)
                high -> withDeg("Плечі нахилені більше, ніж зазвичай — вирівняй їх", d, precise, lang)
                lang == FeedbackLang.EN -> withDeg("Shoulder line flatter than your usual — let the playing shoulder drop a touch", d, precise, lang)
                else -> withDeg("Лінія плечей рівніша, ніж зазвичай — дай ігровому плечу трохи опуститись", d, precise, lang)
            }
            // Unknown metric (e.g. future rotational cues): qualitative-only, never degrees.
            else -> when (lang) {
                FeedbackLang.EN -> if (high) "A bit more than your usual on that move — ease off" else "A bit less than your usual on that move"
                FeedbackLang.UA -> if (high) "Трохи більше, ніж зазвичай у цьому русі — стримай" else "Трохи менше, ніж зазвичай у цьому русі"
            }
        }
    }

    fun positive(lang: FeedbackLang): String = when (lang) {
        FeedbackLang.EN -> "Good rep — keep that rhythm"
        FeedbackLang.UA -> "Гарний повтор — так тримати"
    }

    private fun withDeg(base: String, deg: Int, precise: Boolean, lang: FeedbackLang): String =
        if (precise) {
            when (lang) {
                FeedbackLang.EN -> "$base (about $deg° off your baseline)"
                FeedbackLang.UA -> "$base (близько $deg° від твого еталону)"
            }
        } else {
            base
        }
}
