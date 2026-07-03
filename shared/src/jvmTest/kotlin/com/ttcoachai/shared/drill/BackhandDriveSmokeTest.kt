package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.drill.movements.BackhandDrive
import com.ttcoachai.shared.models.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity smoke test (docs/superpowers/specs/2026-07-02-generic-movement-pipeline-design.md):
 * BackhandDrive.DEFINITION is currently identical data to ForehandDrive.DEFINITION
 * (same detection tuning, rep-validation flags, metrics, message templates — only
 * `id` differs), so running MovementAnalyzer/MovementCalibrator with it over the
 * same fixture ForehandDriveDrillAnalyzer uses must produce the same rep count.
 * This is the proof that a new movement is pure data: no behavior forked along the
 * way to make BackhandDrive work.
 */
class BackhandDriveSmokeTest {

    // Mirrors ForehandDriveEndToEndTest's fixture pin: andrii_1_rtm predates the
    // camera-placement protocol, so cameraYawDeg = 0f treats fixture geometry as reference.
    @Test
    fun backhandDefinitionProducesSameRepCountAsForehandOnSameFootage() {
        val seq = TestFixturesV2.loadAndriiRtm()

        val forehandBaseline = DrillCalibrator.calibrate(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 0f
        )
        val forehandReport = ForehandDriveDrillAnalyzer(baseline = forehandBaseline, cameraYawDeg = 0f).analyze(seq)

        val backhandBaseline = MovementCalibrator.calibrate(
            sequence = seq,
            definition = BackhandDrive.DEFINITION,
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 0f
        )
        val backhandAnalyzer = MovementAnalyzer(
            definition = BackhandDrive.DEFINITION,
            baseline = backhandBaseline,
            cameraYawDeg = 0f
        )
        val backhandReport = backhandAnalyzer.analyze(seq)

        assertEquals("backhand_drive", backhandBaseline.drillType)
        assertEquals(
            forehandBaseline.repCount + forehandBaseline.excludedRepIndices.size,
            backhandBaseline.repCount + backhandBaseline.excludedRepIndices.size,
            "identical definitions must detect the same forward-rep count"
        )
        assertEquals(
            forehandReport.reps.size, backhandReport.reps.size,
            "identical definitions must analyze the same rep count"
        )
        assertTrue(backhandReport.reps.isNotEmpty(), "fixture must actually produce reps")
    }
}
