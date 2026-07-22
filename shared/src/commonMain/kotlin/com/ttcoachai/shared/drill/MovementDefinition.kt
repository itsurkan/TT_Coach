package com.ttcoachai.shared.drill

import com.ttcoachai.shared.detection.DetectionConfig

/**
 * Everything that distinguishes one movement (forehand drive, backhand drive, a
 * future footwork drill, ...) from another, expressed as pure data (docs/superpowers/
 * specs/2026-07-02-generic-movement-pipeline-design.md): which signal keypoint and
 * tuning drives detection ([detection]), which candidate filters the rep pipeline
 * applies ([repValidation]), which metrics are extracted per rep ([metrics]), and
 * which phrases narrate deviations ([messages]). [MovementRepPipeline],
 * [MovementAnalyzer] and [MovementCalibrator] are the movement-agnostic engines that
 * consume a [MovementDefinition]; adding a new movement is a new object, not new code.
 */
data class MovementDefinition(
    /** Also used as PersonalBaseline.drillType. */
    val id: String,
    val detection: DetectionConfig = DetectionConfig(),
    val repValidation: RepValidationConfig = RepValidationConfig(),
    val metrics: List<MetricSpec>,
    val messages: MessageTemplates
) {
    /**
     * Trust-rule precision for [metricKey]. First honours an explicit per-metric precision from
     * this definition's [metrics]; for keys not in [metrics] (e.g. derived metrics computed outside
     * the peak-spec list) it defers to [MetricPrecisionPolicy] rather than blindly returning
     * QUALITATIVE — so the batch path reports the same precision the live path does.
     */
    fun precisionFor(metricKey: String): MetricPrecision =
        metrics.find { it.key == metricKey }?.precision ?: MetricPrecisionPolicy.precisionFor(metricKey)
}
