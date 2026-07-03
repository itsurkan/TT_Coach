/*
 * AI Coach for Table Tennis
 * BaselineDeriverTest — Tests for per-player baseline derivation from the
 * JsonStrokeDetector + StrokeAnalyzer pipeline output.
 */

package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.TestFixtures
import com.ttcoachai.shared.detection.JsonStrokeDetector
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import com.ttcoachai.shared.models.ExerciseParameters
import com.ttcoachai.shared.models.StrokeDetectorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaselineDeriverTest {

    // ── 1. Happy path: real fixture through the full pipeline ────────────────

    @Test
    fun derive_forehandFixturePipeline_producesValidBaseline() {
        val frames = TestFixtures.loadForehandDrive()
        assertTrue(frames.isNotEmpty(), "Fixture must have frames")

        val detector = JsonStrokeDetector(StrokeDetectorConfig.FOREHAND)
        val detectionResult = detector.detect(frames)
        val strokes = detectionResult.strokes
        assertTrue(strokes.isNotEmpty(), "Fixture must yield at least one detected stroke")

        val analyses = strokes.map { stroke ->
            val contactFrame = frames.getOrNull(stroke.contactFrame)
                ?: frames[stroke.forwardEndFrame.coerceIn(0, frames.lastIndex)]
            StrokeAnalyzer.analyzeStroke(
                contactFrame.landmarks,
                ExerciseParameters.forehandDriveBeginner(),
                detectionResult.getPhaseForFrame(contactFrame.frameIndex)
            )
        }

        // minRepCount=1 because the fixture's stroke count isn't guaranteed to be >= 10.
        val baseline = BaselineDeriver.derive(
            strokes = strokes,
            analyses = analyses,
            frameIntervalMs = computeFrameIntervalMs(frames),
            drillType = "forehand_shadow",
            createdAtMs = 0L,
            minRepCount = 1
        )

        assertEquals(strokes.size - baseline.excludedRepIndices.size, baseline.repCount)
        assertTrue(baseline.repCount >= 1, "Expected at least one valid rep")
        assertTrue(baseline.metricStats.isNotEmpty(), "Expected at least one technique metric")
        assertTrue(baseline.phaseDurationsMs.isNotEmpty(), "Expected phase durations")
        assertTrue(
            baseline.qualityScore in 0.0..1.0,
            "qualityScore out of range: ${baseline.qualityScore}"
        )
        for ((key, stats) in baseline.metricStats) {
            assertTrue(stats.mean.isFinite(), "$key mean must be finite, got ${stats.mean}")
            assertTrue(stats.std.isFinite() && stats.std >= 0.0, "$key std invalid: ${stats.std}")
            assertTrue(stats.sampleCount > 0, "$key sampleCount must be > 0")
        }
    }

    // ── 2. Insufficient reps throws ──────────────────────────────────────────

    @Test
    fun derive_fewerThanMinRepCount_throws() {
        val strokes = List(3) { i -> syntheticStroke(strokeIndex = i, contactFrame = 10 + i) }
        val analyses = List(3) { syntheticAnalysis(wrist = 165f, rotation = 50f) }

        assertFailsWith<IllegalArgumentException> {
            BaselineDeriver.derive(
                strokes = strokes,
                analyses = analyses,
                frameIntervalMs = 33L,
                drillType = "forehand_shadow",
                createdAtMs = 0L
            )
        }
    }

    // ── 3. Outlier rep is excluded and doesn't skew the mean ─────────────────

    @Test
    fun derive_outlierRep_isExcluded() {
        val normalCount = 14
        val strokes = mutableListOf<DetectedStroke>()
        val analyses = mutableListOf<AnalysisResult>()
        repeat(normalCount) { i ->
            strokes.add(syntheticStroke(strokeIndex = i, contactFrame = 10 + i))
            analyses.add(syntheticAnalysis(wrist = 165f, rotation = 50f))
        }
        // Insert a clear outlier in the middle: wrist angle way off the cluster.
        val outlierIndex = 7
        analyses[outlierIndex] = syntheticAnalysis(wrist = 45f, rotation = 50f)

        val baseline = BaselineDeriver.derive(
            strokes = strokes,
            analyses = analyses,
            frameIntervalMs = 33L,
            drillType = "forehand_shadow",
            createdAtMs = 0L,
            minRepCount = 10
        )

        assertTrue(
            outlierIndex in baseline.excludedRepIndices,
            "Expected rep $outlierIndex to be excluded, got ${baseline.excludedRepIndices}"
        )
        assertEquals(strokes.size - baseline.excludedRepIndices.size, baseline.repCount)
        val wristMean = baseline.metricStats[BaselineDeriver.METRIC_WRIST_ANGLE]?.mean
        assertNotNull(wristMean)
        assertTrue(
            kotlin.math.abs(wristMean - 165.0) < 0.5,
            "wrist mean should remain near 165 after exclusion, got $wristMean"
        )
    }

    // ── 4. Zero variance → std = 0 and qualityScore = 1.0 ─────────────────────

    @Test
    fun derive_identicalReps_zeroStdAndPerfectQuality() {
        val strokes = List(12) { i -> syntheticStroke(strokeIndex = i, contactFrame = 10 + i) }
        val analyses = List(12) { syntheticAnalysis(wrist = 170f, rotation = 55f) }

        val baseline = BaselineDeriver.derive(
            strokes = strokes,
            analyses = analyses,
            frameIntervalMs = 33L,
            drillType = "forehand_shadow",
            createdAtMs = 0L
        )

        assertEquals(12, baseline.repCount)
        assertTrue(baseline.excludedRepIndices.isEmpty(), "No outliers expected for identical reps")
        for ((key, stats) in baseline.metricStats) {
            assertEquals(0.0, stats.std, 1e-9, "$key std should be 0 for identical reps")
        }
        assertEquals(1.0, baseline.qualityScore, 1e-9)
    }

    // ── 5. Determinism: same input → byte-identical output ────────────────────

    @Test
    fun derive_calledTwiceWithSameInput_producesEqualBaselines() {
        val strokes = List(12) { i -> syntheticStroke(strokeIndex = i, contactFrame = 10 + i) }
        val analyses = List(12) { i ->
            syntheticAnalysis(wrist = 160f + i, rotation = 50f + (i % 3))
        }

        val b1 = BaselineDeriver.derive(
            strokes = strokes, analyses = analyses,
            frameIntervalMs = 33L, drillType = "forehand_shadow", createdAtMs = 1234L
        )
        val b2 = BaselineDeriver.derive(
            strokes = strokes, analyses = analyses,
            frameIntervalMs = 33L, drillType = "forehand_shadow", createdAtMs = 1234L
        )
        assertEquals(b1, b2)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun computeFrameIntervalMs(frames: List<com.ttcoachai.shared.models.PoseFrame>): Long {
        if (frames.size < 2) return 33L
        return (frames[1].timestampMs - frames[0].timestampMs).coerceAtLeast(1L)
    }

    private fun syntheticStroke(
        strokeIndex: Int,
        contactFrame: Int,
        durationMs: Long = 600L
    ): DetectedStroke {
        val prepStart = contactFrame - 6
        val prepEnd = contactFrame - 4
        val fwdStart = contactFrame - 3
        val fwdEnd = contactFrame + 2
        val retStart = contactFrame + 3
        val retEnd = contactFrame + 6
        return DetectedStroke(
            strokeIndex = strokeIndex,
            preparationStartFrame = prepStart,
            preparationEndFrame = prepEnd,
            forwardStartFrame = fwdStart,
            contactFrame = contactFrame,
            forwardEndFrame = fwdEnd,
            returnStartFrame = retStart,
            returnEndFrame = retEnd,
            backswingMinValue = 0.4f,
            forwardPeakValue = 0.7f,
            peakVelocity = 0.1f,
            strokeDurationMs = durationMs,
            forwardSwingDurationMs = 200L,
            isComplete = true
        )
    }

    private fun syntheticAnalysis(
        wrist: Float,
        rotation: Float,
        followThrough: Float = 120f,
        contactHeight: Float = 0.9f,
        elbowDistance: Float = 0.2f
    ): AnalysisResult {
        return AnalysisResult(
            wristAngle = wrist,
            bodyRotation = rotation,
            followThroughAngle = followThrough,
            contactHeight = contactHeight,
            elbowBodyDistance = elbowDistance
        )
    }
}
