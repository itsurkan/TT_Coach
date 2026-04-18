package com.ttcoachai.managers

import com.ttcoachai.shared.analysis.BaselineDeriver
import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Singleton session state for the calibration flow.
 *
 * Calibration feeds the BaselineDeriver off parallel per-rep `DetectedStroke`
 * and `AnalysisResult` lists. PoseAnalysisProcessor records strokes as they
 * complete; this manager runs per-rep 2σ outlier detection against running
 * stats of prior accepted reps (US3), excluding outliers from the counter and
 * surfacing them on [outlierEvents] so the UI can flash a transient banner.
 *
 * Volatile / double-checked locking mirrors [TrainingStateManager]. All
 * mutations funnel through `synchronized(lock)` because the pose pipeline
 * posts from a background thread while the UI reads on main.
 */
class CalibrationStateManager internal constructor() {

    private val lock = Any()

    private val _strokes = mutableListOf<DetectedStroke>()
    private val _analyses = mutableListOf<AnalysisResult>()
    private val _excludedAttempts = mutableListOf<ExcludedAttempt>()
    private var _firstFrameTimestampMs: Long = 0L
    private var _lastFrameTimestampMs: Long = 0L
    private var _frameCount: Int = 0
    private var _attemptIndex: Int = 0

    @Volatile
    var drillType: String? = null
        private set
    @Volatile
    var targetRepCount: Int = DEFAULT_TARGET_REPS
        private set
    @Volatile
    var isCapturing: Boolean = false
        private set

    private val _acceptedRepCount = MutableStateFlow(0)
    val acceptedRepCount: StateFlow<Int> = _acceptedRepCount.asStateFlow()

    private val _excludedCount = MutableStateFlow(0)
    val excludedCount: StateFlow<Int> = _excludedCount.asStateFlow()

    private val _outlierEvents =
        MutableSharedFlow<OutlierDetected>(replay = 0, extraBufferCapacity = 4)
    val outlierEvents: SharedFlow<OutlierDetected> = _outlierEvents.asSharedFlow()

    fun startSession(drillType: String, targetRepCount: Int = DEFAULT_TARGET_REPS) {
        synchronized(lock) {
            _strokes.clear()
            _analyses.clear()
            _excludedAttempts.clear()
            _firstFrameTimestampMs = 0L
            _lastFrameTimestampMs = 0L
            _frameCount = 0
            _attemptIndex = 0
            this.drillType = drillType
            this.targetRepCount = targetRepCount
            this.isCapturing = true
            _acceptedRepCount.value = 0
            _excludedCount.value = 0
        }
    }

    fun onFrameProcessed(timestampMs: Long) {
        synchronized(lock) {
            if (!isCapturing) return
            if (_frameCount == 0) _firstFrameTimestampMs = timestampMs
            _lastFrameTimestampMs = timestampMs
            _frameCount++
        }
    }

    /**
     * Record a completed rep. If it exceeds [OUTLIER_SIGMA_THRESHOLD] σ on any
     * metric relative to already-accepted reps (post-warmup), it is excluded
     * from the counter and surfaced on [outlierEvents] for the UI. Pre-warmup
     * everything is accepted so the initial stats have a starting distribution.
     */
    fun recordStroke(stroke: DetectedStroke, analysis: AnalysisResult) {
        val event: OutlierDetected? = synchronized(lock) {
            if (!isCapturing) return@synchronized null
            val attemptIdx = _attemptIndex++
            val outlierEval = evaluateOutlier(analysis, stroke)
            if (outlierEval != null) {
                _excludedAttempts.add(
                    ExcludedAttempt(
                        attemptedRepIndex = attemptIdx,
                        metricKey = outlierEval.first,
                        deviationSigmas = outlierEval.second
                    )
                )
                _excludedCount.value = _excludedAttempts.size
                OutlierDetected(attemptIdx, outlierEval.first, outlierEval.second)
            } else {
                _strokes.add(stroke)
                _analyses.add(analysis)
                _acceptedRepCount.value = _strokes.size
                null
            }
        }
        if (event != null) _outlierEvents.tryEmit(event)
    }

    fun finishCapture() {
        synchronized(lock) { isCapturing = false }
    }

    fun discardSession() {
        synchronized(lock) {
            _strokes.clear()
            _analyses.clear()
            _excludedAttempts.clear()
            _firstFrameTimestampMs = 0L
            _lastFrameTimestampMs = 0L
            _frameCount = 0
            _attemptIndex = 0
            drillType = null
            isCapturing = false
            _acceptedRepCount.value = 0
            _excludedCount.value = 0
        }
    }

