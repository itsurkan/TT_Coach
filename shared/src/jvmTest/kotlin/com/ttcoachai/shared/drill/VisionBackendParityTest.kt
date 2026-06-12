package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseSequence2D
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Milestone 1 exit gate: Apple Vision pose output (real `VisionPoseExport` CLI exports,
 * NOT synthetic fixtures) drives the shared drill pipeline with parity to RTMPose.
 *
 * Gate definition (per the iOS port plan, Task 1.3): forward-rep count after
 * detect → ForwardStrokeFilter → RepFilter must be within ±2 of the RTM golden
 * on the same video. The plan's gate is deliberately measured BEFORE the
 * calibrator's 2σ outlier exclusion — that stage reacts to per-rep metric noise,
 * which differs between models without indicating a mapping defect.
 *
 * Additionally, DrillCalibrator must produce a plausible baseline from the
 * Vision data (it may exclude different outliers than RTM does).
 */
class VisionBackendParityTest {

    private val REP_COUNT_TOLERANCE = 2

    private fun loadSequence(fixtureName: String): PoseSequence2D = when (fixtureName) {
        "andrii_1_vision" -> TestFixturesV2.loadAndriiVision()
        "andrii_1_rtm" -> TestFixturesV2.loadAndriiRtm()
        "video_2_vision" -> TestFixturesV2.loadVideo2Vision()
        "video_2_rtm" -> TestFixturesV2.loadVideo2Rtm()
        else -> error("Unknown fixture: $fixtureName")
    }

    /** detect → ForwardStrokeFilter → RepFilter rep count (the plan's gate quantity). */
    private fun pipelineRepCount(fixtureName: String): Int {
        val seq = loadSequence(fixtureName)
        val detected = StrokeDetector2D().detect(seq.frames, Handedness.RIGHT, seq.aspectRatio, seq.intervalMs)
        val forward = ForwardStrokeFilter.filter(detected, seq.frames, Handedness.RIGHT)
        return RepFilter.filter(forward).size
    }

    private fun calibrate(label: String, fixtureName: String): PersonalBaseline {
        val result = DrillCalibrator.calibrate(
            sequence = loadSequence(fixtureName),
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = Handedness.RIGHT,
            minRepCount = 3,
            cameraYawDeg = 0f  // Pin yaw; fixture doesn't follow the placement protocol
        )
        println("$label: reps=${result.repCount}, excluded=${result.excludedRepIndices.size}, quality=${result.qualityScore}")
        return result
    }

    private fun assertRepParity(video: String, visionFixture: String, rtmFixture: String) {
        val visionCount = pipelineRepCount(visionFixture)
        val rtmCount = pipelineRepCount(rtmFixture)
        val diff = abs(visionCount - rtmCount)
        println("$video parity: Vision=$visionCount, RTM=$rtmCount, diff=$diff (tolerance=$REP_COUNT_TOLERANCE)")
        assertTrue(
            diff <= REP_COUNT_TOLERANCE,
            "$video: Vision pipeline rep count ($visionCount) must be within " +
                "±$REP_COUNT_TOLERANCE of RTM ($rtmCount)"
        )
    }

    @Test
    fun andrii1VisionParity() {
        assertRepParity("andrii_1", "andrii_1_vision", "andrii_1_rtm")

        val visionBaseline = calibrate("Vision", "andrii_1_vision")
        val rtmBaseline = calibrate("RTM", "andrii_1_rtm")
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
        assertRepParity("video_2", "video_2_vision", "video_2_rtm")

        val visionBaseline = calibrate("Vision", "video_2_vision")
        val rtmBaseline = calibrate("RTM", "video_2_rtm")
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

        val baselines = listOf(
            "andrii_1_vision" to calibrate("andrii_1_vision", "andrii_1_vision"),
            "andrii_1_rtm" to calibrate("andrii_1_rtm", "andrii_1_rtm"),
            "video_2_vision" to calibrate("video_2_vision", "video_2_vision"),
            "video_2_rtm" to calibrate("video_2_rtm", "video_2_rtm")
        )

        println("\n| Source            | Reps | Excluded | Quality |")
        println("|-------------------|------|----------|---------|")
        baselines.forEach { (source, baseline) ->
            println(
                "| ${source.padEnd(17)} | ${baseline.repCount.toString().padEnd(4)} | " +
                    "${baseline.excludedRepIndices.size.toString().padEnd(8)} | " +
                    "%.3f |".format(baseline.qualityScore)
            )
        }

        // Same gate as the per-video tests: pipeline rep parity within ±2.
        assertRepParity("andrii_1", "andrii_1_vision", "andrii_1_rtm")
        assertRepParity("video_2", "video_2_vision", "video_2_rtm")

        // Every baseline must be plausible.
        baselines.forEach { (source, baseline) ->
            assertTrue(baseline.qualityScore > 0.0, "$source quality must be >0.0")
            assertTrue(baseline.repCount >= 3, "$source must keep >=3 reps after exclusion")
        }
    }
}
