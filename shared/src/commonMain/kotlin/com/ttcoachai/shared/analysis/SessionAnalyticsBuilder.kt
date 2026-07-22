package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.FeedbackItem
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, zero-dependency builder that turns retained per-stroke results + feedback
 * into a [SessionAnalytics]. No Android, no I/O, no external deps (kotlin.math only).
 * Proven on JVM before any Android consumer reads it.
 */
object SessionAnalyticsBuilder {

    private const val CLEAN_THRESHOLD = 80f
    private const val MAX_BUCKETS = 12

    fun build(
        sessionId: String,
        results: List<AnalysisResult>,
        feedback: List<FeedbackItem>,
        isTypeEnabled: (CorrectionType) -> Boolean = { true },
    ): SessionAnalytics {
        val sorted = results.sortedBy { it.timestamp }

        val cleanCount = sorted.count { it.overallScore >= CLEAN_THRESHOLD }
        val errorCount = sorted.size - cleanCount

        val timeline = buildTimeline(sorted)
        var peakIndex = 0
        var peak = 0f
        timeline.forEachIndexed { i, v -> if (v > peak) { peak = v; peakIndex = i } }

        val focusAreas = feedback
            .filter { it.type != CorrectionType.GENERAL && isTypeEnabled(it.type) }
            .groupingBy { it.type }
            .eachCount()
            .map { (type, count) -> FocusArea(type, count) }
            .sortedByDescending { it.count }

        val summary = buildSummary(timeline, peak, focusAreas)

        return SessionAnalytics(
            sessionId = sessionId,
            accuracyTimeline = timeline,
            peakAccuracy = peak,
            peakBucketIndex = peakIndex,
            cleanCount = cleanCount,
            errorCount = errorCount,
            focusAreas = focusAreas,
            summaryText = summary,
        )
    }

    private fun buildTimeline(sorted: List<AnalysisResult>): List<Float> {
        if (sorted.isEmpty()) return emptyList()
        val bucketCount = min(MAX_BUCKETS, sorted.size)
        val out = ArrayList<Float>(bucketCount)
        for (b in 0 until bucketCount) {
            val start = b * sorted.size / bucketCount
            val end = (b + 1) * sorted.size / bucketCount
            val slice = sorted.subList(start, end)
            val mean = if (slice.isEmpty()) 0f else slice.sumOf { it.overallScore.toDouble() }.toFloat() / slice.size
            out.add(mean)
        }
        return out
    }

    /** Deterministic English template. UK localization of this dynamic text is out of scope. */
    private fun buildSummary(
        timeline: List<Float>,
        peak: Float,
        focusAreas: List<FocusArea>,
    ): String {
        if (timeline.isEmpty()) {
            return "No strokes were analysed in this session."
        }
        val peakPct = peak.roundToInt()
        val delta = (timeline.last() - timeline.first()).roundToInt()
        val trendPhrase = when {
            delta > 0 -> "you finished $delta% stronger than you started"
            delta < 0 -> "accuracy dipped ${-delta}% by the end"
            else -> "accuracy held steady across the session"
        }
        val focusPhrase = focusAreas.firstOrNull()
            ?.let { " Your main focus area was ${displayName(it.type)}." }
            ?: ""
        return "You peaked at $peakPct% accuracy and $trendPhrase.$focusPhrase"
    }

    /** Stable string-resource key for the UI to localize the static focus-area label. */
    fun displayNameKey(type: CorrectionType): String = when (type) {
        CorrectionType.WRIST -> "focus_wrist"
        CorrectionType.BODY_ROTATION -> "focus_body_rotation"
        CorrectionType.FOLLOW_THROUGH -> "focus_follow_through"
        CorrectionType.CONTACT_HEIGHT -> "focus_contact_height"
        CorrectionType.ELBOW_POSITION -> "focus_elbow_position"
        CorrectionType.STROKE_SPEED -> "focus_stroke_speed"
        CorrectionType.KNEE_BEND -> "focus_knee_bend"
        CorrectionType.GENERAL -> "focus_general"
    }

    /** English name used only inside the generated summary text. */
    private fun displayName(type: CorrectionType): String = when (type) {
        CorrectionType.WRIST -> "wrist angle"
        CorrectionType.BODY_ROTATION -> "body rotation"
        CorrectionType.FOLLOW_THROUGH -> "follow-through"
        CorrectionType.CONTACT_HEIGHT -> "contact height"
        CorrectionType.ELBOW_POSITION -> "elbow position"
        CorrectionType.STROKE_SPEED -> "stroke speed"
        CorrectionType.KNEE_BEND -> "knee bend"
        CorrectionType.GENERAL -> "general technique"
    }
}
