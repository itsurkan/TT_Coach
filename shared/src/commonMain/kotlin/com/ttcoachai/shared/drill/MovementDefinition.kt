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
     * Trust-rule precision for [metricKey], looked up from [metrics]. Unknown keys
     * (e.g. a future rotational cue with no MetricSpec) default to QUALITATIVE —
     * never overclaim a precision this definition doesn't back with a spec.
     */
    fun precisionFor(metricKey: String): MetricPrecision =
        metrics.find { it.key == metricKey }?.precision ?: MetricPrecision.QUALITATIVE
}
