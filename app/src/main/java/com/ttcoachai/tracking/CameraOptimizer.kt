package com.ttcoachai.tracking

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import com.ttcoachai.shared.models.CameraConfiguration

/**
 * Controls CameraX exposure settings for ball tracking mode.
 *
 * Uses Camera2 interop APIs to apply manual exposure (no AE) with brightness-based
 * adaptation. Falls back to AE + exposure compensation if manual mode is unsupported.
 *
 * Rate-limited to max 1 adjustment per 2 seconds.
 */
class CameraOptimizer(
    private val camera2Control: Camera2CameraControl,
    private val cameraCharacteristics: CameraCharacteristics,
    initialConfig: CameraConfiguration = CameraConfiguration()
) {

    companion object {
        private const val TAG = "CameraOptimizer"

        // Default manual exposure settings
        private const val DEFAULT_EXPOSURE_NS = 2_000_000L   // 2 ms
        private const val MAX_EXPOSURE_NS = 8_000_000L       // 8 ms hard ceiling
        private const val MIN_EXPOSURE_NS = 500_000L         // 0.5 ms
        private const val DEFAULT_ISO = 800
        private const val MAX_ISO = 3200
        private const val MIN_ISO = 100
        private const val DEFAULT_FPS = 30

        // Brightness adaptation thresholds (0-255 luminance scale)
        private const val BRIGHT_THRESHOLD = 160f    // Reduce ISO / exposure
        private const val DIM_THRESHOLD = 80f        // Increase ISO first, then exposure
        private const val EMA_ALPHA = 0.1f           // Smoothing factor

        // Rate limiting: max 1 adjustment per 2 seconds
        private const val MIN_ADJUSTMENT_INTERVAL_MS = 2_000L
    }

    private var _currentConfig = initialConfig
    val currentConfig: CameraConfiguration get() = _currentConfig

    private var supportsManualExposure: Boolean = false
    private var lastAdjustmentTimeMs: Long = 0L

    /**
     * Apply optimized camera settings for ball tracking: manual exposure 2ms, ISO 800, 30 FPS.
     * Falls back to AE + target FPS range if manual mode is unsupported on the device.
     */
    fun applyBallTrackingMode() {
        supportsManualExposure = checkManualExposureSupport()

        if (supportsManualExposure) {
            applyManualSettings(
                exposureNs = DEFAULT_EXPOSURE_NS,
                iso = DEFAULT_ISO,
                fps = DEFAULT_FPS
            )
        } else {
            Log.w(TAG, "Device does not support manual AE. Falling back to AE with FPS lock.")
            applyAutoExposureFallback(fps = DEFAULT_FPS)
        }
    }

    /**
     * Restore default auto-exposure camera settings.
     */
    fun restoreDefaultMode() {
        try {
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
            camera2Control.setCaptureRequestOptions(builder.build())
            _currentConfig = CameraConfiguration(isAutoExposure = true)
            Log.d(TAG, "Restored default auto-exposure mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore default mode", e)
        }
    }

    /**
     * Adapt exposure based on current frame brightness.
     *
     * Brightness adaptation strategy (R12):
     * - Too bright → lower ISO first, then lower exposure
     * - Too dim    → raise ISO first (up to 3200), then raise exposure (up to 8ms)
     *
     * Rate-limited to max 1 adjustment per 2 seconds.
     *
     * @param averageLuminance Average brightness of frame center (0-255)
     */
    fun onBrightnessUpdate(averageLuminance: Float) {
        val now = System.currentTimeMillis()
        if (now - lastAdjustmentTimeMs < MIN_ADJUSTMENT_INTERVAL_MS) return

        // Update EMA
        val newEma = EMA_ALPHA * averageLuminance + (1f - EMA_ALPHA) * _currentConfig.luminanceEma

        if (newEma >= BRIGHT_THRESHOLD && supportsManualExposure) {
            // Too bright — reduce ISO, then exposure
            val newIso = (_currentConfig.isoSensitivity / 2).coerceAtLeast(MIN_ISO)
            val newExposure = if (newIso <= MIN_ISO) {
                (_currentConfig.exposureTimeNs / 2).coerceAtLeast(MIN_EXPOSURE_NS)
            } else {
                _currentConfig.exposureTimeNs
            }
            if (newIso != _currentConfig.isoSensitivity || newExposure != _currentConfig.exposureTimeNs) {
                applyManualSettings(newExposure, newIso, _currentConfig.targetFps)
                lastAdjustmentTimeMs = now
                Log.d(TAG, "Brightness high ($newEma). ISO→$newIso, Exposure→${newExposure / 1_000}µs")
            }
        } else if (newEma <= DIM_THRESHOLD && supportsManualExposure) {
            // Too dim — raise ISO first, then exposure
            val newIso = (_currentConfig.isoSensitivity * 2).coerceAtMost(MAX_ISO)
            val newExposure = if (newIso >= MAX_ISO) {
                (_currentConfig.exposureTimeNs * 2).coerceAtMost(MAX_EXPOSURE_NS)
            } else {
                _currentConfig.exposureTimeNs
            }
            if (newIso != _currentConfig.isoSensitivity || newExposure != _currentConfig.exposureTimeNs) {
                applyManualSettings(newExposure, newIso, _currentConfig.targetFps)
                lastAdjustmentTimeMs = now
                Log.d(TAG, "Brightness low ($newEma). ISO→$newIso, Exposure→${newExposure / 1_000}µs")
            }
        }

        // Always update the EMA in config even without a hardware change
        _currentConfig = _currentConfig.copy(luminanceEma = newEma)
    }

    // --- Private helpers ---

    private fun checkManualExposureSupport(): Boolean {
        val supportedHardwareLevel = cameraCharacteristics
            .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        return supportedHardwareLevel != null &&
                supportedHardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
    }

    private fun applyManualSettings(exposureNs: Long, iso: Int, fps: Int) {
        try {
            val fpsRange = Range(fps, fps)
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

            camera2Control.setCaptureRequestOptions(builder.build())

            _currentConfig = CameraConfiguration(
                exposureTimeNs = exposureNs,
                isoSensitivity = iso,
                targetFps = fps,
                isAutoExposure = false,
                luminanceEma = _currentConfig.luminanceEma
            )
            Log.d(TAG, "Applied manual: exposure=${exposureNs / 1_000}µs, ISO=$iso, FPS=$fps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply manual camera settings", e)
        }
    }

    private fun applyAutoExposureFallback(fps: Int) {
        try {
            val fpsRange = Range(fps, fps)
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

            camera2Control.setCaptureRequestOptions(builder.build())

            _currentConfig = CameraConfiguration(
                targetFps = fps,
                isAutoExposure = true,
                luminanceEma = _currentConfig.luminanceEma
            )
            Log.d(TAG, "Applied AE fallback: FPS=$fps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply AE fallback settings", e)
        }
    }
}
