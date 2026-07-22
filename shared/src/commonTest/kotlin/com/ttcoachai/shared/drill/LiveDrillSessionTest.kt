package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.MetricStats
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Streaming-wrapper tests (A1+A2): feeds the SAME synthetic wrist-speed shape used by
 * StrokeDetector2DTest one frame at a time via [LiveDrillSession.onFrame], asserting
 * median-interval robustness, buffer-trim safety, peak-timestamp dedup, reset
 * reproducibility, and latestSkeleton().
 */
class LiveDrillSessionTest {

    // still — accelerate to peak — decelerate — still (same shape as StrokeDetector2DTest)
    private val singleStrokeXs = listOf(
        0.50f, 0.50f, 0.50f, 0.50f,
        0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f,
        0.72f, 0.72f, 0.72f, 0.72f
    )

    /**
     * Keypoints for one frame: fixed torso (len 0.25), moving right wrist at [wx].
     * Nose is offset to the RIGHT of shoulder-mid (facingSign = +1) to match the
     * wrist's rightward approach into the peak, so ForwardStrokeFilter's head-facing
     * fallback (needed when only one direction of stroke exists — speed-dominance
     * requires >=2 strokes per direction) verifies this as a forward stroke.
     */
    private fun keypointsAt(wx: Float): List<Keypoint2D> {
        val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
        kp[Coco17.NOSE] = Keypoint2D(0.55f, 0.20f, 1f)
        kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
        kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
        kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
        kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
        kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
        // Fixed knee/ankle (same every frame) so knee_bend is a real, non-degenerate
        // hip-knee-ankle angle instead of AngleCalculations2D's coincident-point null —
        // needed for the metricBands wiring test below; irrelevant to every other test
        // in this file since none of them assert on knee_bend.
        kp[Coco17.RIGHT_KNEE] = Keypoint2D(0.51f, 0.75f, 1f)
        kp[Coco17.RIGHT_ANKLE] = Keypoint2D(0.41f, 0.95f, 1f)
        return kp
    }

    /** Timestamps for [xs] at a constant [intervalMs], starting at 0. */
    private fun timestampsFor(xs: List<Float>, intervalMs: Long): List<Long> =
        xs.indices.map { it * intervalMs }

    private fun baseline(): PersonalBaseline = PersonalBaseline(
        drillType = "forehand_drive",
        metricStats = emptyMap(),
        phaseDurationsMs = emptyMap(),
        repCount = 3,
        excludedRepIndices = emptyList(),
        qualityScore = 1.0,
        createdAtMs = 0L
    )

    /** cameraYawDeg=0 override sidesteps CameraAngleEstimator so tests are deterministic. */
    private fun newSession(bufferMs: Long = 4000L): LiveDrillSession = LiveDrillSession(
        baseline = baseline(),
        aspectRatio = 1f,
        rules = emptyList(),
        handedness = Handedness.RIGHT,
        cameraYawDeg = 0f,
        bufferMs = bufferMs
    )

    private fun feedAll(
        session: LiveDrillSession,
        xs: List<Float>,
        timestamps: List<Long>
    ): List<SpokenFeedback> {
        val all = mutableListOf<SpokenFeedback>()
        xs.forEachIndexed { i, wx ->
            all += session.onFrame(keypointsAt(wx), timestamps[i])
        }
        return all
    }

    // ---- median interval ----

    /**
     * Shape from StrokeDetector2DTest.msBasedMinGapSuppressesSubGapPeaksAtAnyFps: a
     * stroke peak followed by a smaller bump 400ms later, both above the smoothed
     * speed threshold — only the 500ms refractory (minGap) tells them apart, and
     * minGap is derived from intervalMs (ms -> frame count). One dropped-frame-style
     * outlier delta (1000ms, e.g. autofocus hiccup) skews the MEAN interval to 181ms
     * while the MEDIAN stays 100ms. At the mean interval, minGap shrinks to fewer
     * frames and the bump survives as a second (spurious) stroke; at the median
     * interval the refractory correctly suppresses it -> exactly one stroke/rep.
     */
    private val minGapShapeXs = listOf(
        0.50f, 0.50f, 0.53f, 0.61f, 0.64f, 0.655f, 0.685f, 0.735f,
        0.765f, 0.765f, 0.765f, 0.765f
    )

