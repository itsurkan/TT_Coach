package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D

/**
 * The five Phase 2 in-plane forehand-drive metrics (context doc §3), expressed as
 * [MetricSpec]s so generic movement/drill code ([MovementMetrics], [SanityBounds])
 * can operate on any movement's metric list rather than these five specifically.
 * Sanity bounds here are byte-for-byte the values [SanityBounds] used to hard-code;
 * this object is now their single source of truth.
 */
object CoreMetricSpecs {

    val ALL: List<MetricSpec> = listOf(
        MetricSpec(
            key = DrillMetrics.METRIC_ELBOW_ANGLE,
            precision = MetricPrecision.PRECISE_DEGREES,
            sanityBounds = 20.0..170.0,
            extractor = { kp, handedness, xScale, minScore ->
                AngleCalculations2D.elbowAngle(kp, handedness, xScale, minScore)
            }
        ),
        MetricSpec(
            key = DrillMetrics.METRIC_SHOULDER_ANGLE,
            precision = MetricPrecision.PRECISE_DEGREES,
            sanityBounds = 5.0..175.0,
            extractor = { kp, handedness, xScale, minScore ->
                AngleCalculations2D.shoulderAngle(kp, handedness, xScale, minScore)
            }
        ),
        MetricSpec(
            key = DrillMetrics.METRIC_KNEE_BEND,
            precision = MetricPrecision.PRECISE_DEGREES,
            sanityBounds = 60.0..180.0,
            extractor = { kp, handedness, xScale, minScore ->
                AngleCalculations2D.kneeBend(kp, handedness, xScale, minScore)
            }
        ),
        MetricSpec(
            key = DrillMetrics.METRIC_TORSO_LEAN,
            precision = MetricPrecision.PRECISE_DEGREES,
            sanityBounds = -60.0..60.0,
            extractor = { kp, _, xScale, minScore ->
                AngleCalculations2D.torsoLean(kp, xScale, minScore)
            }
        ),
        MetricSpec(
            key = DrillMetrics.METRIC_SHOULDER_TILT,
            precision = MetricPrecision.PRECISE_DEGREES,
            sanityBounds = -60.0..60.0,
            extractor = { kp, _, xScale, minScore ->
                AngleCalculations2D.shoulderTilt(kp, xScale, minScore)
            }
        )
    )
}
