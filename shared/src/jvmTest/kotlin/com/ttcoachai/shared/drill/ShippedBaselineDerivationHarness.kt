package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ONE-TIME editorial derivation tool for `ShippedBaselines.FOREHAND_ANDRII` (docs/superpowers/
 * specs/2026-07-27-no-calibration-shipped-baseline-design.md §1). Not a regression gate on its
 * own numbers — prints per-rep diagnostics (metrics + |yaw|) plus the final derived
 * PersonalBaseline so a human can visually sanity-check rep selection (poses_viewer /
 * visualize-pose skill) before pasting numbers into ShippedBaselines.kt.
 *
 * `cameraYawDeg` is pinned to 0f — the SAME established convention
 * `ForehandDriveEndToEndTest.calibrated()` already uses for this exact fixture family — a
 * DELIBERATE, one-time relaxation of the placement gate for this editorial derivation only
 * (real per-rep |yaw| on this footage runs ~41-90°, past the normal ~30° gate; spec §1
 * explicitly sanctions relaxing it here, "an intentional exception, not a precedent").
 *
 * Run: `./gradlew :shared:jvmTest --tests "com.ttcoachai.shared.drill.ShippedBaselineDerivationHarness"`
 * and read the captured stdout — that IS this task's deliverable, copied into
 * docs/shipped-baseline-derivation.md and pasted into ShippedBaselines.kt (Task B).
 */
class ShippedBaselineDerivationHarness {

    private fun loadSequence(): PoseSequence2D {
        // Gradle's jvmTest working directory is the `shared/` module dir, so `../` reaches
        // the repo root. If this file isn't found, print `File(".").absolutePath` and adjust
        // the relative prefix — do not silently swap in a different (e.g. classpath) fixture.
        val file = File("../Videos/andrii_1/andrii_1_poses_mediapipe_lite.json")
        require(file.exists()) {
            "Missing ${file.path} (absolute: ${file.absolutePath}, cwd: ${File(".").absolutePath}) " +
                "— run `.venv/bin/python scripts/poses/export_poses_mediapipe.py " +
                "Videos/andrii_1/andrii_1.mp4 --model lite` from the repo root first (Task A step 1)."
        }
        return PoseJsonV2Parser.parse(file.readText())
    }

    @Test
    fun printPerRepDiagnosticsAndDerivedBaseline() {
        val seq = loadSequence()
        val handedness = Handedness.RIGHT
        val xScale = seq.aspectRatio // cameraYawDeg=0 -> ViewGeometry(aspectRatio, 0f).xScale == aspectRatio

        // Same detect -> ForwardStrokeFilter -> RepFilter -> LocomotionFilter chain
        // DrillCalibrator.calibrate() runs internally (mirrors LiveDrillSession.onFrame /
        // ForehandDriveDrillAnalyzer.analyze) — replicated here with PUBLIC apis only, purely
        // to print per-rep diagnostics BEFORE derivation's own 2-sigma outlier exclusion runs.
        val detected = StrokeDetector2D().detect(seq.frames, handedness, xScale, seq.intervalMs)
        val forward = ForwardStrokeFilter.filter(detected, seq.frames, handedness)
        val banded = RepFilter.filter(forward)
        val stationary = LocomotionFilter.filterStationary(banded, seq.frames, xScale)

        println("=== ShippedBaseline derivation: andrii_1 (MediaPipe-lite) ===")
        println("createdAtMs candidate (paste literal): ${System.currentTimeMillis()}")
        println(
            "raw detected=${detected.size} forward=${forward.size} banded=${banded.size} " +
                "stationary=${stationary.size}"
        )

        stationary.forEachIndexed { index, stroke ->
            val yaw = CameraAngleEstimator.estimateYawForStroke(seq.frames, stroke, xScale, seq.intervalMs)
            val metrics = DrillMetrics.extractAtPeak(seq.frames, stroke.peakFrame, handedness, xScale, seq.intervalMs) +
                DerivedMetrics.merge(seq.frames, stroke, handedness, xScale, seq.intervalMs)
            val metricsStr = metrics.entries.joinToString(", ") { (k, v) -> "$k=${"%.1f".format(v)}" }
            val yawStr = yaw?.let { "%.1f".format(it) } ?: "null"
            println("rep[$index] peakFrame=${stroke.peakFrame} startFrame=${stroke.startFrame} " +
                "endFrame=${stroke.endFrame} yaw=$yawStr $metricsStr")
        }

        // Actual derivation, cameraYawDeg pinned per the class doc above.
        val baseline = DrillCalibrator.calibrate(
            sequence = seq,
            drillType = "forehand_drive",
            createdAtMs = 1L,
            handedness = handedness,
            minRepCount = 3,
            cameraYawDeg = 0f
        )

        println("=== Derived PersonalBaseline ===")
        println(
            "repCount=${baseline.repCount} excludedRepIndices=${baseline.excludedRepIndices} " +
                "qualityScore=${baseline.qualityScore}"
        )
        println("--- metricStats (paste into ShippedBaselines.FOREHAND_ANDRII.metricStats) ---")
        for (key in DrillMetrics.ALL_KEYS) {
            val stats = baseline.metricStats[key]
            println("\"$key\" to MetricStats(mean=${stats?.mean}, std=${stats?.std}, " +
                "min=${stats?.min}, max=${stats?.max}, sampleCount=${stats?.sampleCount}),")
        }
        println("--- phaseDurationsMs (paste into ShippedBaselines.FOREHAND_ANDRII.phaseDurationsMs) ---")
        for ((key, stats) in baseline.phaseDurationsMs) {
            println("\"$key\" to MetricStats(mean=${stats.mean}, std=${stats.std}, " +
                "min=${stats.min}, max=${stats.max}, sampleCount=${stats.sampleCount}),")
        }

        // Sanity only — this harness's value is the printed diagnostics above, not an assertion.
        assertTrue(baseline.repCount > 0, "derivation must yield at least one kept rep")
        assertTrue(baseline.metricStats.isNotEmpty(), "at least some in-plane metrics must derive")
    }
}
