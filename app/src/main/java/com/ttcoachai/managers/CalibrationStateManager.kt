package com.ttcoachai.managers

import com.ttcoachai.shared.models.AnalysisResult
import com.ttcoachai.shared.models.DetectedStroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton session state for the calibration flow.
 *
 * Calibration feeds the BaselineDeriver off parallel per-rep `DetectedStroke`
 * and `AnalysisResult` lists, so this manager collects both as the capture
 * fragment pulls them from PoseAnalysisProcessor's stroke-completion callback.
 *
 * Volatile / double-checked locking mirrors [TrainingStateManager]. All
 * mutations funnel through `synchronized(lock)` because the pose pipeline
 * may post from a background thread while the UI reads on main.
 */
class CalibrationStateManager internal constructor() {

    private val lock = Any()

    private val _strokes = mutableListOf<DetectedStroke>()
    private val _analyses = mutableListOf<AnalysisResult>()
    private var _firstFrameTimestampMs: Long = 0L
    private var _lastFrameTimestampMs: Long = 0L
    private var _frameCount: Int = 0

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
    /** Number of accepted (non-excluded) reps captured so far. UI binds here. */
    val acceptedRepCount: StateFlow<Int> = _acceptedRepCount.asStateFlow()

    /**
     * Begin a new calibration capture. Clears any prior session state.
     */
    fun startSession(drillType: String, targetRepCount: Int = DEFAULT_TARGET_REPS) {
        synchronized(lock) {
            _strokes.clear()
            _analyses.clear()
            _firstFrameTimestampMs = 0L
            _lastFrameTimestampMs = 0L
            _frameCount = 0
            this.drillType = drillType
            this.targetRepCount = targetRepCount
            this.isCapturing = true
            _acceptedRepCount.value = 0
        }
    }

    /**
     * Called by the pose pipeline on each processed frame so we can compute
     * `frameIntervalMs` for BaselineDeriver from real observed timestamps
     * rather than assuming 30 fps.
     */
    fun onFrameProcessed(timestampMs: Long) {
        synchronized(lock) {
            if (!isCapturing) return
            if (_frameCount == 0) _firstFrameTimestampMs = timestampMs
            _lastFrameTimestampMs = timestampMs
            _frameCount++
        }
    }

    fun recordStroke(stroke: DetectedStroke, analysis: AnalysisResult) {
        synchronized(lock) {
            if (!isCapturing) return
            _strokes.add(stroke)
            _analyses.add(analysis)
            _acceptedRepCount.value = _strokes.size
        }
    }

    /** Stop capture without discarding buffered strokes — used when auto-advancing to review. */
    fun finishCapture() {
        synchronized(lock) { isCapturing = false }
    }

    /** Abandon the session entirely — used when the user exits before reaching the floor. */
    fun discardSession() {
        synchronized(lock) {
            _strokes.clear()
            _analyses.clear()
            _firstFrameTimestampMs = 0L
            _lastFrameTimestampMs = 0L
            _frameCount = 0
            drillType = null
            isCapturing = false
            _acceptedRepCount.value = 0
        }
    }

    /**
     * Snapshot of the collected session. Returns null if no strokes are buffered.
     *
     * `frameIntervalMs` is the mean observed frame interval (falls back to
     * 33 ms for ~30 fps if the session was too short to measure).
     */
    fun snapshot(): Snapshot? = synchronized(lock) {
        if (_strokes.isEmpty()) return@synchronized null
        val intervalMs = computeFrameIntervalMs()
        Snapshot(
            drillType = drillType ?: return@synchronized null,
            strokes = _strokes.toList(),
            analyses = _analyses.toList(),
            frameIntervalMs = intervalMs
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

    companion object {
        const val DEFAULT_TARGET_REPS = 15
        const val MIN_REPS_TO_PERSIST = 10
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
