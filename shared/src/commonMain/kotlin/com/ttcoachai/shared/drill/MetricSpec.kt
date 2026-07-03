package com.ttcoachai.shared.drill

import com.ttcoachai.shared.models.Handedness
import com.ttcoachai.shared.models.Keypoint2D

/**
 * One measurable technique metric of a movement (docs/superpowers/specs/
 * 2026-07-02-generic-movement-pipeline-design.md): a key, its trust-rule precision,
 * an optional anatomical sanity band (SanityBounds semantics — values outside are
 * tracking glitches, dropped and never coached on), and the extractor that computes
 * it from a frame's keypoints.
 */
data class MetricSpec(
    val key: String,
    val precision: MetricPrecision,
    /** Values outside are tracking glitches — dropped, never coached (SanityBounds semantics). null = no band. */
    val sanityBounds: ClosedFloatingPointRange<Double>?,
    /** (keypoints, handedness, xScale, minScore) -> value in degrees, or null when score-gated. */
    val extractor: (List<Keypoint2D>, Handedness, Float, Float) -> Float?
)