    fun snapshot(): Snapshot? = synchronized(lock) {
        if (_strokes.isEmpty()) return@synchronized null
        Snapshot(
            drillType = drillType ?: return@synchronized null,
            strokes = _strokes.toList(),
            analyses = _analyses.toList(),
            frameIntervalMs = computeFrameIntervalMs()
        )
    }

    /**
     * Returns (metricKey, deviationSigmas) if this new rep is an outlier
     * versus already-accepted reps, else null.
     */
    private fun evaluateOutlier(
        analysis: AnalysisResult,
        stroke: DetectedStroke
    ): Pair<String, Double>? {
        if (_strokes.size < WARMUP_REPS) return null

        val newValues = extractMetricValues(analysis) + extractPhaseDurations(stroke)
        for ((key, value) in newValues) {
            val (mean, std) = runningMeanStd(key) ?: continue
            if (std <= 0.0) continue
            val sigmas = abs(value - mean) / std
            if (sigmas > OUTLIER_SIGMA_THRESHOLD) return key to sigmas
        }
        return null
    }

    private fun runningMeanStd(metricKey: String): Pair<Double, Double>? {
        val values = _analyses.mapNotNull { extractMetricValues(it)[metricKey] } +
            _strokes.mapNotNull { extractPhaseDurations(it)[metricKey] }
        if (values.size < WARMUP_REPS) return null
        val mean = values.sum() / values.size
        val sumSq = values.sumOf { (it - mean) * (it - mean) }
        val std = sqrt(sumSq / (values.size - 1))
        return mean to std
    }

    private fun extractMetricValues(result: AnalysisResult): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        result.wristAngle?.let { out[BaselineDeriver.METRIC_WRIST_ANGLE] = it.toDouble() }
        result.bodyRotation?.let { out[BaselineDeriver.METRIC_BODY_ROTATION] = it.toDouble() }
        result.followThroughAngle?.let { out[BaselineDeriver.METRIC_FOLLOW_THROUGH_ANGLE] = it.toDouble() }
        result.contactHeight?.let { out[BaselineDeriver.METRIC_CONTACT_HEIGHT] = it.toDouble() }
        result.elbowBodyDistance?.let { out[BaselineDeriver.METRIC_ELBOW_BODY_DISTANCE] = it.toDouble() }
        return out
    }

    private fun extractPhaseDurations(stroke: DetectedStroke): Map<String, Double> {
        val interval = computeFrameIntervalMs().toDouble()
        val backswing = (stroke.preparationEndFrame - stroke.preparationStartFrame).coerceAtLeast(0) * interval
        val forward = (stroke.forwardEndFrame - stroke.forwardStartFrame).coerceAtLeast(0) * interval
        val followThrough = (stroke.returnEndFrame - stroke.returnStartFrame).coerceAtLeast(0) * interval
        return mapOf(
            BaselineDeriver.PHASE_BACKSWING_MS to backswing,
            BaselineDeriver.PHASE_FORWARD_SWING_MS to forward,
            BaselineDeriver.PHASE_FOLLOW_THROUGH_MS to followThrough,
            BaselineDeriver.PHASE_STROKE_TOTAL_MS to stroke.strokeDurationMs.toDouble().coerceAtLeast(0.0)
        )
    }

    private fun computeFrameIntervalMs(): Long {
        if (_frameCount <= 1) return DEFAULT_FRAME_INTERVAL_MS
        val spanMs = _lastFrameTimestampMs - _firstFrameTimestampMs
        if (spanMs <= 0) return DEFAULT_FRAME_INTERVAL_MS
        return (spanMs / (_frameCount - 1)).coerceAtLeast(1L)
    }

    data class Snapshot(
        val drillType: String,
        val strokes: List<DetectedStroke>,
        val analyses: List<AnalysisResult>,
        val frameIntervalMs: Long
    )

    data class OutlierDetected(
        val attemptedRepIndex: Int,
        val metricKey: String,
        val deviationSigmas: Double
    )

    data class ExcludedAttempt(
        val attemptedRepIndex: Int,
        val metricKey: String,
        val deviationSigmas: Double
    )

    companion object {
        const val DEFAULT_TARGET_REPS = 15
        const val MIN_REPS_TO_PERSIST = 10
        const val WARMUP_REPS = 3
        const val OUTLIER_SIGMA_THRESHOLD = 2.0
        private const val DEFAULT_FRAME_INTERVAL_MS = 33L

        @Volatile
        private var instance: CalibrationStateManager? = null

        fun getInstance(): CalibrationStateManager {
            return instance ?: synchronized(this) {
                instance ?: CalibrationStateManager().also { instance = it }
            }
        }
    }
}
