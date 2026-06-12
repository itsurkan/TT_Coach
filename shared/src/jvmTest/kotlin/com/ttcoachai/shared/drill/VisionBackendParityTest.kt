package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Validates that Vision pose output drives the shared drill pipeline with parity to RTMPose.
 *
 * This is the Milestone 1 exit gate: it proves that Vision→COCO-17 mapping + PoseJsonV2Parser
 * produce usable input for the full stroke detection, filtering, and calibration pipeline.
 *
 * Test structure:
 * 1. Load both Vision and RTM fixtures for the same video
 * 2. Run the full pipeline (detect → ForwardStrokeFilter → RepFilter) on both
 * 3. Assert rep count parity (within ±2 tolerance — Vision's lower confidence naturally
 *    produces slightly fewer detections, but the core mechanics must align)
 * 4. Run DrillCalibrator on both to verify baselines are plausible
 */
class VisionBackendParityTest {

    private val REP_COUNT_TOLERANCE = 2  // Vision naturally has lower confidence; allow ±2

    private fun calibrate(label: String, fixtureName: String): PersonalBaseline {
        val sequence = when (fixtureName) {
            "andrii_1_vision" -> TestFixturesV2.loadAndriiVision()
            "andrii_1_rtm" -> TestFixturesV2.loadAndriiRtm()
            "video_2_vision" -> TestFixturesV2.loadVideo2Vision()
            "video_2_rtm" -> TestFixturesV2.loadVideo2Rtm()
            else -> error("Unknown fixture: $fixtureName")
        }

        val result = DrillCalibrator.calibrate(
            sequence = sequence,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 0f  // Pin yaw; fixture doesn't follow protocol
        )

        println("$label: reps=${result.repCount}, excluded=${result.excludedRepIndices.size}, quality=${result.qualityScore}")
        return result
    }

    @Test
    fun andrii1VisionParity() {
        val visionBaseline = calibrate("Vision", "andrii_1_vision")
        val rtmBaseline = calibrate("RTM", "andrii_1_rtm")

        val visionCount = visionBaseline.repCount
        val rtmCount = rtmBaseline.repCount

        val diff = abs(visionCount - rtmCount)
        println("andrii_1 parity: Vision=$visionCount, RTM=$rtmCount, diff=$diff (tolerance=$REP_COUNT_TOLERANCE)")

        assertTrue(
            diff <= REP_COUNT_TOLERANCE,
            "Vision rep count ($visionCount) must be within ±$REP_COUNT_TOLERANCE of RTM ($rtmCount)"
        )

        // Both must produce reasonable baselines (Vision may have lower quality due to lower confidence)
        assertTrue(
            visionBaseline.qualityScore > 0.0,
            "Vision baseline quality must be >0.0, got ${visionBaseline.qualityScore}"
        )
        assertTrue(
            rtmBaseline.qualityScore > 0.0,
            "RTM baseline quality must be >0.0, got ${rtmBaseline.qualityScore}"
        )
    }

    @Test
    fun video2VisionParity() {
        val visionBaseline = calibrate("Vision", "video_2_vision")
        val rtmBaseline = calibrate("RTM", "video_2_rtm")

        val visionCount = visionBaseline.repCount
        val rtmCount = rtmBaseline.repCount

        val diff = abs(visionCount - rtmCount)
        println("video_2 parity: Vision=$visionCount, RTM=$rtmCount, diff=$diff (tolerance=$REP_COUNT_TOLERANCE)")

        assertTrue(
            diff <= REP_COUNT_TOLERANCE,
            "Vision rep count ($visionCount) must be within ±$REP_COUNT_TOLERANCE of RTM ($rtmCount)"
        )

        // Both must produce reasonable baselines (Vision may have lower quality due to lower confidence)
        assertTrue(
            visionBaseline.qualityScore > 0.0,
            "Vision baseline quality must be >0.0, got ${visionBaseline.qualityScore}"
        )
        assertTrue(
            rtmBaseline.qualityScore > 0.0,
            "RTM baseline quality must be >0.0, got ${rtmBaseline.qualityScore}"
        )
    }

    @Test
    fun visionMetricsComparison() {
        println("\n=== Detailed Metrics Comparison ===")

        val visionAndrii = calibrate("andrii_1_vision", "andrii_1_vision")
        val rtmAndrii = calibrate("andrii_1_rtm", "andrii_1_rtm")
        val visionVideo2 = calibrate("video_2_vision", "video_2_vision")
        val rtmVideo2 = calibrate("video_2_rtm", "video_2_rtm")

        println("\n| Source            | Reps | Excluded | Quality |")
        println("|-------------------|------|----------|---------|")
        listOf(
            "andrii_1_vision" to visionAndrii,
            "andrii_1_rtm" to rtmAndrii,
            "video_2_vision" to visionVideo2,
            "video_2_rtm" to rtmVideo2
        ).forEach { (source, baseline) ->
            println("| ${source.padEnd(17)} | ${baseline.repCount.toString().padEnd(4)} | ${baseline.excludedRepIndices.size.toString().padEnd(8)} | %.3f |".format(baseline.qualityScore))
        }

        // Verify all fixtures yield metrics (exact metric names depend on detection quality)
        assertTrue(visionAndrii.metricStats.isNotEmpty(), "andrii_1_vision must derive metrics")
        assertTrue(rtmAndrii.metricStats.isNotEmpty(), "andrii_1_rtm must derive metrics")
        assertTrue(visionVideo2.metricStats.isNotEmpty(), "video_2_vision must derive metrics")
        assertTrue(rtmVideo2.metricStats.isNotEmpty(), "video_2_rtm must derive metrics")

        println("\nAll fixtures successfully derived metrics")
    }
}
