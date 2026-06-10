package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForehandDriveEndToEndTest {

    // RAW detector count from the Task 6 diagnostic run against andrii_1_rtm.json —
    // an upper bound: ForwardStrokeFilter (recovery swings) and RepFilter (junk)
    // drop peaks before calibration/analysis. Update if detector tuning changes.
    private val RAW_DETECTOR_COUNT = 23

    private val MAX_FORWARD_REPS = 16 // 15 observed + 1 headroom; update if detector tuning or fixtures change

    // The fixture predates the camera-placement protocol, so its true yaw is unknown —
    // tests pin cameraYawDeg = 0f (treat fixture geometry as reference) instead of
    // letting the estimator gate feedback on footage we can't re-shoot.
    private fun calibrated() = DrillCalibrator.calibrate(
        sequence = TestFixturesV2.loadAndriiRtm(),
        drillType = "forehand_drive",
        createdAtMs = 1L,
        handedness = Handedness.RIGHT,
        minRepCount = 3, // fixture has fewer than the production 10 reps after filtering
        cameraYawDeg = 0f
    )

    @Test
    fun calibrationProducesUsableBaseline() {
        val baseline = calibrated()
        assertTrue(
            baseline.repCount + baseline.excludedRepIndices.size < RAW_DETECTOR_COUNT,
            "filters must drop recovery swings/junk: ${baseline.repCount}+${baseline.excludedRepIndices.size} " +
                "vs raw $RAW_DETECTOR_COUNT — if equal, ForwardStrokeFilter is dead on real footage"
        )
        assertTrue(
            baseline.repCount + baseline.excludedRepIndices.size <= MAX_FORWARD_REPS,
            "forward-rep count ${baseline.repCount}+${baseline.excludedRepIndices.size} — " +
                "if this exceeds $MAX_FORWARD_REPS, ForwardStrokeFilter stopped dropping recovery swings"
        )
        assertTrue(baseline.repCount >= 3)
        assertTrue(baseline.metricStats.isNotEmpty(), "at least some in-plane metrics must derive")
        assertTrue(baseline.qualityScore in 0.0..1.0)
        println("E2E: forward reps=${baseline.repCount}, outlier-excluded=${baseline.excludedRepIndices.size}, " +
            "metrics=${baseline.metricStats.mapValues { it.value.mean }}")
    }

    @Test
    fun ownBaselineStaysMostlyQuietOnOwnReps() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val baseline = calibrated()
        val report = ForehandDriveDrillAnalyzer(baseline = baseline, cameraYawDeg = 0f).analyze(seq)
        // Same detector, same filters, same yaw override → the analyzer must see
        // exactly the rep set calibration derived from (kept + outlier-excluded).
        assertEquals(baseline.repCount + baseline.excludedRepIndices.size, report.reps.size)
        // Only reps excluded as outliers during derivation may trigger cues — plus at
        // most ONE borderline rep: outlier exclusion uses INITIAL stats (σ inflated by
        // the outlier itself) while cue evaluation uses FINAL post-exclusion stats
        // (tighter σ), so a rep just inside the initial band can flag against the final
        // band (initial-vs-final asymmetry, mirrors the 003 BaselineDeriver path).
        // Observed on this fixture: rep 8 (elbow 99.5° vs final mean 49.4°, 2σ=37.2°).
        val flagged = report.reps.withIndex().filter { it.value.cues.isNotEmpty() }.map { it.index }
        val flaggedNonOutliers = flagged.filterNot { it in baseline.excludedRepIndices }
        assertTrue(
            flaggedNonOutliers.size <= 1,
            "at most one non-outlier rep may be flagged by its own baseline (initial-vs-final " +
                "stats asymmetry); flagged=$flagged, excluded=${baseline.excludedRepIndices}"
        )
    }

    @Test
    fun shiftedBaselineProducesCorrectDirectionalCueInBothLanguages() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]
            ?: error("fixture must yield elbow angles — check score gating thresholds")

        // Pretend the player's usual elbow is 30° straighter → every rep reads TOO_LOW.
        val shifted = real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )

        for (lang in FeedbackLang.entries) {
            val report = ForehandDriveDrillAnalyzer(baseline = shifted, lang = lang, cameraYawDeg = 0f).analyze(seq)
            // Direction is asserted on calibration-KEPT reps only: the rep calibration
            // excluded as an outlier (elbow 124° vs mean 49°) legitimately reads TOO_HIGH
            // even against the +30°-shifted baseline (2σ band tops out at ~117°).
            // Rep ordering matches calibration (asserted in ownBaselineStaysMostlyQuiet).
            val elbowCues = report.reps.withIndex()
                .filter { it.index !in real.excludedRepIndices }
                .flatMap { it.value.cues }
                .filter { it.metricKey == DrillMetrics.METRIC_ELBOW_ANGLE }
            assertTrue(elbowCues.isNotEmpty(), "shifted baseline must flag elbow on real reps")
            assertTrue(elbowCues.all { it.direction == CueDirection.TOO_LOW }, "direction must match the shift")
            val spokenElbow = report.feedback.filter { it.cue?.metricKey == DrillMetrics.METRIC_ELBOW_ANGLE }
            assertTrue(spokenElbow.isNotEmpty(), "elbow cue must reach the voice channel")
            assertTrue(spokenElbow.all { "°" in it.message }, "in-plane cue must carry degrees ($lang)")
        }
    }

    @Test
    fun spokenFeedbackRespectsCadenceOnRealTimeline() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]!!
        val shifted = real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )
        val report = ForehandDriveDrillAnalyzer(baseline = shifted, cameraYawDeg = 0f).analyze(seq)
        report.feedback.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 3000, "cadence violated: ${a.timestampMs}→${b.timestampMs}")
        }
    }

    @Test
    fun cameraYawEstimatesOnRealFootageAndGateSkipsFeedback() {
        val seq = TestFixturesV2.loadAndriiRtm()

        // Diagnostic: what does the estimator say about this (non-protocol) footage?
        val estimated = CameraAngleEstimator.estimateSideViewYawDeg(seq.frames, seq.aspectRatio)
        println("andrii_1_rtm estimated camera yaw: $estimated°")
        assertTrue(estimated != null && estimated in 0f..90f, "estimator must produce a value on real footage")

        // Forcing a beyond-gate yaw must skip all feedback regardless of baseline.
        val gated = ForehandDriveDrillAnalyzer(baseline = calibrated(), cameraYawDeg = 45f).analyze(seq)
        assertFalse(gated.placementOk)
        assertTrue(gated.feedback.isEmpty(), "feedback must be skipped beyond the placement gate")
        assertTrue(gated.reps.isNotEmpty(), "reps still reported for diagnostics")
    }
}
