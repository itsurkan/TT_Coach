package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * MEASUREMENT-ONLY harness (not a regression gate, no "fix" applied). Answers: how much does
 * `stroke_speed` change as a function of pose-sampling frame rate, on the SAME full-fps footage?
 *
 * Loads the exact same fixture as [ShippedBaselineDerivationHarness]
 * (`Videos/andrii_1/andrii_1_poses_mediapipe_lite.json`, intervalMs=17, 1106 frames), then
 * decimates the frame list by keeping every Nth frame (N in {1,2,3,4,6,8,12}) and re-runs the
 * SAME detect -> ForwardStrokeFilter -> RepFilter -> LocomotionFilter -> DrillMetrics/
 * DerivedMetrics pipeline with intervalMs = sourceIntervalMs * N, so the pipeline believes it is
 * looking at real footage captured at that lower rate.
 *
 * Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.FrameRateSensitivityHarness"`
 * and read stdout — that IS the deliverable.
 */
class FrameRateSensitivityHarness {

    private fun loadSequence(): PoseSequence2D {
        val file = File("../Videos/andrii_1/andrii_1_poses_mediapipe_lite.json")
        require(file.exists()) {
            "Missing ${file.path} (absolute: ${file.absolutePath}, cwd: ${File(".").absolutePath})"
        }
        return PoseJsonV2Parser.parse(file.readText())
    }

    @Test
    fun printFrameRateSensitivityTable() {
        val seq = loadSequence()
        val handedness = Handedness.RIGHT
        val xScale = seq.aspectRatio

        println("=== Frame-rate sensitivity: andrii_1 (MediaPipe-lite), source intervalMs=${seq.intervalMs}, frames=${seq.frames.size} ===")

        val sampleFactors = listOf(1, 2, 3, 4, 6, 8, 12)

        for (n in sampleFactors) {
            val decimatedFrames = seq.frames.filterIndexed { idx, _ -> idx % n == 0 }
            val effectiveIntervalMs = seq.intervalMs * n

            val detected = StrokeDetector2D().detect(decimatedFrames, handedness, xScale, effectiveIntervalMs)
            val forward = ForwardStrokeFilter.filter(detected, decimatedFrames, handedness)
            val banded = RepFilter.filter(forward)
            val stationary = LocomotionFilter.filterStationary(banded, decimatedFrames, xScale)

            data class RepMetrics(val strokeSpeed: Double?, val elbow: Double?, val knee: Double?, val torsoLean: Double?)

            val repMetrics = stationary.map { stroke ->
                val peakMetrics = DrillMetrics.extractAtPeak(
                    decimatedFrames, stroke.peakFrame, handedness, xScale, effectiveIntervalMs
                )
                val derived = DerivedMetrics.merge(
                    decimatedFrames, stroke, handedness, xScale, effectiveIntervalMs
                )
                RepMetrics(
                    strokeSpeed = derived["stroke_speed"],
                    elbow = peakMetrics["elbow_angle"],
                    knee = peakMetrics["knee_bend"],
                    torsoLean = peakMetrics["torso_lean"]
                )
            }

            val speeds = repMetrics.mapNotNull { it.strokeSpeed }

            val meanSpeed = if (speeds.isNotEmpty()) speeds.average() else null
            val minSpeed = speeds.minOrNull()
            val maxSpeed = speeds.maxOrNull()

            println(
                "N=$n effectiveIntervalMs=$effectiveIntervalMs frames=${decimatedFrames.size} " +
                    "detected=${detected.size} forward=${forward.size} banded=${banded.size} " +
                    "reps=${stationary.size} stroke_speed(mean/min/max)=" +
                    "${meanSpeed?.let { "%.2f".format(it) } ?: "n/a"}/" +
                    "${minSpeed?.let { "%.2f".format(it) } ?: "n/a"}/" +
                    "${maxSpeed?.let { "%.2f".format(it) } ?: "n/a"}"
            )

            if (n == 1) {
                val elbowVals = repMetrics.mapNotNull { it.elbow }
                val kneeVals = repMetrics.mapNotNull { it.knee }
                val torsoVals = repMetrics.mapNotNull { it.torsoLean }
                println(
                    "  N=1 other-metric means: elbow_angle=" +
                        "${if (elbowVals.isNotEmpty()) "%.2f".format(elbowVals.average()) else "n/a"} " +
                        "knee_bend=${if (kneeVals.isNotEmpty()) "%.2f".format(kneeVals.average()) else "n/a"} " +
                        "torso_lean=${if (torsoVals.isNotEmpty()) "%.2f".format(torsoVals.average()) else "n/a"}"
                )
            }
        }

        // Trivially-true sanity assertion — the printed table above is the actual deliverable.
        assertTrue(true)
    }
}
