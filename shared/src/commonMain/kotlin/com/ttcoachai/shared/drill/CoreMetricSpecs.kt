package com.ttcoachai.shared.drill

import com.ttcoachai.shared.analysis.AngleCalculations2D

/**
 * The four Phase 2 in-plane forehand-drive peak metrics (context doc §3), expressed
 * as [MetricSpec]s so generic movement/drill code ([MovementMetrics], [SanityBounds])
 * can operate on any movement's metric list rather than these four specifically.
 * Sanity bounds here are byte-for-byte the values [SanityBounds] used to hard-code;
 * this object is now their single source of truth. [ALL] is the peak-extracted spec
 * list only ([DrillMetrics.PEAK_KEYS]) — `shoulder_tilt` is no longer a member (still
 * a defined [DrillMetrics.METRIC_SHOULDER_TILT] constant, just not peak-extracted).
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
        )
    )

    /**
     * NOT included in [ALL] — exists for a later task's derived-merge at endFrame.
     * Same shoulder-elbow-wrist extractor as [DrillMetrics.METRIC_ELBOW_ANGLE]; nothing
     * consumes this yet.
     */
    val FOLLOW_THROUGH: MetricSpec = MetricSpec(
        key = DrillMetrics.METRIC_FOLLOW_THROUGH_ANGLE_2D,
        precision = MetricPrecision.PRECISE_DEGREES,
        sanityBounds = 20.0..170.0,
        extractor = { kp, handedness, xScale, minScore ->
            AngleCalculations2D.elbowAngle(kp, handedness, xScale, minScore)
        }
    )
}
