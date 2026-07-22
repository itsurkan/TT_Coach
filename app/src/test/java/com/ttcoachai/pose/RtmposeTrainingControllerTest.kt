package com.ttcoachai.pose

import com.ttcoachai.shared.drill.RepEvent
import com.ttcoachai.shared.models.CorrectionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure helpers in [RtmposeTrainingController]: the metricKey ->
 * CorrectionType UI bridge and the rep -> AnalysisResult score synthesis. Both are plain
 * functions with no Android dependency, so plain JUnit (no Robolectric) suffices — the rest
 * of the controller (CameraX, TTS, Room) is Android-heavy and left to manual/instrumented
 * verification per the task brief.
 */
class RtmposeTrainingControllerTest {

    @Test
    fun mapMetricToCorrectionType_null_isGeneral() {
        assertEquals(CorrectionType.GENERAL, RtmposeTrainingController.mapMetricToCorrectionType(null))
    }

    @Test
    fun mapMetricToCorrectionType_elbowAngle_isElbowBend() {
        assertEquals(
            CorrectionType.ELBOW_BEND,
            RtmposeTrainingController.mapMetricToCorrectionType("elbow_angle")
        )
    }

    @Test
    fun mapMetricToCorrectionType_torsoLean_isPosture() {
        assertEquals(
            CorrectionType.POSTURE,
            RtmposeTrainingController.mapMetricToCorrectionType("torso_lean")
        )
    }

    @Test
    fun mapMetricToCorrectionType_shoulderTilt_isGeneral() {
        assertEquals(
            CorrectionType.GENERAL,
            RtmposeTrainingController.mapMetricToCorrectionType("shoulder_tilt")
        )
    }

    @Test
    fun mapMetricToCorrectionType_shoulderAngle_isElbowPosition() {
        assertEquals(
            CorrectionType.ELBOW_POSITION,
            RtmposeTrainingController.mapMetricToCorrectionType("shoulder_angle")
        )
    }

    @Test
    fun mapMetricToCorrectionType_kneeBend_isKneeBend() {
        assertEquals(
            CorrectionType.KNEE_BEND,
            RtmposeTrainingController.mapMetricToCorrectionType("knee_bend")
        )
    }

    @Test
    fun mapMetricToCorrectionType_followThroughAngle2d_isFollowThrough() {
        assertEquals(
            CorrectionType.FOLLOW_THROUGH,
            RtmposeTrainingController.mapMetricToCorrectionType("follow_through_angle_2d")
        )
    }

    @Test
    fun mapMetricToCorrectionType_strokeSpeed_isStrokeSpeed() {
        assertEquals(
            CorrectionType.STROKE_SPEED,
            RtmposeTrainingController.mapMetricToCorrectionType("stroke_speed")
        )
    }

    @Test
    fun mapMetricToCorrectionType_coilRatio_isBodyRotation() {
        assertEquals(
            CorrectionType.BODY_ROTATION,
            RtmposeTrainingController.mapMetricToCorrectionType("coil_ratio")
        )
    }

    @Test
    fun mapMetricToCorrectionType_unknownKey_isGeneral() {
        assertEquals(
            CorrectionType.GENERAL,
            RtmposeTrainingController.mapMetricToCorrectionType("some_future_metric")
        )
    }

    @Test
    fun synthesizeAnalysisResult_cleanRep_scoresNinetyFive() {
        val rep = RepEvent(atMs = 1000L, cueCount = 0, placementOk = true)
        val result = RtmposeTrainingController.synthesizeAnalysisResult(rep)
        assertEquals(95f, result.overallScore, 0.001f)
        assertEquals(1000L, result.timestamp)
    }

    @Test
    fun synthesizeAnalysisResult_repWithCues_scoresSixtyFive() {
        val rep = RepEvent(atMs = 2000L, cueCount = 2, placementOk = true)
        val result = RtmposeTrainingController.synthesizeAnalysisResult(rep)
        assertEquals(65f, result.overallScore, 0.001f)
    }

    @Test
    fun synthesizeAnalysisResult_placementFailed_scoresZero() {
        val rep = RepEvent(atMs = 3000L, cueCount = 0, placementOk = false)
        val result = RtmposeTrainingController.synthesizeAnalysisResult(rep)
        assertEquals(0f, result.overallScore, 0.001f)
    }

    @Test
    fun synthesizeAnalysisResult_placementFailedWithCues_stillScoresZero() {
        val rep = RepEvent(atMs = 4000L, cueCount = 3, placementOk = false)
        val result = RtmposeTrainingController.synthesizeAnalysisResult(rep)
        assertEquals(0f, result.overallScore, 0.001f)
    }
}
