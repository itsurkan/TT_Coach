package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage-level goldens for the detection chain (detect → ForwardStrokeFilter →
 * RepFilter) on real footage. Mirrored 1:1 by the TS harness
 * (poses_viewer/src/drill2d/__tests__/golden.test.ts) — per the binding fix-flow
 * rule these Kotlin numbers are the source of truth for the TS goldens; update
 * both suites in the same change or not at all.
 */
class ForwardStrokeFilterRealFootageTest {

    private fun counts(seq: PoseSequence2D): Triple<Int, Int, Int> {
        // Pre-protocol footage → cameraYawDeg pinned to 0 (xScale = aspectRatio).
        val raw = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val forward = ForwardStrokeFilter.filter(raw, seq.frames, Handedness.RIGHT)
        val reps = RepFilter.filter(forward)
        println("raw=${raw.size} forward=${forward.size} reps=${reps.size}")
        return Triple(raw.size, forward.size, reps.size)
    }

    @Test
    fun andrii1GoldenUnchangedByL28Fix() {
        assertEquals(Triple(23, 15, 15), counts(TestFixturesV2.loadAndriiRtm()))
    }

    @Test
    fun video4ShadowPlayDrivesAreKept() {
        // Ground truth: 12 forward drives, right hand verified visually
        // frame-by-frame (racket + watch overlay, 2026-06-11). Before the L-28
        // fix this read 18/4/4 — startFrame bled into the previous follow-through
        // and 7 true drives measured dx ≤ 0.
        assertEquals(Triple(18, 12, 9), counts(TestFixturesV2.loadVideo4Rtm()))
    }
}
