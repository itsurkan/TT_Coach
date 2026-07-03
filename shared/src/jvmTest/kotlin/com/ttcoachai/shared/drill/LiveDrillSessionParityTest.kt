package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.MetricStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 Part 2, task A3 — STREAMING PARITY GATE.
 *
 * Proves that [LiveDrillSession] (frame-by-frame, A1/A2) emits the SAME coaching
 * feedback as the batch [ForehandDriveDrillAnalyzer] over the identical fixture +
 * baseline. This locks the live path to the proven batch path BEFORE any Android
 * code lands. See `.superpowers/sdd/task-A3-brief.md` for the full spec and the
 * guardrail against loosening this test to force a pass.
 */
class LiveDrillSessionParityTest {

    // The fixture predates the camera-placement protocol; pin cameraYawDeg = 0f
    // (treat fixture geometry as reference) exactly like ForehandDriveEndToEndTest.
    private fun calibrated() = DrillCalibrator.calibrate(
        sequence = TestFixturesV2.loadAndriiRtm(),
        drillType = "forehand_drive",
        createdAtMs = 1L,
        handedness = Handedness.RIGHT,
        minRepCount = 3,
        cameraYawDeg = 0f
    )

    /** Same +30° shifted-elbow baseline as
     *  ForehandDriveEndToEndTest.shiftedBaselineProducesCorrectDirectionalCueInBothLanguages
     *  so feedback is actually emitted (an unshifted own-baseline stays mostly quiet). */
    private fun shiftedBaseline(): com.ttcoachai.shared.models.PersonalBaseline {
        val real = calibrated()
        val elbow = real.metricStats[DrillMetrics.METRIC_ELBOW_ANGLE]
            ?: error("fixture must yield elbow angles — check score gating thresholds")
        return real.copy(
            metricStats = real.metricStats + (DrillMetrics.METRIC_ELBOW_ANGLE to
                MetricStats(elbow.mean + 30.0, elbow.std, elbow.min + 30.0, elbow.max + 30.0, elbow.sampleCount))
        )
    }

    /** Trailing-incomplete-stroke tolerance: live can lag batch by at most this many
     *  emitted feedback entries — the final stroke may never stabilize because no
     *  frame follows its endFrame in a live replay. */
    private val TRAILING = 1

    @Test
    fun liveReplayMatchesBatchFeedbackPrefix() {
        val seq = TestFixturesV2.loadAndriiRtm()
        val shifted = shiftedBaseline()

        // Report whether the live 4s sliding buffer actually trims this fixture.
        val totalSpanMs = seq.frames.size * seq.intervalMs
        val bufferMs = 4000L
        println(
            "PARITY: seq span=${totalSpanMs}ms (${seq.frames.size} frames @ ${seq.intervalMs}ms) " +
                "vs bufferMs=$bufferMs -> trimming ${if (totalSpanMs > bufferMs) "WILL" else "will NOT"} occur"
        )

        val batch = ForehandDriveDrillAnalyzer(baseline = shifted, cameraYawDeg = 0f).analyze(seq).feedback

        val live = LiveDrillSession(baseline = shifted, aspectRatio = seq.aspectRatio, cameraYawDeg = 0f)
        val emitted = mutableListOf<SpokenFeedback>()
        seq.frames.forEachIndexed { i, f -> emitted += live.onFrame(f.keypoints, i * seq.intervalMs) }

        println("PARITY: batch.size=${batch.size}, emitted.size=${emitted.size}")
        println("PARITY: batch = " + batch.map { Triple(it.cue?.metricKey, it.cue?.direction, it.timestampMs) })
        println("PARITY: live  = " + emitted.map { Triple(it.cue?.metricKey, it.cue?.direction, it.timestampMs) })

        // 1. Non-vacuous: the shifted baseline must actually fire cues on both paths.
        assertTrue(batch.isNotEmpty(), "batch feedback must be non-empty (shifted baseline should fire cues)")
        assertTrue(emitted.isNotEmpty(), "live feedback must be non-empty (shifted baseline should fire cues)")

        // 2. Cue/direction/message parity as a PREFIX: live may lag batch (trailing
        //    incomplete stroke) but must not diverge on anything it DOES emit.
        for (i in emitted.indices) {
            assertEquals(
                batch[i].cue?.metricKey, emitted[i].cue?.metricKey,
                "metricKey mismatch at index $i: batch=${batch[i]} live=${emitted[i]}"
            )
            assertEquals(
                batch[i].cue?.direction, emitted[i].cue?.direction,
                "direction mismatch at index $i: batch=${batch[i]} live=${emitted[i]}"
            )
            assertEquals(
                batch[i].message, emitted[i].message,
                "message mismatch at index $i: batch=${batch[i]} live=${emitted[i]}"
            )
        }

        // 3. Trailing tolerance only: live must not emit MORE than batch (no
        //    duplicates/spurious), and must not be missing more than TRAILING.
        assertTrue(
            emitted.size in (batch.size - TRAILING)..batch.size,
            "emitted.size=${emitted.size} must be in [${batch.size - TRAILING}, ${batch.size}] " +
                "(batch.size=${batch.size}, TRAILING=$TRAILING)"
        )

        // 4. Cadence holds on the live path too.
        emitted.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 3000, "cadence violated live: ${a.timestampMs}->${b.timestampMs}")
        }

        // 5. Strong: uniform synthetic timestamps should make live and batch
        //    timestamps identical for every entry live actually emits. If this ever
        //    diverges while (2) still holds, REPORT it rather than deleting this
        //    assertion (see task-A3-brief.md guardrail).
        for (i in emitted.indices) {
            assertEquals(
                batch[i].timestampMs, emitted[i].timestampMs,
                "timestampMs mismatch at index $i: batch=${batch[i].timestampMs} live=${emitted[i].timestampMs}"
            )
        }
    }
}
