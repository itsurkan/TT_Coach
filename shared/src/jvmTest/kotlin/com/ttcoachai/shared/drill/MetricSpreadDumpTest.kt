package com.ttcoachai.shared.drill

import com.ttcoachai.shared.TestFixturesV2
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.io.PoseJsonV2Parser
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PoseSequence2D
import com.ttcoachai.shared.models.Stroke2D
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * DIAGNOSTIC ONLY — not a regression gate, always passes. Dumps raw per-rep metric
 * values (and per-metric spread stats) for the RTM fixtures, using the SAME
 * detect -> ForwardStrokeFilter -> RepFilter -> DrillMetrics.extractAtPeak pipeline
 * as [ShippedBaselineDerivationHarness] / [DrillCalibrator], so the absurd spread in
 * ShippedBaselines.FOREHAND_ANDRII (elbow_angle 64.5 +/- 29.9, follow_through_angle_2d
 * 132.3 +/- 44.3, knee_bend 174.7) can be inspected rep-by-rep: real technique variance,
 * a broken peak-frame choice, or pose-tracking noise.
 *
 * Run: ./gradlew :shared:jvmTest --tests "*MetricSpreadDumpTest*" -i
 */
class MetricSpreadDumpTest {

    private fun loadResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path)
            ?: throw IllegalStateException("Test resource not found on classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }

    private fun loadVideo3Rtm(): PoseSequence2D =
        PoseJsonV2Parser.parse(loadResource("fixtures/video_3_rtm.json"))

    private data class Stat(val n: Int, val min: Double, val p25: Double, val median: Double, val p75: Double, val max: Double, val mean: Double, val std: Double)

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        if (sorted.size == 1) return sorted[0]
        val idx = p * (sorted.size - 1)
        val lo = idx.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        val frac = idx - lo
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
    }

    private fun stat(values: List<Double>): Stat? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return Stat(
            n = values.size,
            min = sorted.first(),
            p25 = percentile(sorted, 0.25),
            median = percentile(sorted, 0.5),
            p75 = percentile(sorted, 0.75),
            max = sorted.last(),
            mean = mean,
            std = sqrt(variance)
        )
    }

    private fun fmt(d: Double?) = if (d == null) "null" else "%.1f".format(d)

    private fun dumpFixture(name: String, seq: PoseSequence2D, handedness: Handedness = Handedness.RIGHT) {
        val xScale = seq.aspectRatio // ViewGeometry(aspectRatio, yaw=0f).xScale == aspectRatio

        val rawPeaks = StrokeDetector2D().detect(seq.frames, handedness, xScale, seq.intervalMs)
        val forward = ForwardStrokeFilter.filter(rawPeaks, seq.frames, handedness)
        val banded = RepFilter.filter(forward)

        println("")
        println("========================================================================")
        println("=== FIXTURE: $name ===")
        println("========================================================================")
        println(
            "intervalMs=${seq.intervalMs} fps=${"%.1f".format(1000.0 / seq.intervalMs)} " +
                "totalFrames=${seq.totalFrames} videoDurationMs=${seq.videoDurationMs} " +
                "videoWidth=${seq.videoWidth} videoHeight=${seq.videoHeight} aspectRatio(xScale)=$xScale"
        )
        println(
            "rawPeakCount=${rawPeaks.size} afterForwardStrokeFilter=${forward.size} afterRepFilter=${banded.size}"
        )

        // Which raw peaks got dropped at each stage, identified by peakFrame (stroke identity
        // isn't preserved as a stable index across stages, so peakFrame is the join key).
        val forwardPeakFrames = forward.map { it.peakFrame }.toSet()
        val bandedPeakFrames = banded.map { it.peakFrame }.toSet()
        val droppedByForward = rawPeaks.filter { it.peakFrame !in forwardPeakFrames }.map { it.peakFrame }
        val droppedByRepFilter = forward.filter { it.peakFrame !in bandedPeakFrames }.map { it.peakFrame }
        println("raw peakFrames=${rawPeaks.map { it.peakFrame }}")
        println("dropped by ForwardStrokeFilter (peakFrame): $droppedByForward")
        println("dropped by RepFilter (peakFrame): $droppedByRepFilter")
        println("KEPT reps (peakFrame): $bandedPeakFrames")

        data class RepRow(val index: Int, val stroke: Stroke2D, val metrics: Map<String, Double>)

        val repRows = banded.mapIndexed { index, stroke ->
            val peakMetrics = DrillMetrics.extractAtPeak(seq.frames, stroke.peakFrame, handedness, xScale, seq.intervalMs)
            val derived = DerivedMetrics.merge(seq.frames, stroke, handedness, xScale, seq.intervalMs)
            RepRow(index, stroke, peakMetrics + derived)
        }

        println("")
        println("--- per-rep table (${name}) ---")
        val header = listOf("rep", "startFrame", "peakFrame", "endFrame") + DrillMetrics.ALL_KEYS
        println(header.joinToString(" | "))
        for (row in repRows) {
            val cells = listOf(
                row.index.toString(), row.stroke.startFrame.toString(),
                row.stroke.peakFrame.toString(), row.stroke.endFrame.toString()
            ) + DrillMetrics.ALL_KEYS.map { fmt(row.metrics[it]) }
            println(cells.joinToString(" | "))
        }

        println("")
        println("--- per-metric summary (${name}): n, min, p25, median, p75, max, mean, sigma ---")
        for (key in DrillMetrics.ALL_KEYS) {
            val values = repRows.mapNotNull { it.metrics[key] }
            val s = stat(values)
            if (s == null) {
                println("$key: n=0 (no values — all reps score-gated or sanity-dropped for this metric)")
            } else {
                println(
                    "$key: n=${s.n} min=${fmt(s.min)} p25=${fmt(s.p25)} median=${fmt(s.median)} " +
                        "p75=${fmt(s.p75)} max=${fmt(s.max)} mean=${fmt(s.mean)} sigma=${fmt(s.std)}"
                )
            }
        }

        if (name == "andrii_1_rtm") {
            println("")
            println("--- andrii_1_rtm: elbow_angle / knee_bend at start/peak/end frame per rep ---")
            println("rep | startFrame elbow/knee | peakFrame elbow/knee | endFrame elbow/knee")
            for (row in repRows) {
                val s = row.stroke
                val startFrame = seq.frames.getOrNull(s.startFrame)
                val peakFrame = seq.frames.getOrNull(s.peakFrame)
                val endFrame = seq.frames.getOrNull(s.endFrame)
                val startM = startFrame?.let { DrillMetrics.extractAtFrame(it, handedness, xScale) }
                val peakM = peakFrame?.let { DrillMetrics.extractAtFrame(it, handedness, xScale) }
                val endM = endFrame?.let { DrillMetrics.extractAtFrame(it, handedness, xScale) }
                println(
                    "rep[${row.index}] " +
                        "start(f=${s.startFrame}) elbow=${fmt(startM?.get(DrillMetrics.METRIC_ELBOW_ANGLE))} knee=${fmt(startM?.get(DrillMetrics.METRIC_KNEE_BEND))} | " +
                        "peak(f=${s.peakFrame}) elbow=${fmt(peakM?.get(DrillMetrics.METRIC_ELBOW_ANGLE))} knee=${fmt(peakM?.get(DrillMetrics.METRIC_KNEE_BEND))} | " +
                        "end(f=${s.endFrame}) elbow=${fmt(endM?.get(DrillMetrics.METRIC_ELBOW_ANGLE))} knee=${fmt(endM?.get(DrillMetrics.METRIC_KNEE_BEND))}"
                )
            }
        }
    }

    @Test
    fun dumpAllFixtures() {
        dumpFixture("andrii_1_rtm", TestFixturesV2.loadAndriiRtm())
        dumpFixture("video_2_rtm", TestFixturesV2.loadVideo2Rtm())
        dumpFixture("video_3_rtm", loadVideo3Rtm())
        dumpFixture("video_4_rtm", TestFixturesV2.loadVideo4Rtm())
        // Diagnostic dump only — always green regardless of what the numbers show.
    }
}
