package com.ttcoachai.pose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.ttcoachai.shared.models.Keypoint2D

/**
 * Bridges CameraX frames to the RTMPose [PoseBackend]: converts an [ImageProxy] to a rotated
 * ARGB [Bitmap], runs pose inference, and hands back keypoints + frame timestamp via [onPose].
 * Single responsibility — frame in, pose out. The caller (Activity) decides what to do with the
 * result (live-feedback vs calibration buffer).
 *
 * The image-conversion + rotation/centering technique is COPIED from the frozen
 * `PoseLandmarkerProcessor` (freeze discipline: this class does not call or edit it).
 *
 * [onPose] is invoked synchronously on the calling thread — i.e. whatever CameraX analyzer
 * executor calls [analyze]. This class does not post to the main thread; the caller is
 * responsible for marshalling UI updates.
 */
class RtmposeFrameProcessor(
    private val backend: PoseBackend,
    private val mirror: Boolean = false,
    private val onPose: (keypoints: List<Keypoint2D>, timestampMs: Long) -> Unit,
) {
    companion object {
        private const val TAG = "RtmposeFrameProcessor"
    }

    private var rotatedBitmap: Bitmap? = null
    private val matrix = Matrix()

    /**
     * Call on the CameraX analyzer executor. Converts + rotates the frame, runs the pose
     * backend, and invokes [onPose]. Always closes [imageProxy] exactly once, on every path
     * (success, convert-fail, inference-fail). Never throws to the caller.
     */
    fun analyze(imageProxy: ImageProxy) {
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L // ns -> ms

        val bitmap = try {
            imageProxy.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap", e)
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedWidth = if (rotationDegrees % 180 != 0) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotationDegrees % 180 != 0) imageProxy.width else imageProxy.height

        if (rotatedBitmap == null || rotatedBitmap!!.width != rotatedWidth || rotatedBitmap!!.height != rotatedHeight) {
            rotatedBitmap?.recycle()
            rotatedBitmap = Bitmap.createBitmap(
                rotatedWidth,
                rotatedHeight,
                Bitmap.Config.ARGB_8888
            )
        }

        val tempBitmap = rotatedBitmap
        if (tempBitmap == null) {
            bitmap.recycle()
            imageProxy.close()
            return
        }

        val centerX = imageProxy.width / 2f
        val centerY = imageProxy.height / 2f

        matrix.reset()
        matrix.postTranslate(-centerX, -centerY)
        matrix.postRotate(rotationDegrees.toFloat())

        if (mirror) {
            matrix.postScale(-1f, 1f)
        }

        matrix.postTranslate(rotatedWidth / 2f, rotatedHeight / 2f)

        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, matrix, null)

        // Recycle the intermediate bitmap from toBitmap()
        bitmap.recycle()
        imageProxy.close()

        val keypoints = try {
            backend.estimatePose(tempBitmap, rotatedWidth, rotatedHeight)
        } catch (e: Exception) {
            Log.e(TAG, "RTMPose inference failed", e)
            return
        }

        onPose(keypoints, timestampMs)
    }

    /** Recycles the reusable rotated bitmap. Call when done with this processor. */
    fun close() {
        rotatedBitmap?.recycle()
        rotatedBitmap = null
    }
}
