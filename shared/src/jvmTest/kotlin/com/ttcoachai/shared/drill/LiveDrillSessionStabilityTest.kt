package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 Part 2, task A4 — TIMESTAMP/INTERVAL STABILITY GATE.
 *
 * A3 pinned parity between [LiveDrillSession] and the batch analyzer on UNIFORM
 * timestamps. This test pins ROBUSTNESS: the emitted cue set (metricKey+direction
 * sequence) must be stable under (1) bounded deterministic timestamp jitter and
 * (2) uniform frame-rate scaling of the wall clock — because [LiveDrillSession]
 * derives its `intervalMs` as the MEDIAN of consecutive frame deltas (L-26/L-02),
 * which absorbs small per-frame timing noise, and [StrokeDetector2D] converts all
 * its tuning windows from milliseconds using that same interval, so a uniform
 * clock rescale should not change which frames get detected as stroke boundaries.
 *
 * See `.superpowers/sdd/task-A4-brief.md` for the full spec and the guardrail
 * against loosening these assertions to force a pass.
 */
class LiveDrillSessionStabilityTest {

    // Same fixture/baseline setup as A3 (LiveDrillSessionParityTest) and the E2E
    // suite (ForehandDriveEndToEndTest) — cameraYawDeg pinned to 0f since the
    // fixture predates the camera-placement protocol.
    private fun calibrated() = DrillCalibrator.calibrate(
        sequence = TestFixturesV2.loadAndriiRtm(),
        drillType = "forehand_drive",
        createdAtMs = 1L,
        handedness = Handedness.RIGHT,
        minRepCount = 3,
        cameraYawDeg = 0f
    )

    /** Same +30° shifted-elbow baseline as A3/E2E so feedback actually fires
     *  (an unshifted own-baseline stays mostly quiet). */
    private fun shiftedBaseline(): com.ttcoachai.shared.models.PersonalBaseline {
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]
            ?: error("fixture must yield elbow angles — check score gating thresholds")
        return real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )
    }

    /** Deterministic bounded jitter: derived purely from index, no randomness.
     *  A triangle wave (period 20, amplitude 5ms: 0,1,2,3,4,5,4,3,...,-5,...,0) so
     *  jitter(i)-jitter(i-1) is at most 1ms — i.e. this models small PER-FRAME
     *  timing noise (each frame arrives up to 1ms earlier/later than its neighbor
     *  would predict), keeping consecutive deltas within ~intervalMs ± 1ms.
     *
     *  A naive `(i*k) % m - offset` sawtooth (tried first) is NOT small per-frame
     *  jitter: because `k % m` doesn't divide evenly, jitter(i)-jitter(i-1) jumps by
     *  a large fixed step every frame (here it was +4ms or -7ms, not the intended
     *  ±5ms bound), which biases every consecutive delta away from intervalMs
     *  instead of jittering it symmetrically — inflating the median by ~24% and
     *  spuriously changing detection. That is a bug in a jitter *model*, not in
     *  LiveDrillSession; a triangle wave is the correct "small independent offset
     *  per frame" construction. */
    private fun jitter(i: Int): Long {
        val period = 20
        val x = i % period
        return when {
            x <= 5 -> x.toLong()
            x <= 15 -> (10 - x).toLong()
            else -> (x - 20).toLong()
        }
    }

    @Test
    fun cueSequenceStableUnderJitterAndFpsScaling() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val shifted = shiftedBaseline()

        fun replay(ts: (Int) -> Long): List<SpokenFeedback> {
            val s = LiveDrillSession(baseline = shifted, aspectRatio = seq.aspectRatio, cameraYawDeg = 0f)
            val out = mutableListOf<SpokenFeedback>()
            seq.frames.forEachIndexed { i, f -> out += s.onFrame(f.keypoints, ts(i)) }
            return out
        }

        // Uniform reference — identical setup to A3's live replay.
        val ref = replay { i -> i * seq.intervalMs }
        assertTrue(ref.isNotEmpty(), "reference replay must be non-empty (shifted baseline should fire cues)")
        assertCadence(ref, "ref")

        // Scenario 1: bounded deterministic jitter, timestamps kept strictly increasing.
        var prev = -1L
        val jittered = replay { i ->
            var t = i * seq.intervalMs + jitter(i)
            if (t <= prev) t = prev + 1
            prev = t
            t
        }

        // Scenario 2: uniform fps scaling — 1.5x slower wall clock (denser real-time
        // spacing of the same frames) and ~0.67x faster.
        val slower = replay { i -> (i * seq.intervalMs * 3) / 2 }
        val faster = replay { i -> (i * seq.intervalMs * 2) / 3 }

        val refCues = ref.map { it.cue?.metricKey to it.cue?.direction }
        val jitteredCues = jittered.map { it.cue?.metricKey to it.cue?.direction }
        val slowerCues = slower.map { it.cue?.metricKey to it.cue?.direction }
        val fasterCues = faster.map { it.cue?.metricKey to it.cue?.direction }

        println("STABILITY: ref      = $refCues")
        println("STABILITY: jittered = $jitteredCues")
        println("STABILITY: slower   = $slowerCues")
        println("STABILITY: faster   = $fasterCues")

        assertTrue(jittered.isNotEmpty(), "jittered replay must be non-empty")
        assertTrue(slower.isNotEmpty(), "slower replay must be non-empty")
        assertTrue(faster.isNotEmpty(), "faster replay must be non-empty")

        assertCadence(jittered, "jittered")
        assertCadence(slower, "slower")
        assertCadence(faster, "faster")

        // KNOWN DIVERGENCE (see task-A4-report.md): kept strict per the guardrail,
        // not loosened. Bounded ±5ms jitter (a proper triangle-wave model — see
        // [jitter] doc) shifts medianIntervalMs by just 1ms on this fixture
        // (17ms -> 18ms), but StrokeDetector2D.framesFor() converts ms-windows to
        // frame counts via `(ms / intervalMs).toInt()`; for minPeakGapMs=500 that's
        // 500/17=29 vs 500/18=28 frames — a 1-frame (~17ms) change in the minimum
        // peak-separation gap, which is enough to un-suppress a nearby secondary
        // peak that was previously merged by NMS, splitting 1 stroke into 2 in
        // several places (23 -> 27 raw strokes on this fixture). This is a real
        // integer-rounding sensitivity in the detector's ms->frame conversion, not
        // a flaw in the median-interval design itself or in this test's jitter
        // model (isolated via a temporary StrokeDetector2D-level comparison during
        // investigation — divergence is in detection, not downstream metrics/rules).
        assertEquals(refCues, jitteredCues, "cue sequence must be stable under bounded ±5ms jitter")
        assertEquals(refCues, slowerCues, "cue sequence must be stable under 1.5x wall-clock scaling")
        assertEquals(refCues, fasterCues, "cue sequence must be stable under ~0.67x wall-clock scaling")
    }

    private fun assertCadence(run: List<SpokenFeedback>, label: String) {
        run.zipWithNext().forEach { (a, b) ->
            assertTrue(
                b.timestampMs - a.timestampMs >= 3000,
                "cadence violated in $label: ${a.timestampMs}->${b.timestampMs}"
            )
        }
    }
}
