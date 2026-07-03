package com.ttcoachai.pose

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * Small reusable helper that builds configured ONNX Runtime sessions. 1:1 Kotlin port of
 * `iosApp/TTCoach/Pose/ORTSessionFactory.swift`.
 *
 * We share a single [OrtEnvironment] across sessions. Unlike iOS (which appends a CoreML EP and
 * falls back to CPU on failure), Android skips the accelerated-EP story here: YOLOX MUST run
 * CPU-only regardless (baked-in NMS breaks accelerated EPs — same reason iOS forces CPU for it),
 * and callers that want NNAPI for RTMPose wire it explicitly via [SessionConfig] rather than this
 * factory guessing at fallback behavior.
 */
object OrtSessionFactory {

    /** One process-wide ORT environment. */
    private val sharedEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    /** Errors surfaced when building an ORT session. */
    sealed class OrtSessionFactoryError(message: String) : Exception(message) {
        class ModelFileMissing(path: String) :
            OrtSessionFactoryError("ONNX model file not found at: $path")

        class SessionCreationFailed(model: String, underlying: Throwable) :
            OrtSessionFactoryError("Failed to create OrtSession for $model: ${underlying.message}")
    }

    /**
     * Creates a session for a model at an absolute file path. Pass `cpuOnly = true` for a
     * CPU-only session (required for the YOLOX detector).
     */
    @Throws(OrtSessionFactoryError::class)
    fun makeSession(modelPath: String, modelLabel: String? = null, cpuOnly: Boolean = true): OrtSession {
        val label = modelLabel ?: File(modelPath).name
        val file = File(modelPath)
        if (!file.exists()) {
            throw OrtSessionFactoryError.ModelFileMissing(modelPath)
        }
        val options = makeSessionOptions(modelLabel = label, cpuOnly = cpuOnly)
        return try {
            sharedEnv.createSession(modelPath, options)
        } catch (e: OrtException) {
            throw OrtSessionFactoryError.SessionCreationFailed(label, e)
        }
    }

    /**
     * Creates a session from bytes read out of the app's `assets/` directory (used by the
     * on-device pose backend). Pass `cpuOnly = true` for a CPU-only session (required for the
     * YOLOX detector).
     */
    @Throws(OrtSessionFactoryError::class)
    fun makeSession(
        assetManager: AssetManager,
        assetName: String,
        modelLabel: String? = null,
        cpuOnly: Boolean = true
    ): OrtSession {
        val label = modelLabel ?: assetName
        val bytes = try {
            assetManager.open(assetName).use { it.readBytes() }
        } catch (e: IOException) {
            throw OrtSessionFactoryError.ModelFileMissing("$assetName (in assets)")
        }
        val options = makeSessionOptions(modelLabel = label, cpuOnly = cpuOnly)
        return try {
            sharedEnv.createSession(bytes, options)
        } catch (e: OrtException) {
            throw OrtSessionFactoryError.SessionCreationFailed(label, e)
        }
    }

    /**
     * Builds session options. `cpuOnly = true` leaves the default CPU execution provider only
     * (no accelerated EP appended) — required for YOLOX, whose baked-in NMS has a dynamic-shape
     * node accelerated EPs reject (hard-fails when there are zero detections, common on real
     * footage). `cpuOnly = false` is a placeholder for a future accelerated-EP path (e.g. NNAPI
     * for RTMPose) and currently behaves identically to CPU-only until that is wired.
     */
    private fun makeSessionOptions(modelLabel: String, cpuOnly: Boolean): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        if (cpuOnly) {
            android.util.Log.i(TAG, "$modelLabel: CPU-only.")
        } else {
            android.util.Log.i(TAG, "$modelLabel: no accelerated EP wired yet; running CPU-only.")
        }
        return options
    }

    private const val TAG = "OrtSessionFactory"
}
