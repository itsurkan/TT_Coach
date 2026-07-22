package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.BaselineRuleFactory
import com.ttcoachai.shared.detection.StrokeDetector2D
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.ViewGeometry

/**
 * Streaming wrapper (Phase 3 tasks A1+A2) over the SAME batch pipeline
 * [ForehandDriveDrillAnalyzer] uses (`StrokeDetector2D.detect -> ForwardStrokeFilter ->
 * RepFilter -> LocomotionFilter`, then per-rep via [DrillRepProcessor]) — feeds camera
 * frames one at a time and emits [SpokenFeedback] incrementally as strokes stabilize.
 *
 * Correctness backbone of Phase 3 live feedback: constructor params/defaults mirror
 * [ForehandDriveDrillAnalyzer] exactly so batch and live stay configured identically
 * (this is what makes the A3 batch-parity gate meaningful).
 */
class LiveDrillSession(
    private val baseline: PersonalBaseline,
    /** Camera frame width/height (keypoints are per-axis normalized). */
    private val aspectRatio: Float,
    rules: List<BaselineRule> = BaselineRuleFactory.defaultRules(baseline),
    private val handedness: Handedness = Handedness.RIGHT,
    private val lang: FeedbackLang = FeedbackLang.EN,
    /** ONE long-lived instance — cadence state spans the whole live session. */
    private val cadence: FeedbackCadencePolicy = FeedbackCadencePolicy(),
    private val detector: StrokeDetector2D = StrokeDetector2D(),
    private val cameraYawDeg: Float? = null,
    private val maxCameraYawDeg: Float = DrillCalibrator.DEFAULT_MAX_CAMERA_YAW_DEG,
    private val hipTravelMaxTorso: Float = LocomotionFilter.DEFAULT_MAX_TRAVEL_TORSO,
    private val bufferMs: Long = 4000L,
    /**
     * Optional explicit min..max bands (e.g. custom-drill editor "knees · strike" target)
     * that REPLACE the baseline consistency rule for the given metric key — see
     * [BaselineRuleFactory.applyRangeOverrides]. [DrillMetrics.extractAtPeak] only ever
     * produces ONE `knee_bend` value per rep (median at the stroke's wrist-speed peak,
     * i.e. the strike) — there is no separate backswing-phase metric extraction yet, so
     * a backswing band has nothing to attach to and is silently unused if passed here.
     * Empty map (default) reproduces the exact pre-existing baseline-only behavior.
     */
    metricBands: Map<String, ClosedRange<Double>> = emptyMap()
) {
    private val rules: List<BaselineRule> = BaselineRuleFactory.applyRangeOverrides(rules, metricBands)

    init {
        require(maxCameraYawDeg <= ViewGeometry.MAX_YAW_DEG) {
            "maxCameraYawDeg must be <= ViewGeometry.MAX_YAW_DEG (${ViewGeometry.MAX_YAW_DEG}°), " +
                "got $maxCameraYawDeg — the 1/cos xScale correction is undefined beyond it"
        }
    }

    /**
     * Optional per-rep observer, fired once per newly-stabilized/emitted stroke
     * (same dedup point as the [SpokenFeedback] emission below), for UI rep
     * counters that need every rep — not just the ones with something to say
     * (a clean rep still increments a counter, even though [SpokenFeedback] may
     * be null/cadence-suppressed for it). Defaults to null so existing callers
     * that never set it see no behavior change (`?.invoke` is a no-op).
     */
    var onRep: ((RepEvent) -> Unit)? = null

    /** Rolling buffer; each [PoseFrame2D] already carries its own real timestampMs. */
    private val frames = mutableListOf<PoseFrame2D>()

    /** Real timestamps of stroke peaks already processed (dedup key — never an index,
     *  since indices shift when the buffer trims). */
    private val emittedPeaks = mutableSetOf<Long>()

    private var latest: List<Keypoint2D>? = null

    /** Most recent frame's keypoints, for the overlay. Null before any frame. */
    fun latestSkeleton(): List<Keypoint2D>? = latest

    fun reset() {
        frames.clear()
        emittedPeaks.clear()
        cadence.reset()
        latest = null
    }

    fun onFrame(keypoints: List<Keypoint2D>, timestampMs: Long): List<SpokenFeedback> {
        frames.add(PoseFrame2D(frameIndex = frames.size, timestampMs = timestampMs, keypoints = keypoints))
        latest = keypoints

        if (frames.size < 2) return emptyList()

        val intervalMs = medianIntervalMs()

        val detected = detector.detect(frames, handedness, aspectRatio, intervalMs)
        val strokes = LocomotionFilter.filterStationary(
            RepFilter.filter(ForwardStrokeFilter.filter(detected, frames, handedness)),
            frames, aspectRatio, hipTravelMaxTorso
        )

        val feedback = mutableListOf<SpokenFeedback>()
        var minNeededIndex = frames.size // nothing pending by default
        for (stroke in strokes) {
            val stabilized = stroke.endFrame < frames.lastIndex
            val peakTimestamp = frames[stroke.peakFrame].timestampMs
            if (stabilized && peakTimestamp !in emittedPeaks) {
                val rep = DrillRepProcessor.computeRep(
                    frames = frames,
                    stroke = stroke,
                    aspectRatio = aspectRatio,
                    intervalMs = intervalMs,
                    handedness = handedness,
                    baseline = baseline,
                    rules = rules,
                    cameraYawOverride = cameraYawDeg,
                    maxCameraYawDeg = maxCameraYawDeg
                )
                val atMs = frames[stroke.endFrame].timestampMs
                // Mark processed exactly once, whether or not feedback is spoken, so
                // cadence.offer fires once per rep — matching the batch analyzer.
                emittedPeaks.add(peakTimestamp)
                onRep?.invoke(RepEvent(atMs = atMs, cueCount = rep.cues.size, placementOk = rep.placementOk))
                val spoken = DrillRepProcessor.emitRepFeedback(rep, atMs, cadence, lang)
                if (spoken != null) feedback += spoken
            } else if (!stabilized) {
                // Still forming: never trim past this stroke's frames.
                if (stroke.startFrame < minNeededIndex) minNeededIndex = stroke.startFrame
            }
        }

        trim(minNeededIndex)

        return feedback
    }

    /** Median of consecutive timestamp deltas across the buffer — robust to jitter
     *  and dropped frames (L-26/L-02). Clamped to at least 1ms. */
    private fun medianIntervalMs(): Long {
        val deltas = ArrayList<Long>(frames.size - 1)
        for (i in 1 until frames.size) {
            deltas.add(frames[i].timestampMs - frames[i - 1].timestampMs)
        }
        val sorted = deltas.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        return median.coerceAtLeast(1L)
    }

    /** Drops frames older than [bufferMs] relative to the latest timestamp, but never
     *  past [minNeededIndex] (the earliest frame any un-emitted/not-yet-stabilized
     *  stroke still needs), keeping [frames] index-aligned (frameIndex is rewritten). */
    private fun trim(minNeededIndex: Int) {
        val latestTs = frames.last().timestampMs
        val cutoffTs = latestTs - bufferMs
        var dropCount = 0
        while (dropCount < frames.size &&
            dropCount < minNeededIndex &&
            frames[dropCount].timestampMs < cutoffTs
        ) {
            dropCount++
        }
        if (dropCount == 0) return
        repeat(dropCount) { frames.removeAt(0) }
        for (i in frames.indices) {
            frames[i] = frames[i].copy(frameIndex = i)
        }
    }
}

/**
 * Fired once per rep [LiveDrillSession] emits (whether or not it produced
 * spoken feedback), for UI rep counters that need to tally every completed
 * rep rather than only the ones with a cue to speak.
 */
public data class RepEvent(val atMs: Long, val cueCount: Int, val placementOk: Boolean)
