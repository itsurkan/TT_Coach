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
 * Milestone exit gate for the iOS RTMPose backend: pose JSON produced by the
 * `RTMPoseExport` CLI — which runs the SAME ONNX pipeline (YOLOX-m + RTMPose-m,
 * same preprocessing) as the in-app `RTMPoseBackend` — must drive the shared
 * drill pipeline with parity to the desktop Python RTMPose golden.
 *
 * Gate (tighter than the Vision gate's ±2): forward-rep count after
 * detect → ForwardStrokeFilter → RepFilter must be within **±1** of the Python
 * RTM golden on the same video. Same models on both sides, so the only source
 * of drift is the Swift preprocessing (letterbox/affine/SimCC/normalization);
 * a larger gap means a preprocessing defect to fix in the Swift backend, never
 * a shared-threshold change.
 *
 * Also asserts `DrillCalibrator` yields a plausible baseline from the iOS-RTM
 * data, and logs an iOS-RTM-vs-Python-RTM comparison table.
 */
class IosRtmposeParityTest {

    private val REP_COUNT_TOLERANCE = 1

    private fun loadSequence(fixtureName: String): PoseSequence2D = when (fixtureName) {
        "andrii_1_ios_rtm" -> TestFixturesV2.loadAndriiIosRtm()
        "andrii_1_rtm" -> TestFixturesV2.loadAndriiRtm()
        "video_2_ios_rtm" -> TestFixturesV2.loadVideo2IosRtm()
        "video_2_rtm" -> TestFixturesV2.loadVideo2Rtm()
        else -> error("Unknown fixture: $fixtureName")
    }

    /** detect → ForwardStrokeFilter → RepFilter rep count (the gate quantity). */
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
            cameraYawDeg = 0f  // Pin yaw; fixtures don't follow the placement protocol
        )
        println("$label: reps=${result.repCount}, excluded=${result.excludedRepIndices.size}, quality=${result.qualityScore}")
        return result
    }

    private fun assertRepParity(video: String, iosFixture: String, rtmFixture: String) {
        val iosCount = pipelineRepCount(iosFixture)
        val rtmCount = pipelineRepCount(rtmFixture)
        val diff = abs(iosCount - rtmCount)
        println("$video parity: iOS-RTM=$iosCount, Python-RTM=$rtmCount, diff=$diff (tolerance=$REP_COUNT_TOLERANCE)")
        assertTrue(
            diff <= REP_COUNT_TOLERANCE,
            "$video: iOS-RTM pipeline rep count ($iosCount) must be within " +
                "±$REP_COUNT_TOLERANCE of Python-RTM ($rtmCount)"
        )
    }

    /**
     * Mean per-keypoint coordinate distance between an iOS-RTM export and the golden,
     * compared frame-index-by-frame-index. Only valid when the two share frame indexing
     * (same sampling) — true for video_2, NOT andrii_1.
     */
    private fun assertKeypointProximity(video: String, iosFixture: String, rtmFixture: String, maxMeanError: Float) {
        val ios = loadSequence(iosFixture).frames.associateBy { it.frameIndex }
        val golden = loadSequence(rtmFixture).frames.associateBy { it.frameIndex }
        var sumErr = 0f
        var n = 0
        for ((idx, gFrame) in golden) {
            val iFrame = ios[idx] ?: continue
            if (gFrame.keypoints.isEmpty() || iFrame.keypoints.isEmpty()) continue
            val k = minOf(gFrame.keypoints.size, iFrame.keypoints.size)
            for (j in 0 until k) {
                sumErr += abs(iFrame.keypoints[j].x - gFrame.keypoints[j].x)
                sumErr += abs(iFrame.keypoints[j].y - gFrame.keypoints[j].y)
                n += 2
            }
        }
        val meanErr = if (n > 0) sumErr / n else Float.MAX_VALUE
        println("$video keypoint proximity: mean|d|=$meanErr over $n coords (max=$maxMeanError)")
        assertTrue(n > 0, "$video: no comparable keypoints between iOS-RTM and golden")
        assertTrue(
            meanErr <= maxMeanError,
            "$video: iOS-RTM keypoints must match golden within mean $maxMeanError (got $meanErr)"
        )
    }

    @Test
    fun andrii1IosRtmParity() {
        assertRepParity("andrii_1", "andrii_1_ios_rtm", "andrii_1_rtm")

        val iosBaseline = calibrate("iOS-RTM", "andrii_1_ios_rtm")
        assertTrue(
            iosBaseline.qualityScore > 0.0,
            "iOS-RTM baseline quality must be >0.0, got ${iosBaseline.qualityScore}"
        )
        assertTrue(iosBaseline.repCount >= 3, "iOS-RTM must keep >=3 reps after exclusion")
    }

    @Test
    fun video2IosRtmParity() {
        assertRepParity("video_2", "video_2_ios_rtm", "video_2_rtm")

        // video_2 is frame-aligned with the golden (same 356 frames), so the strongest
        // parity signal is per-keypoint coordinate closeness — a direction-stable check
        // of the whole Swift preprocessing+inference chain, unlike the rep count which is
        // a marginal-sample tripwire here. (andrii_1 is 59fps and keeps all frames, so its
        // frame indices DON'T align with the golden's time-sampled ones — keypoint-by-index
        // comparison is only valid for video_2; andrii_1 relies on rep-count parity.)
        assertKeypointProximity("video_2", "video_2_ios_rtm", "video_2_rtm", maxMeanError = 0.01f)

        val iosBaseline = calibrate("iOS-RTM", "video_2_ios_rtm")
        assertTrue(iosBaseline.repCount >= 3, "iOS-RTM must keep >=3 reps after exclusion")
    }

    @Test
    fun iosRtmMetricsComparison() {
        println("\n=== iOS-RTM vs Python-RTM comparison ===")
        val baselines = listOf(
            "andrii_1_ios_rtm" to calibrate("andrii_1_ios_rtm", "andrii_1_ios_rtm"),
            "andrii_1_rtm" to calibrate("andrii_1_rtm", "andrii_1_rtm"),
            "video_2_ios_rtm" to calibrate("video_2_ios_rtm", "video_2_ios_rtm"),
            "video_2_rtm" to calibrate("video_2_rtm", "video_2_rtm")
        )
        println("\n| Source            | Reps | Excluded | Quality |")
        println("|-------------------|------|----------|---------|")
        baselines.forEach { (source, b) ->
            println(
                "| ${source.padEnd(17)} | ${b.repCount.toString().padEnd(4)} | " +
                    "${b.excludedRepIndices.size.toString().padEnd(8)} | %.3f |".format(b.qualityScore)
            )
        }
        assertRepParity("andrii_1", "andrii_1_ios_rtm", "andrii_1_rtm")
        assertRepParity("video_2", "video_2_ios_rtm", "video_2_rtm")
        // All baselines must calibrate (>=3 reps). Quality floor only on andrii_1
        // samples (video_2 is the marginal 3-rep case — see video2IosRtmParity).
        baselines.forEach { (source, b) ->
            assertTrue(b.repCount >= 3, "$source must keep >=3 reps after exclusion")
            assertTrue(b.qualityScore >= 0.0, "$source quality must be a valid score")
            if (source.startsWith("andrii_1")) {
                assertTrue(b.qualityScore > 0.0, "$source quality must be >0.0")
            }
        }
    }
}
