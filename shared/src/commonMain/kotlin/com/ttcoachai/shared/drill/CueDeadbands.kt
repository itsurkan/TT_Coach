package com.ttcoachai.shared.drill

/**
 * Minimum absolute deviation required before [DrillFeedbackEngine.evaluateRep] emits a
 * cue for a metric, even though the value is technically outside the band/baseline rule.
 *
 * Evidence (device log, session band [169.9, 179.6] derived from baseline mean=174.7,
 * std=2.4): the SAME rep (`atMs=1139817813`), emitted twice from adjacent peak frames,
 * measured `knee_bend=169.1` and `knee_bend=172.5` — 3.4° of jitter on one stroke — and
 * `elbow_angle=76.5` vs `84.5` — ~8° of jitter. That spread is live MediaPipe
 * measurement noise, not player movement between frames of the same stroke. The
 * baseline's σ describes the player's stroke-to-stroke *consistency*, measured from a
 * full-fps desktop pose export — it does not describe live on-device measurement error.
 * A live excursion smaller than that noise floor is indistinguishable from jitter and
 * must not be coached.
 */
object CueDeadbands {

    /**
     * Models live pose-estimation noise on the in-plane angle metrics, NOT player
     * tolerance — player tolerance is already encoded separately by the band/σ this
     * deadband is compared against. See the class doc for the measured evidence
     * (3.4° same-rep knee_bend spread, ~8° same-rep elbow_angle spread, both from
     * adjacent peak frames of a single stroke).
     */
    private const val LIVE_POSE_NOISE_DEG = 3.0

    /**
     * @return the minimum `abs(delta)` (see [DrillFeedbackEngine.evaluateRep]) required
     * before a cue is emitted for [metricKey]. [LIVE_POSE_NOISE_DEG] for the five
     * precise in-plane angle metrics — reuses [MetricPrecisionPolicy.precisionFor]'s
     * classification (`PRECISE_DEGREES`) rather than re-listing the metric keys here, so
     * the two policies can never silently diverge. 0.0 (no deadband — unchanged
     * behavior) for everything else, including `stroke_speed` and `coil_ratio`: they are
     * qualitative proxies in different units, where a degrees floor is meaningless.
     */
    fun forMetric(metricKey: String): Double =
        if (MetricPrecisionPolicy.precisionFor(metricKey) == MetricPrecision.PRECISE_DEGREES) {
            LIVE_POSE_NOISE_DEG
        } else {
            0.0
        }
}
