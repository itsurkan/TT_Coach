package com.ttcoachai.shared.analysis

import com.ttcoachai.shared.TestFixtures
import com.ttcoachai.shared.detection.JsonStrokeDetector
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Diagnostic runner for the new canonical-stroke pipeline on the andrii_1
 * fixture. Prints stroke count, selected rep, and motion amplitude under each
 * Method. Run with:
 *
 *     ./gradlew :shared:jvmTest --tests CanonicalStrokeBuilderAndriiDiagnostic
 *
 * Asserts that at least the BEST_REP output preserves meaningful motion
 * (amplitude > 0.05 in normalized image coords).
 */
class CanonicalStrokeBuilderAndriiDiagnostic {

    @Test
    fun rebuildAndriiCanonicalStroke() {
        val frames = TestFixtures.loadForehandAndrii()
        val intervalMs = TestFixtures.loadIntervalMs("fixtures/andrii_1.json")

        println("--- andrii_1 fixture ---")
        println("frames=${frames.size}, intervalMs=$intervalMs")

        val detection = JsonStrokeDetector().detect(frames)
        println("strokes_detected=${detection.strokes.size}")
        detection.strokes.forEach { s ->
            println(
                "  stroke#${s.strokeIndex}: prep=${s.preparationStartFrame}..${s.preparationEndFrame}" +
                    ", fwd=${s.forwardStartFrame}..${s.forwardEndFrame}" +
                    ", contact=${s.contactFrame}" +
                    ", ret=${s.returnStartFrame}..${s.returnEndFrame}" +
                    ", dur=${s.strokeDurationMs}ms"
            )
        }

        if (detection.strokes.isEmpty()) {
            println("WARNING: no strokes detected — JsonStrokeDetector config mismatch.")
            return
        }

        // Diagnostics for each method
        for (method in CanonicalStrokeBuilder.Method.values()) {
            val cfg = CanonicalStrokeBuilder.Config(
                method = method,
                phaseAlign = true,
                smoothingRadius = 1,
                loopBlend = true
            )
            val result = CanonicalStrokeBuilder.build(
                allFrames = frames,
                strokes = detection.strokes,
                intervalMs = intervalMs,
                config = cfg
            )
            println(
                "method=$method: frames=${result.frames.size}" +
                    ", selected=${result.selectedStrokeIndex}" +
                    ", motionAmp=${"%.4f".format(result.motionAmplitude)}"
            )
        }

        // Now the same, with phaseAlign off, to see the smearing effect
        for (method in CanonicalStrokeBuilder.Method.values()) {
            val cfg = CanonicalStrokeBuilder.Config(
                method = method,
                phaseAlign = false,
                smoothingRadius = 1,
                loopBlend = true
            )
            val result = CanonicalStrokeBuilder.build(
                allFrames = frames,
                strokes = detection.strokes,
                intervalMs = intervalMs,
                config = cfg
            )
            println(
                "method=$method phaseAlign=OFF: frames=${result.frames.size}" +
                    ", selected=${result.selectedStrokeIndex}" +
                    ", motionAmp=${"%.4f".format(result.motionAmplitude)}"
            )
        }

        // Acceptance: BEST_REP with phase-align should preserve motion.
        val best = CanonicalStrokeBuilder.build(
            allFrames = frames,
            strokes = detection.strokes,
            intervalMs = intervalMs,
            config = CanonicalStrokeBuilder.Config(
                method = CanonicalStrokeBuilder.Method.BEST_REP,
                phaseAlign = true
            )
        )
        assertTrue(
            best.frames.isNotEmpty(),
            "BEST_REP produced empty canonical stroke"
        )
        assertTrue(
            best.motionAmplitude > 0.05f,
            "BEST_REP motion amplitude too low (${best.motionAmplitude}) — stroke looks frozen"
        )
    }
}
