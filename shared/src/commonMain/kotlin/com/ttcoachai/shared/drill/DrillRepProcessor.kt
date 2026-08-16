package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.BaselineRule
import com.ttcoachai.shared.analysis.CameraAngleEstimator
import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.PersonalBaseline
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Stroke2D
import com.ttcoachai.shared.models.ViewGeometry
import kotlin.math.abs

/**
 * Shared per-rep flow: yaw resolution → placement gate → ViewGeometry → metrics →
 * cues → cadenced feedback. Extracted from [ForehandDriveDrillAnalyzer] (behavior-preserving,
 * Phase 3 task A0) so the batch analyzer and the live session ([LiveDrillSession], task A1)
 * run byte-identical per-rep logic.
 */
internal object DrillRepProcessor {

    /**
     * Pure, stateless per-rep computation: resolves this rep's camera yaw, gates on
     * placement, corrects xScale, extracts metrics at the stroke peak, and evaluates
     * baseline-rule cues. No cadence, no language — safe to call from any context.
     */
    internal fun computeRep(
        frames: List<PoseFrame2D>,
        stroke: Stroke2D,
        aspectRatio: Float,
        intervalMs: Long,
        handedness: Handedness,
        baseline: PersonalBaseline,
        rules: List<BaselineRule>,
        cameraYawOverride: Float?,
        maxCameraYawDeg: Float
    ): RepAnalysis {
        val yaw = cameraYawOverride
            ?: CameraAngleEstimator.estimateYawForStroke(frames, stroke, aspectRatio, intervalMs)
        // Null yaw = placement unverifiable → fails the gate (conservatism: an
        // unverifiable rep must not trigger feedback), same handling as over-yaw.
        val placementOk = yaw != null && abs(yaw) <= maxCameraYawDeg
        // Beyond the gate (or unmeasurable) the 1/cos model is unreliable: fall back
        // to plain aspect (this rep's metrics become diagnostics only; no cues).
        val view = if (yaw != null && placementOk) ViewGeometry(aspectRatio, yaw)
                   else ViewGeometry(aspectRatio)
        val metrics = DrillMetrics.extractAtPeak(frames, stroke.peakFrame, handedness, view.xScale, intervalMs) +
            DerivedMetrics.merge(frames, stroke, handedness, view.xScale, intervalMs)
        val cues = if (placementOk) DrillFeedbackEngine.evaluateRep(metrics, baseline, rules)
                   else emptyList()
        return RepAnalysis(stroke, metrics, cues, cameraYawDeg = yaw, placementOk = placementOk)
    }

    /**
     * Stateful per-rep emission: applies the cadence policy to one rep's cues and
     * returns the spoken feedback for it, or null if nothing should be said now.
     *
     * [cuesForCadence] is what actually competes for the cadence window — defaults to
     * [RepAnalysis.cues] (unfiltered) so existing callers see zero behavior change.
     * Callers that need to drop cues the user has muted (e.g. a disabled correction-type
     * chip) must filter BEFORE calling this, and pass the filtered list here — passing an
     * empty list makes [FeedbackCadencePolicy.offer] return null WITHOUT consuming the
     * cadence window (see its own null-top-cue short-circuit), so a rep whose only cues
     * are all muted stays silent but never blocks the next rep from speaking.
     *
     * The positive-reinforcement branch below deliberately still reads the UNFILTERED
     * [RepAnalysis.cues] — a rep that had real corrections which just got muted is not a
     * "clean rep" and must not trigger positive reinforcement in their place.
     */
    internal fun emitRepFeedback(
        rep: RepAnalysis,
        atMs: Long,
        cadence: FeedbackCadencePolicy,
        lang: FeedbackLang,
        cuesForCadence: List<FeedbackCue> = rep.cues
    ): SpokenFeedback? {
        if (!rep.placementOk) return null // silent rep; UI surfaces the placement flag
        val cue = cadence.offer(atMs, cuesForCadence)
        return when {
            cue != null ->
                SpokenFeedback(atMs, FeedbackMessageCatalog.format(cue, lang), cue)
            rep.cues.isEmpty() && rep.metrics.isNotEmpty() && cadence.offerPositive(atMs) ->
                SpokenFeedback(atMs, FeedbackMessageCatalog.positive(lang), null)
            else -> null
        }
    }
}
