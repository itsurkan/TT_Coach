package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 Part 2, task A4 (reworked by A4-fix) — TIMESTAMP/INTERVAL STABILITY GATE.
 *
 * A3 pinned parity between [LiveDrillSession] and the batch analyzer on UNIFORM
 * timestamps. This test pins the ROBUSTNESS guarantee the median-interval design
 * actually makes: realistic, symmetric, sub-frame timing jitter that keeps the
 * session's MEDIAN inter-frame delta at the true capture interval does not change
 * the coached rep/cue set. [LiveDrillSession] derives `intervalMs` as the MEDIAN of
 * consecutive frame deltas (L-26/L-02), which absorbs jitter that cancels out around
 * the median; [StrokeDetector2D] converts all its tuning windows from milliseconds
 * using that same interval (see DESIGN_LIMITATIONS L-36 for the ms->frame rounding
 * sensitivity this does NOT paper over).
 *
 * A genuine SUSTAINED fps change (uniform wall-clock rescale) moves the median
 * itself and can legitimately shift detection by design — that's the documented
 * residual in L-36, not a stability this test claims.
 *
 * See `.superpowers/sdd/task-A4-brief.md` / `task-A4fix-brief.md` for the full
 * spec and the guardrail against loosening these assertions to force a pass.
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

    /** Deterministic, MEDIAN-PRESERVING jitter: derived purely from index, no
     *  randomness. Every 10th frame arrives 2ms late, the very next frame arrives
     *  2ms early (paired pulse that cancels), all other frames are on-time. This
     *  models realistic sub-frame scheduler noise (e.g. USB/camera-driver jitter
     *  bumping one frame's delivery a couple ms) while leaving the vast majority
     *  of consecutive deltas exactly `seq.intervalMs` — so the RUNNING MEDIAN over
     *  the whole buffer stays exactly `seq.intervalMs`, verified empirically below
     *  (not merely asserted).
     *
     *  This replaces an earlier triangle-wave attempt (period 20, amplitude 5ms):
     *  because the wave's own slope (±1ms/frame) combines with the base interval,
     *  EVERY delta became interval±1 and never equaled the true interval, and the
     *  ±1 distribution was asymmetric (550 vs 555 on this fixture) — median shifted
     *  17->18ms, which is a sustained-interval change in disguise, not symmetric
     *  jitter. That is a bug in a jitter *model*, not in LiveDrillSession; a sparse
     *  cancelling pulse — most frames untouched, a few nudged and immediately
     *  corrected — is the honest "small noise around a stable median" construction. */
    private fun jitter(i: Int): Long {
        val period = 10
        return when (i % period) {
            0 -> 2L
            1 -> -2L
            else -> 0L
        }
    }

    /** Running median of consecutive deltas in [timestamps] — mirrors
     *  [LiveDrillSession.medianIntervalMs] exactly, so we can verify empirically
     *  (not just assert on faith) that a jitter model actually preserves the
     *  median the session will compute over the full buffer. */
    private fun medianDelta(timestamps: List<Long>): Long {
        val deltas = (1 until timestamps.size).map { timestamps[it] - timestamps[it - 1] }
        val sorted = deltas.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    @Test
    fun cueSequenceStableUnderMedianPreservingJitter() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val shifted = shiftedBaseline()

        fun replay(ts: (Int) -> Long): Pair<List<SpokenFeedback>, List<Long>> {
            val s = LiveDrillSession(baseline = shifted, aspectRatio = seq.aspectRatio, cameraYawDeg = 0f)
            val out = mutableListOf<SpokenFeedback>()
            val timestamps = mutableListOf<Long>()
            seq.frames.forEachIndexed { i, f ->
                val t = ts(i)
                timestamps += t
                out += s.onFrame(f.keypoints, t)
            }
            return out to timestamps
        }

        // Uniform reference — identical setup to A3's live replay.
        val (ref, refTs) = replay { i -> i * seq.intervalMs }
        assertTrue(ref.isNotEmpty(), "reference replay must be non-empty (shifted baseline should fire cues)")
        assertCadence(ref, "ref")
        assertEquals(seq.intervalMs, medianDelta(refTs), "reference median must equal seq.intervalMs by construction")

        // Realistic, median-preserving jitter: paired cancelling pulses (see [jitter]
        // doc) — the vast majority of consecutive deltas are untouched at
        // seq.intervalMs, so the RUNNING MEDIAN the session computes stays exactly
        // seq.intervalMs. Verified empirically below, not just asserted by design.
        var prev = -1L
        val (jittered, jitteredTs) = replay { i ->
            var t = i * seq.intervalMs + jitter(i)
            if (t <= prev) t = prev + 1
            prev = t
            t
        }
        val jitteredMedian = medianDelta(jitteredTs)
        println("STABILITY: jitteredMedian=$jitteredMedian (seq.intervalMs=${seq.intervalMs})")
        assertEquals(
            seq.intervalMs, jitteredMedian,
            "jitter model must be median-preserving: computed median=$jitteredMedian, expected=${seq.intervalMs}"
        )
        // Timestamps must stay strictly increasing (real capture guarantee).
        jitteredTs.zipWithNext().forEach { (a, b) ->
            assertTrue(b > a, "jittered timestamps must be strictly increasing: $a -> $b")
        }

        val refCues = ref.map { it.cue?.metricKey to it.cue?.direction }
        val jitteredCues = jittered.map { it.cue?.metricKey to it.cue?.direction }

        println("STABILITY: ref      = $refCues")
        println("STABILITY: jittered = $jitteredCues")

        assertTrue(jittered.isNotEmpty(), "jittered replay must be non-empty")
        assertCadence(jittered, "jittered")

        // The real, honest stability guarantee: realistic sub-frame jitter that
        // keeps the median interval unchanged does not change which cues fire, in
        // what order (metricKey+direction identity — message/timestamp not compared,
        // since jitter legitimately perturbs per-rep timing by design).
        assertEquals(refCues, jitteredCues, "cue sequence must be stable under median-preserving jitter")
    }

    /**
     * A genuine SUSTAINED fps change moves the median interval itself and can
     * legitimately shift detection (StrokeDetector2D.framesFor ms->frame windows
     * change meaning — DESIGN_LIMITATIONS L-36) — that is documented behavior, not
     * a stability this design promises. This scenario only pins that a uniform
     * wall-clock rescale stays internally well-formed (cadence-valid, non-empty),
     * NOT that it reproduces the reference cue set.
     */
    @Test
    fun fpsScalingStaysWellFormedButMayShiftDetection() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val shifted = shiftedBaseline()

        fun replay(ts: (Int) -> Long): List<SpokenFeedback> {
            val s = LiveDrillSession(baseline = shifted, aspectRatio = seq.aspectRatio, cameraYawDeg = 0f)
            val out = mutableListOf<SpokenFeedback>()
            seq.frames.forEachIndexed { i, f -> out += s.onFrame(f.keypoints, ts(i)) }
            return out
        }

        // 1.5x slower wall clock (denser real-time spacing of the same frames) and
        // ~0.67x faster — both change the median interval (17ms -> 25ms / 11ms),
        // which is exactly the L-36 residual, not a bug.
        val slower = replay { i -> (i * seq.intervalMs * 3) / 2 }
        val faster = replay { i -> (i * seq.intervalMs * 2) / 3 }

        println("STABILITY: slower = ${slower.map { it.cue?.metricKey to it.cue?.direction }}")
        println("STABILITY: faster = ${faster.map { it.cue?.metricKey to it.cue?.direction }}")

        assertTrue(slower.isNotEmpty(), "slower replay must be non-empty")
        assertTrue(faster.isNotEmpty(), "faster replay must be non-empty")
        assertCadence(slower, "slower")
        assertCadence(faster, "faster")
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
