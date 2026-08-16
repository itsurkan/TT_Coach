package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Stroke2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [DrillRepProcessor.emitRepFeedback]'s `cuesForCadence` param (bug fix: a live-session
 * cue whose CorrectionType is disabled in Settings used to still WIN the cadence race —
 * FeedbackCadencePolicy picks max-severity BEFORE the caller's disabled-type check runs
 * downstream — so a single disabled chip silenced the entire session, since runner-up
 * cues never got a turn once the window was burned on a cue that then got dropped).
 * [LiveDrillSession.cueFilter] is what actually produces `cuesForCadence` in production;
 * these tests exercise the mechanism directly with hand-built cues (style matches
 * FeedbackCadencePolicyTest) so severities/values are exact and don't depend on the
 * pose-geometry pipeline.
 */
class DrillRepProcessorTest {

    private val stroke = Stroke2D(strokeIndex = 0, startFrame = 0, peakFrame = 2, endFrame = 4, peakSpeed = 5f)

    private val topCue = FeedbackCue(
        DrillMetrics.METRIC_STROKE_SPEED, CueDirection.TOO_LOW, -5.0, 20.0, MetricPrecision.QUALITATIVE
    )
    private val runnerUpCue = FeedbackCue(
        DrillMetrics.METRIC_TORSO_LEAN, CueDirection.TOO_HIGH, 7.4, 3.0, MetricPrecision.PRECISE_DEGREES
    )

    private fun rep(cues: List<FeedbackCue>, metrics: Map<String, Double> = mapOf("dummy" to 1.0)): RepAnalysis =
        RepAnalysis(stroke = stroke, metrics = metrics, cues = cues, cameraYawDeg = 0f, placementOk = true)

    // ---- default (no cuesForCadence arg) behavior is unchanged ----

    @Test
    fun defaultCuesForCadenceFallsBackToRepCuesAndPicksHighestSeverity() {
        val cadence = FeedbackCadencePolicy()
        val spoken = DrillRepProcessor.emitRepFeedback(
            rep(listOf(runnerUpCue, topCue)), atMs = 0L, cadence = cadence, lang = FeedbackLang.EN
        )
        assertNotNull(spoken)
        assertEquals(topCue, spoken.cue, "with no filter, the highest-severity cue must still win, matching pre-fix behavior")
    }

    // ---- filtering out the top cue lets the runner-up speak ----

    @Test
    fun rejectingTopSeverityCueSpeaksNextHighestAllowedCueInstead() {
        val cadence = FeedbackCadencePolicy()
        val allRep = rep(listOf(runnerUpCue, topCue))
        val spoken = DrillRepProcessor.emitRepFeedback(
            allRep, atMs = 0L, cadence = cadence, lang = FeedbackLang.EN,
            cuesForCadence = allRep.cues.filter { it.metricKey != topCue.metricKey }
        )
        assertNotNull(spoken)
        assertEquals(runnerUpCue, spoken.cue, "the disabled top cue must not block the runner-up from being spoken")
    }

    // ---- all cues rejected: stays silent AND does not consume the cadence window ----

    @Test
    fun allCuesRejectedStaysSilentAndDoesNotConsumeTheCadenceWindow() {
        val cadence = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        val allRep = rep(listOf(topCue, runnerUpCue))

        val spoken = DrillRepProcessor.emitRepFeedback(
            allRep, atMs = 0L, cadence = cadence, lang = FeedbackLang.EN,
            cuesForCadence = emptyList() // every cue's CorrectionType disabled
        )
        assertNull(spoken, "a rep whose only cues are all muted must stay silent, not fall back to positive reinforcement")

        // The next rep's cue, well inside minIntervalMs, must still win immediately —
        // proving the muted rep above left FeedbackCadencePolicy's lastEmittedMs untouched.
        val nextSpoken = cadence.offer(nowMs = 500L, cues = listOf(runnerUpCue))
        assertEquals(runnerUpCue, nextSpoken, "the cadence window must not have been consumed by the all-rejected rep")
    }

    // ---- positive reinforcement uses the UNFILTERED rep.cues, not cuesForCadence ----

    @Test
    fun genuinelyCleanRepStillSpeaksPositiveAfterMaxInterval() {
        val cadence = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        cadence.offer(0L, listOf(topCue)) // seed lastEmittedMs so offerPositive's wait applies
        val cleanRep = rep(cues = emptyList())

        val spoken = DrillRepProcessor.emitRepFeedback(
            cleanRep, atMs = 5000L, cadence = cadence, lang = FeedbackLang.EN
        )
        assertNotNull(spoken)
        assertNull(spoken.cue, "a genuinely clean rep (no cues at all) must still get positive reinforcement")
    }

    @Test
    fun repWithMutedCuesDoesNotFalselyTriggerPositiveReinforcement() {
        val cadence = FeedbackCadencePolicy(minIntervalMs = 3000, maxIntervalMs = 5000)
        cadence.offer(0L, listOf(topCue)) // seed lastEmittedMs
        // rep.cues is NOT empty — this rep had a real correction — it was just muted.
        val mutedRep = rep(cues = listOf(topCue))

        val spoken = DrillRepProcessor.emitRepFeedback(
            mutedRep, atMs = 5000L, cadence = cadence, lang = FeedbackLang.EN,
            cuesForCadence = emptyList()
        )
        assertNull(
            spoken,
            "a rep with a real (but muted) correction must not be mistaken for a clean rep " +
                "and must not trigger positive reinforcement in its place"
        )
    }
}
