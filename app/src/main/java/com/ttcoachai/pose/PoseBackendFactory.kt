package com.ttcoachai.pose

// PoseBackendFactory.kt
//
// Single resolution point from the persisted PoseBackendVariant (Settings) to a live PoseBackend.
// Calibration and live training both go through THIS factory so baseline keypoint characteristics
// always match the backend that actually produced them at feedback time — never construct
// MediaPipePoseLandmarkerBackend directly from a live drill/calibration site.
//
// RtmposeBackend is unaffected: it stays available to PoseBenchmarkActivity (FPS A/B bench) and
// is not deleted in this task.

import android.content.Context
import com.google.mediapipe.tasks.core.Delegate
import com.ttcoachai.managers.SettingsManager

/**
 * MediaPipe PoseLandmarker model + delegate combination selectable from Settings. Persisted via
 * [SettingsManager.getPoseBackendVariant]/[SettingsManager.setPoseBackendVariant] (stored as the
 * enum name; unknown/missing stored value falls back to [MEDIAPIPE_LITE_GPU]).
 */
enum class PoseBackendVariant(val modelAssetName: String, val delegate: Delegate) {
    MEDIAPIPE_LITE_GPU("pose_landmarker_lite.task", Delegate.GPU),
    MEDIAPIPE_LITE_CPU("pose_landmarker_lite.task", Delegate.CPU),
    MEDIAPIPE_FULL_GPU("pose_landmarker_full.task", Delegate.GPU),
    MEDIAPIPE_FULL_CPU("pose_landmarker_full.task", Delegate.CPU)
}

/** Resolves the live [PoseBackend] from the persisted [PoseBackendVariant]. */
object PoseBackendFactory {
    /**
     * Reads the persisted variant from [SettingsManager] and constructs the matching
     * [MediaPipePoseLandmarkerBackend]. Throws on failure (bad model asset, unsupported GPU
     * delegate, etc.) — same contract as that backend's constructor. Callers should wrap this
     * call in try/catch, same as they previously did around `RtmposeBackend(...)`.
     */
    fun create(context: Context): PoseBackend {
        val variant = SettingsManager(context).getPoseBackendVariant()
        return MediaPipePoseLandmarkerBackend(context, variant.modelAssetName, variant.delegate)
    }
}
