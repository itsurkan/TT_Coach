package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Coco17
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Topology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillCalibratorTest {

    /**
     * One rep = 4 still frames + 7 swing frames. wristYAtPeak controls the elbow
     * angle at the speed peak. shoulderSepX controls apparent stance vs camera:
     * 0.02 ≈ 5° yaw (passes the gate), 0.22 ≈ 78° (gated — player turned).
     */
    private fun repFrames(startIndex: Int, wristYAtPeak: Float, shoulderSepX: Float = 0.02f): List<PoseFrame2D> {
        val wristXs = listOf(0.50f, 0.50f, 0.50f, 0.50f, 0.51f, 0.53f, 0.57f, 0.63f, 0.68f, 0.71f, 0.72f)
        return wristXs.mapIndexed { i, wx ->
            val kp = MutableList(17) { Keypoint2D(0.5f, 0.5f, 1f) }
            kp[Coco17.RIGHT_SHOULDER] = Keypoint2D(0.44f + shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.LEFT_SHOULDER] = Keypoint2D(0.44f - shoulderSepX / 2, 0.30f, 1f)
            kp[Coco17.RIGHT_ELBOW] = Keypoint2D(0.50f, 0.42f, 1f)
            kp[Coco17.RIGHT_WRIST] = Keypoint2D(wx, wristYAtPeak, 1f)
            kp[Coco17.RIGHT_HIP] = Keypoint2D(0.45f, 0.55f, 1f)
            kp[Coco17.LEFT_HIP] = Keypoint2D(0.43f, 0.55f, 1f)
            kp[Coco17.RIGHT_KNEE] = Keypoint2D(0.46f, 0.72f, 1f)
            kp[Coco17.RIGHT_ANKLE] = Keypoint2D(0.48f, 0.90f, 1f)
            PoseFrame2D(startIndex + i, (startIndex + i) * 100L, kp)
        }
    }

    private fun sequenceOf(
        wristYs: List<Float>,
        shoulderSeps: List<Float> = List(wristYs.size) { 0.02f }
    ): PoseSequence2D {
        val frames = mutableListOf<PoseFrame2D>()
        wristYs.forEachIndexed { i, y -> frames += repFrames(frames.size, y, shoulderSeps[i]) }
        return PoseSequence2D(
            topology = Topology.COCO17, model = "synthetic", videoName = "synthetic.mp4",
            intervalMs = 100L, totalFrames = frames.size, videoDurationMs = frames.size * 100L,
            videoWidth = 1000, videoHeight = 1000, frames = frames
        )
    }

    @Test
    fun calibratesBaselineFromRepeatedReps() {
        // 5 near-identical reps (tiny y jitter so std > 0 and consistency rules derive)
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(
            sequence = seq, drillType = "forehand_drive", createdAtMs = 1L,
            handedness = Handedness.RIGHT, minRepCount = 3
        )
        assertEquals("forehand_drive", baseline.drillType)
        assertEquals("right", baseline.drillerHandedness)
        assertTrue(baseline.repCount >= 3)
        assertTrue(DrillMetrics.METRIC_ELBOW_ANGLE in baseline.metricStats)
        assertTrue(com.ttcoachai.shared.analysis.BaselineDeriver.PHASE_STROKE_TOTAL_MS in baseline.phaseDurationsMs)
    }

    @Test
    fun analyzerIsQuietOnRepsMatchingTheBaseline() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(seq)
        assertTrue(report.reps.isNotEmpty())
        val corrective = report.feedback.filter { it.cue != null }
        assertTrue(corrective.isEmpty(), "reps that built the baseline must not trigger cues: $corrective")
    }

    @Test
    fun analyzerFlagsDeviantRep() {
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        // Same drill, wrist much higher at peak → elbow angle far from baseline
        val deviant = sequenceOf(listOf(0.25f))
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(deviant)
        assertEquals(1, report.reps.size)
        assertTrue(report.reps[0].cues.isNotEmpty(), "deviant rep must produce cues")
        assertTrue(report.feedback.any { it.cue != null }, "cue must be spoken")
    }

    @Test
    fun feedbackRespectsCadence() {
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        // 6 deviant reps, 1.1 s apart (11 frames × 100 ms) → 3 s cadence must suppress some
        val deviant = sequenceOf(List(6) { 0.25f })
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(deviant)
        val spoken = report.feedback
        assertTrue(spoken.size < 6, "cadence must throttle: ${spoken.size}")
        spoken.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 3000, "gap ${b.timestampMs - a.timestampMs} < 3000ms")
        }
    }

    @Test
    fun calibrationThrowsOnBadCameraPlacement() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        assertFailsWith<DrillCalibrator.CameraPlacementException> {
            DrillCalibrator.calibrate(
                seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3,
                cameraYawDeg = 45f // explicit override beyond the 30° gate
            )
        }
    }

    @Test
    fun analyzerSkipsFeedbackOnBadCameraPlacement() {
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(
            seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3, cameraYawDeg = 0f
        )
        val report = ForehandDriveDrillAnalyzer(baseline = baseline, cameraYawDeg = 45f).analyze(seq)
        assertFalse(report.placementOk)
        assertTrue(report.reps.isNotEmpty(), "reps still reported for diagnostics")
        report.reps.forEach { rep ->
            assertEquals(45f, rep.cameraYawDeg)
            assertFalse(rep.placementOk)
            assertTrue(rep.cues.isEmpty(), "no cues on a bad-placement rep")
        }
        assertTrue(report.feedback.isEmpty(), "feedback must be skipped on bad placement")
    }

    @Test
    fun yawIsResolvedPerRepNotPerSession() {
        // Without an override, every rep must carry its own estimated yaw — the player
        // may move their feet between reps. Synthetic reps share identical geometry,
        // so the estimates agree, but each rep is annotated independently.
        val seq = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(seq, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(seq)
        assertTrue(report.reps.isNotEmpty())
        report.reps.forEach { rep ->
            assertTrue(rep.placementOk, "synthetic ~5° yaw must pass the 30° gate")
            assertTrue(rep.cameraYawDeg in 0f..30f, "per-rep yaw out of range: ${rep.cameraYawDeg}")
        }
    }

    @Test
    fun playerTurningMidSessionGatesOnlyTheTurnedReps() {
        // Calibrate in profile, then mid-drill the player turns toward the camera
        // (feet moved → wide apparent shoulder separation) for the last two reps.
        val calib = sequenceOf(listOf(0.40f, 0.401f, 0.399f, 0.4005f, 0.3995f))
        val baseline = DrillCalibrator.calibrate(calib, "forehand_drive", 1L, Handedness.RIGHT, minRepCount = 3)

        val drill = sequenceOf(
            wristYs = listOf(0.40f, 0.401f, 0.399f, 0.40f, 0.401f),
            shoulderSeps = listOf(0.02f, 0.02f, 0.02f, 0.22f, 0.22f)
        )
        val report = ForehandDriveDrillAnalyzer(baseline = baseline).analyze(drill)
        assertEquals(5, report.reps.size)
        assertTrue(report.reps[0].placementOk && report.reps[1].placementOk && report.reps[2].placementOk,
            "profile reps must keep getting feedback")
        // reps[3] is the transition rep: its pre-stroke lookback window mixes both
        // stances, so its verdict depends on the median split — deliberately unasserted.
        assertFalse(report.reps[4].placementOk, "turned rep must be gated (yaw ~78°)")
        assertTrue(report.reps[4].cues.isEmpty(), "gated rep must produce no cues")
    }
}
