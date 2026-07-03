package com.ttcoachai.shared.detection

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeDetector2DTest {

    /**
     * Frames where only the right wrist x moves; shoulders/hips fixed with torso
     * length 0.25, so speeds are well-defined in torso-lengths/sec. Peak raw speed
     * of [singleStrokeXs] at 100 ms interval: 0.06 / 0.25 / 0.1 s = 2.4 torso/s.
     */
    private fun framesFromWristXs(xs: List<Float>, intervalMs: Long = 100L): List<PoseFrame2D> =
        xs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.49f, 0.30f, 1f)
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.51f, 0.30f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.49f, 0.55f, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.51f, 0.55f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, 0.5f, 1f)
            PoseFrame2D(frameIndex = i, timestampMs = i * intervalMs, keypoints = kp)
        }

    // still — accelerate to peak — decelerate — still
    private val singleStrokeXs = listOf(
        0.50f, 0.50f, 0.50f, 0.50f,
        0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f,
        0.72f, 0.72f, 0.72f, 0.72f
    )

    @Test
    fun detectsSingleStroke() {
        val strokes = StrokeDetector2D().detect(framesFromWristXs(singleStrokeXs), Handedness.RIGHT, 1f, 100L)
        assertEquals(1, strokes.size)
        val s = strokes[0]
        // raw speed peaks at frame 7 (0.057→0.063 = 0.06 → 2.4 torso/s); smoothing may shift ±1
        assertTrue(s.peakFrame in 6..8, "peak at ${s.peakFrame}")
        assertTrue(s.startFrame < s.peakFrame, "start ${s.startFrame} before peak")
        assertTrue(s.endFrame > s.peakFrame, "end ${s.endFrame} after peak")
        assertEquals(0, s.strokeIndex)
    }

    @Test
    fun detectsTwoStrokesWithGap() {
        val back = singleStrokeXs.reversed() // return swing of equal magnitude
        val xs = singleStrokeXs + back
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(2, strokes.size)
        assertTrue(strokes[1].peakFrame - strokes[0].peakFrame >= 5)
        assertEquals(listOf(0, 1), strokes.map { it.strokeIndex })
    }

    @Test
    fun subThresholdJitterYieldsNoStrokes() {
        // 0.004/frame jitter = 0.16 torso/s — far below the 1.0 torso/s default
        val xs = List(30) { 0.5f + (if (it % 2 == 0) 0.002f else -0.002f) }
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(0, strokes.size)
    }

    @Test
    fun msBasedTuningSurvivesFpsChange() {
        // The same motion sampled at 50 ms (linear 2× resample): displacement per
        // frame halves but torso-lengths/SEC are unchanged, and ms windows convert
        // to 2× the frame counts — still exactly one stroke. Frame-based tuning
        // would halve every window's time span and break this (L-02).
        val xs50 = singleStrokeXs.flatMapIndexed { i, x ->
            if (i == singleStrokeXs.lastIndex) listOf(x)
            else listOf(x, (x + singleStrokeXs[i + 1]) / 2f)
        }
        val strokes = StrokeDetector2D()
            .detect(framesFromWristXs(xs50, intervalMs = 50L), Handedness.RIGHT, 1f, 50L)
        assertEquals(1, strokes.size)
    }

    @Test
    fun lowScoreWristFramesContributeZeroSpeed() {
        val frames = framesFromWristXs(singleStrokeXs).map { f ->
            val kp = f.keypoints.toMutableList()
            kp[Coco17.RIGHT_WRIST] = kp[Coco17.RIGHT_WRIST].copy(score = 0.1f)
            f.copy(keypoints = kp)
        }
        assertEquals(0, StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun emptyAndTinyInputsAreSafe() {
        assertEquals(0, StrokeDetector2D().detect(emptyList(), Handedness.RIGHT, 1f, 100L).size)
        assertEquals(0, StrokeDetector2D().detect(framesFromWristXs(listOf(0.5f)), Handedness.RIGHT, 1f, 100L).size)
    }

    @Test
    fun detectionIsDeterministic() {
        val frames = framesFromWristXs(singleStrokeXs)
        val a = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        val b = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        assertEquals(a, b)
    }

    // ---- Finding 1: keep-max NMS ----

    @Test
    fun refractoryKeepsTheTallerOfTwoNearbyPeaks() {
        // Two local maxima 400 ms apart (gap=4 < minGap=5 frames): a small backswing
        // bump at idx 4 (smoothed ~1.4 torso/s) and the true stroke peak at idx 8
        // (smoothed ~1.73 torso/s). The old greedy code admits only the FIRST (bump)
        // and drops the taller stroke; keep-max NMS must REPLACE the admitted bump.
        // Stroke deceleration after idx 8 makes sm[8] > sm[9] so idx 8 is the f32 peak.
        val xs = listOf(
            0.50f, 0.50f, 0.50f,          // still prefix
            0.54f, 0.58f, 0.605f, 0.610f, // ramp bump: raw ~1.6,1.6,1.0,0.2 → sm[4]≈1.4
            0.63f, 0.70f, 0.74f,          // dip then stroke: raw 0.8,2.8,1.6 → sm[8]≈1.73
            0.75f, 0.75f, 0.75f
        )
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(1, strokes.size, "one stroke expected, got $strokes")
        assertTrue(strokes[0].peakFrame >= 8, "must keep the taller late peak, got ${strokes[0]}")
    }

    // ---- Finding 2: adjacent strokes never overlap ----

    @Test
    fun adjacentStrokesNeverOverlap() {
        // Continuous rally: forward stroke then immediate return swing with no
        // still gap — valley speed stays above 30%-of-peak floor, so boundary walks
        // overlap without the valley-clamping fix.
        // Symmetric signal: peak1 at idx 3, peak2 at idx 9 (gap=6 >= minGap=5),
        // valley at idx 6 speed ~1.07 torso/s (well above floor ~0.64). Pre-fix:
        // end1=11, start2=1 (overlap). Post-fix: both clamped to valley frame 6.
        val xs = listOf(0.50f, 0.52f, 0.56f, 0.62f, 0.68f, 0.72f, 0.72f, 0.68f, 0.62f, 0.56f, 0.52f, 0.50f)
        val strokes = StrokeDetector2D().detect(framesFromWristXs(xs), Handedness.RIGHT, 1f, 100L)
        assertEquals(2, strokes.size, "expected two strokes, got $strokes")
        assertTrue(strokes[0].endFrame <= strokes[1].startFrame,
            "strokes must not overlap: $strokes")
    }

    // ---- Finding 3: fps-invariance of stroke count via ms-based smoothing window ----

    @Test
    fun msBasedSmoothingGivesSameStrokeCountAtAnyFps() {
        // Two strokes ~1000 ms apart (peak to peak): well outside the 500 ms gap → TWO
        // strokes at every fps. A wobble is placed ~300 ms after stroke 1 peak:
        //   - ms-based smoothing (300 ms → 6 frames at 50 ms): wobble smoothed to 0.91
        //     torso/s, below the 1.0 threshold → suppressed at BOTH fps
        //   - frame-constant smoothing (3 frames at any fps): at 50 ms the wobble
        //     smooths to 1.00 torso/s, remains at threshold → admitted → extra stroke
        // The refractory is NOT the kill mechanism here: the wobble peak is >500 ms
        // from stroke 1 (600 ms at 50 ms) and would survive a frame-fixed minGap=5.
        val xs100 = listOf(
            0.50f, 0.52f, 0.56f, 0.62f,                     // 0-3: ramp, peak at idx 3
            0.62f, 0.645f, 0.675f, 0.660f, 0.640f,           // 4-8: wobble at idx 6 (~300ms after peak)
            0.650f, 0.670f, 0.720f, 0.790f, 0.830f,          // 9-13: stroke 2, peak ~idx 12
            0.830f, 0.830f
        )
        val xs50 = xs100.flatMapIndexed { i, x ->
            if (i == xs100.lastIndex) listOf(x) else listOf(x, (x + xs100[i + 1]) / 2f)
        }
        val at100 = StrokeDetector2D().detect(framesFromWristXs(xs100), Handedness.RIGHT, 1f, 100L)
        val at50 = StrokeDetector2D().detect(framesFromWristXs(xs50, intervalMs = 50L), Handedness.RIGHT, 1f, 50L)
        assertEquals(at100.size, at50.size,
            "stroke count must be fps-invariant: 100ms→${at100.size}, 50ms→${at50.size}")
        assertEquals(2, at100.size)
    }

    // ---- Finding 4: fps-invariance of stroke count via ms-based minGap conversion ----

    @Test
    fun msBasedMinGapSuppressesSubGapPeaksAtAnyFps() {
        // Two peaks 400 ms apart, BOTH above threshold post-smoothing; a tiny
        // 100 ms peak radius makes each a local maximum, so only the 500 ms
        // refractory suppresses the smaller second one → exactly 1 stroke at any
        // fps. Frame-based minGap (5 frames) = 500 ms at 100 ms but only 250 ms
        // at 50 ms sampling: the 400 ms-late bump would be admitted → 2 strokes.
        val detector = StrokeDetector2D(peakWindowRadiusMs = 100)
        // displacements (torso 0.25, 100 ms): stroke 3.2 torso/s at idx 3,
        // bump 2.0 torso/s at idx 7 — smoothed ≈1.87 and ≈1.47, both ≥ 1.0
        val xs100 = listOf(
            0.50f, 0.50f, 0.53f, 0.61f, 0.64f, 0.655f, 0.685f, 0.735f,
            0.765f, 0.765f, 0.765f, 0.765f
        )
        val xs50 = xs100.flatMapIndexed { i, x ->
            if (i == xs100.lastIndex) listOf(x) else listOf(x, (x + xs100[i + 1]) / 2f)
        }
        val at100 = detector.detect(framesFromWristXs(xs100), Handedness.RIGHT, 1f, 100L)
        val at50 = detector.detect(framesFromWristXs(xs50, intervalMs = 50L), Handedness.RIGHT, 1f, 50L)
        assertEquals(1, at100.size, "100ms: refractory must suppress the 400ms-late bump, got $at100")
        assertEquals(1, at50.size, "50ms: ms-based gap must still suppress it, got $at50")
    }

    // ---- Minor: occlusion test ----

    @Test
    fun briefMidStrokeOcclusionDoesNotSplitTheStroke() {
        // 2 low-score wrist frames inside the swing: smoothing must bridge the
        // zero-speed gap so one stroke stays one stroke.
        val frames = framesFromWristXs(singleStrokeXs).mapIndexed { i, f ->
            if (i in 6..7) {
                val kp = f.keypoints.toMutableList()
                kp[Coco17.RIGHT_WRIST] = kp[Coco17.RIGHT_WRIST].copy(score = 0.1f)
                f.copy(keypoints = kp)
            } else f
        }
        val strokes = StrokeDetector2D().detect(frames, Handedness.RIGHT, 1f, 100L)
        assertTrue(strokes.size <= 1, "occlusion must not create extra strokes: $strokes")
    }
}
