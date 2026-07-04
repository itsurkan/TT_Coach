/*
 * AI Coach for Table Tennis
 * RepClassifierTest — Unit tests for the legacy-live-data rep-validity taxonomy.
 */

package com.ttcoachai.shared.feedback

import com.ttcoachai.shared.models.Landmark3D
import kotlin.test.Test
import kotlin.test.assertEquals

class RepClassifierTest {

    @Test
    fun classify_emptyInput_returnsEmptyList() {
        assertEquals(emptyList(), RepClassifier.classify(emptyList()))
    }

    @Test
    fun classify_allForwardUniformSession_allValid() {
        // 5 forward (rightward) strokes, all similar speed/duration -> all VALID.
        val reps = List(5) { forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6) }
        val verdicts = RepClassifier.classify(reps)
        assertEquals(List(5) { RepVerdict.VALID }, verdicts)
    }

    @Test
    fun classify_backswingAmongFastForwardReps_recoverySwing() {
        // 4 fast forward (rightward) reps + 1 slow backswing (leftward) rep.
        val forwardReps = List(4) { forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6) }
        val backswingRep = forwardStrokeRep(startX = 0.80f, endX = 0.55f, frameCount = 6, speedScale = 0.3f)
        val reps: List<List<List<Landmark3D>>> = forwardReps + listOf(backswingRep)
        val verdicts = RepClassifier.classify(reps)

        assertEquals(List(4) { RepVerdict.VALID }, verdicts.take(4))
        assertEquals(RepVerdict.RECOVERY_SWING, verdicts.last())
    }

    @Test
    fun classify_walkingRepHipDriftExceedsThreshold_locomotion() {
        val forwardReps = List(3) { forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6) }
        // Hip-mid drifts by 0.68 torso-lengths (torso ~0.25) while still swinging forward fast.
        val walkingRep = forwardStrokeRep(
            startX = 0.30f,
            endX = 0.80f,
            frameCount = 6,
            hipDriftTorso = 0.68f
        )
        val reps: List<List<List<Landmark3D>>> = forwardReps + listOf(walkingRep)
        val verdicts = RepClassifier.classify(reps)

        assertEquals(RepVerdict.LOCOMOTION, verdicts.last())
    }

    @Test
    fun classify_outlierRepAmongFourValidOthers_speedDurationOutlier() {
        // 4 uniform forward reps + 1 outlier at ~3x peak speed (all same direction, so all
        // survive the forward vote as a single dx-sign group; banding then isolates the outlier).
        val normalReps = List(4) { forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6) }
        val outlierRep = forwardStrokeRep(startX = 0.30f, endX = 2.30f, frameCount = 6)
        val reps: List<List<List<Landmark3D>>> = normalReps + listOf(outlierRep)
        val verdicts = RepClassifier.classify(reps)

        assertEquals(List(4) { RepVerdict.VALID }, verdicts.take(4))
        assertEquals(RepVerdict.SPEED_DURATION_OUTLIER, verdicts.last())
    }

    @Test
    fun classify_garbageRepAllLowVisibility_noPoseAndExcludedFromMedians() {
        val forwardReps = List(4) { forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6) }
        val garbageRep: List<List<Landmark3D>> = List(6) { degradedFrame() }
        val reps: List<List<List<Landmark3D>>> = forwardReps + listOf(garbageRep)
        val verdicts = RepClassifier.classify(reps)

        assertEquals(List(4) { RepVerdict.VALID }, verdicts.take(4))
        assertEquals(RepVerdict.NO_POSE, verdicts.last())
    }

    @Test
    fun classify_fewerThanFourReps_noBandingApplied() {
        // 3 forward reps, one much faster than the others -> would be an outlier under banding,
        // but MIN_REPS_TO_BAND (4) isn't met so all survive as VALID.
        val reps = listOf(
            forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6),
            forwardStrokeRep(startX = 0.30f, endX = 0.80f, frameCount = 6),
            forwardStrokeRep(startX = 0.30f, endX = 2.30f, frameCount = 6)
        )
        val verdicts = RepClassifier.classify(reps)
        assertEquals(List(3) { RepVerdict.VALID }, verdicts)
    }

    @Test
    fun classify_leftwardStrokeSessionDominant_valid() {
        // Negative-dx (leftward) strokes dominate -> forward = leftward, all VALID.
        val reps = List(5) { forwardStrokeRep(startX = 0.80f, endX = 0.30f, frameCount = 6) }
        val verdicts = RepClassifier.classify(reps)
        assertEquals(List(5) { RepVerdict.VALID }, verdicts)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a rep: [frameCount] frames where the wrist (index 16) moves linearly from
     * [startX] to [endX] (y fixed at 0.4, above the hip), facing implied by shoulders/hips
     * fixed at x=0.5, and hip-mid optionally drifting by [hipDriftTorso] torso-lengths across
     * the rep (default 0 = stationary). [speedScale] scales wrist displacement per frame,
     * simulating a slower/faster stroke while keeping the same start/end range shape
     * (used to make backswings read as slower than the forward group).
     */
    private fun forwardStrokeRep(
        startX: Float,
        endX: Float,
        frameCount: Int,
        hipDriftTorso: Float = 0f,
        speedScale: Float = 1f
    ): List<List<Landmark3D>> {
        // torso length with shoulders/hips at y=0.3/0.55 (fixed) is 0.25, matching
        // FALLBACK_TORSO_LENGTH so hipDriftTorso maps directly to hip-x delta.
        val hipDriftX = hipDriftTorso * 0.25f
        return List(frameCount) { i ->
            val t = i.toFloat() / (frameCount - 1)
            val wristX = startX + (endX - startX) * t * speedScale +
                (startX * (1f - speedScale)) // keep start anchored when speedScale < 1
            val hipMidX = 0.5f + hipDriftX * t
            frame(
                wristX = wristX,
                wristY = 0.4f,
                hipMidX = hipMidX,
                shoulderMidX = 0.5f
            )
        }
    }

    private fun frame(
        wristX: Float,
        wristY: Float,
        hipMidX: Float,
        shoulderMidX: Float
    ): List<Landmark3D> {
        val landmarks = MutableList(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 1f) }
        landmarks[0] = Landmark3D(shoulderMidX + 0.02f, 0.2f, 0f, visibility = 1f) // nose: facing right of shoulders
        landmarks[11] = Landmark3D(shoulderMidX - 0.05f, 0.3f, 0f, visibility = 1f) // left shoulder
        landmarks[12] = Landmark3D(shoulderMidX + 0.05f, 0.3f, 0f, visibility = 1f) // right shoulder
        landmarks[16] = Landmark3D(wristX, wristY, 0f, visibility = 1f) // wrist
        landmarks[23] = Landmark3D(hipMidX - 0.05f, 0.55f, 0f, visibility = 1f) // left hip
        landmarks[24] = Landmark3D(hipMidX + 0.05f, 0.55f, 0f, visibility = 1f) // right hip
        return landmarks
    }

    private fun degradedFrame(): List<Landmark3D> =
        List(33) { Landmark3D(0.5f, 0.5f, 0f, visibility = 0.1f) }
}