    @Test
    fun usesMedianIntervalNotMeanOrLast() {
        val session = LiveDrillSession(
            baseline = baseline(),
            aspectRatio = 1f,
            rules = emptyList(),
            handedness = Handedness.RIGHT,
            cameraYawDeg = 0f,
            detector = StrokeDetector2D(peakWindowRadiusMs = 100)
        )
        // One 1000ms outlier delta among otherwise-constant 100ms deltas: median stays
        // 100ms (robust), mean is dragged to 181ms.
        val deltas = mutableListOf(1000L) + List(minGapShapeXs.size - 2) { 100L }
        var t = 0L
        val timestamps = mutableListOf(0L)
        for (d in deltas) {
            t += d
            timestamps.add(t)
        }
        val feedback = feedAll(session, minGapShapeXs, timestamps)
        // At the correct median interval (100ms) the 500ms refractory suppresses the
        // 400ms-later bump -> exactly one stroke -> exactly one emission. At the mean
        // (181ms) minGap shrinks and a spurious second stroke appears -> would over-emit.
        assertEquals(1, feedback.size, "median interval must yield exactly one rep, got $feedback")
    }

    // ---- buffer trim keeps un-emitted stroke frames ----

    @Test
    fun bufferTrimDoesNotDropAnUnemittedStroke() {
        // Small bufferMs forces trimming while the stroke is still recent relative
        // to the running clock; the safety-net guard must keep frames the
        // not-yet-stabilized/not-yet-emitted stroke still needs.
        val session = newSession(bufferMs = 500L)
        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)
        val feedback = feedAll(session, singleStrokeXs, timestamps)
        assertTrue(feedback.isNotEmpty(), "stroke must still emit despite small bufferMs trimming")
        assertEquals(1, feedback.size, "exactly one rep's feedback expected, got $feedback")
    }

    // ---- dedup (A2) ----

    @Test
    fun sameStabilizedStrokeEmitsExactlyOnceAcrossManyFrames() {
        // Keep feeding still frames well past the stroke so it's re-detected/
        // re-confirmed across many onFrame calls and buffer trims; peak-timestamp
        // dedup must still emit exactly once.
        val session = newSession(bufferMs = 4000L)
        val intervalMs = 100L
        val tail = List(40) { singleStrokeXs.last() } // long still tail forces repeated re-detection + trims
        val xs = singleStrokeXs + tail
        val timestamps = timestampsFor(xs, intervalMs)
        val feedback = feedAll(session, xs, timestamps)
        assertEquals(1, feedback.size, "stroke must emit exactly once, got $feedback")
    }

    // ---- reset reproducibility ----

    @Test
    fun resetReproducesTheSameFirstEmission() {
        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)

        val session = newSession()
        val first = feedAll(session, singleStrokeXs, timestamps)
        assertTrue(first.isNotEmpty())

        session.reset()
        val second = feedAll(session, singleStrokeXs, timestamps)

        assertEquals(first.size, second.size)
        assertEquals(first.map { it.message }, second.map { it.message })
        assertEquals(first.map { it.timestampMs }, second.map { it.timestampMs })
    }

    // ---- latestSkeleton ----

    @Test
    fun latestSkeletonIsNullBeforeAnyFrameThenTracksLastFrame() {
        val session = newSession()
        assertNull(session.latestSkeleton())

        val kp0 = keypointsAt(singleStrokeXs[0])
        session.onFrame(kp0, 0L)
        assertEquals(kp0, session.latestSkeleton())

        val kp1 = keypointsAt(singleStrokeXs[1])
        session.onFrame(kp1, 100L)
        assertEquals(kp1, session.latestSkeleton())
    }

    @Test
    fun latestSkeletonIsNullAfterReset() {
        val session = newSession()
        session.onFrame(keypointsAt(0.5f), 0L)
        assertNotNull(session.latestSkeleton())
        session.reset()
        assertNull(session.latestSkeleton())
    }

    // ---- metricBands (custom-drill editor knee-bend target override, live-path wiring) ----

    /** Knee/ankle fixed at the coords baked into [keypointsAt] yield a hip-knee-ankle angle
     *  of ~153° every frame (verified by [rangeRuleOverridesBaselineThatWouldOtherwisePass]
     *  in DrillFeedbackEngineTest via the same geometry-independent math — here we only need
     *  it to be a stable, non-null, non-degenerate value). */
    private fun baselineWithLooseKneeStats(): PersonalBaseline = PersonalBaseline(
        drillType = "forehand_drive",
        metricStats = mapOf(DrillMetrics.METRIC_KNEE_BEND to MetricStats(mean = 150.0, std = 20.0, min = 100.0, max = 200.0, sampleCount = 10)),
        phaseDurationsMs = emptyMap(),
        repCount = 10,
        excludedRepIndices = emptyList(),
        qualityScore = 1.0,
        createdAtMs = 0L
    )

    @Test
    fun withoutMetricBandsLooseBaselinePassesAndSpeaksPositiveMessage() {
        val session = LiveDrillSession(
            baseline = baselineWithLooseKneeStats(),
            aspectRatio = 1f,
            handedness = Handedness.RIGHT,
            cameraYawDeg = 0f
        )
        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)
        val feedback = feedAll(session, singleStrokeXs, timestamps)

        assertEquals(1, feedback.size)
        assertEquals(FeedbackMessageCatalog.positive(FeedbackLang.EN), feedback[0].message,
            "~153° is within mean150±2*20=[110,190] -> no cue -> positive reinforcement")
    }

    @Test
    fun metricBandOverridesLooseBaselineAndSpeaksKneeTooStraightCue() {
        val session = LiveDrillSession(
            baseline = baselineWithLooseKneeStats(),
            aspectRatio = 1f,
            handedness = Handedness.RIGHT,
            cameraYawDeg = 0f,
            metricBands = mapOf(DrillMetrics.METRIC_KNEE_BEND to 110.0..130.0)
        )
        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)
        val feedback = feedAll(session, singleStrokeXs, timestamps)

        assertEquals(1, feedback.size)
        val message = feedback[0].message
        assertTrue(
            message.startsWith("Legs straighter than your usual — bend the knees more"),
            "~153° is above the explicit band's max (130) -> TOO_HIGH knee cue must override the passing baseline consistency check, got: $message"
        )
    }

    // ---- onRep (rep-event side channel for UI counters) ----

    @Test
    fun onRepFiresExactlyOncePerEmittedRepWithPlausibleValues() {
        val session = newSession()
        val events = mutableListOf<RepEvent>()
        session.onRep = { events += it }

        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)
        val feedback = feedAll(session, singleStrokeXs, timestamps)

        assertEquals(1, feedback.size, "sanity: exactly one rep's feedback expected")
        assertEquals(1, events.size, "onRep must fire exactly once for the one emitted rep")
        val event = events.single()
        assertTrue(event.atMs >= 0L, "atMs must be a real (non-negative) timestamp")
        assertTrue(event.cueCount >= 0, "cueCount must be non-negative")
        assertTrue(event.placementOk, "cameraYawDeg=0 override in newSession() should always gate placementOk=true")
    }

    @Test
    fun onRepDefaultsToNullAndDoesNotAffectExistingBehavior() {
        // No onRep assigned — this is the exact fixture/assertions from
        // resetReproducesTheSameFirstEmission, run unmodified to prove the
        // ?.invoke(...) no-op path changes nothing when onRep is left null.
        val intervalMs = 100L
        val timestamps = timestampsFor(singleStrokeXs, intervalMs)

        val session = newSession()
        val first = feedAll(session, singleStrokeXs, timestamps)
        assertTrue(first.isNotEmpty())

        session.reset()
        val second = feedAll(session, singleStrokeXs, timestamps)

        assertEquals(first.size, second.size)
        assertEquals(first.map { it.message }, second.map { it.message })
        assertEquals(first.map { it.timestampMs }, second.map { it.timestampMs })
    }
}
