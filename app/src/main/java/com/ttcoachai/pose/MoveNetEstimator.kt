package com.ttcoachai.pose

// MoveNetEstimator.kt
//
// THROWAWAY PROTOTYPE (FPS A/B bench only — not production, not TDD'd, not covered by freeze
// discipline). Runs Google's MoveNet Thunder single-pose TFLite model on a bitmap and decodes
// its output into the shared schema-v2 Keypoint2D/Coco17 convention, so it's a drop-in
// alternative to RtmposeEstimator behind the same PoseBackend contract.
//
// Model: movenet_thunder.tflite (float16 single-pose, 256x256 input) bundled in assets/.
// Output tensor: [1, 1, 17, 3] = (y, x, score) per keypoint, normalized 0..1 relative to the
// model's own 256x256 letterboxed input square — MoveNet's keypoint order is already COCO-17
// order (nose, eyes, ears, shoulders, elbows, wrists, hips, knees, ankles), so no reindexing is
// needed, only mapping the padded-square coords back to the original frame.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.ttcoachai.shared.models.Keypoint2D
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MoveNetEstimator(
    context: Context,
    assetName: String = DEFAULT_ASSET_NAME
) : AutoCloseable {

    companion object {
        const val DEFAULT_ASSET_NAME = "movenet_thunder.tflite"
        const val KEYPOINT_COUNT = 17
        private const val INPUT_SIZE = 256
    }

    private val interpreter: Interpreter
    private val inputDataType: DataType

    init {
        val model = loadModelFile(context, assetName)
        interpreter = Interpreter(model)
        inputDataType = interpreter.getInputTensor(0).dataType()
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    /**
     * Runs inference on [bitmap] (already rotated to the analysis frame's orientation) and
     * returns 17 keypoints in Coco17 order, normalized against [frameWidth]/[frameHeight].
     */
    fun estimate(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<Keypoint2D> {
        if (frameWidth <= 0 || frameHeight <= 0) return emptyList()

        // Letterbox-resize into a 256x256 square, matching MoveNet's expected padding scheme
        // (longer side scaled to INPUT_SIZE, shorter side padded, top/left aligned).
        val scale = INPUT_SIZE.toFloat() / maxOf(frameWidth, frameHeight)
        val scaledW = (frameWidth * scale).toInt().coerceAtLeast(1)
        val scaledH = (frameHeight * scale).toInt().coerceAtLeast(1)

        val squareBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(squareBitmap)
        val matrix = Matrix().apply { setScale(scale, scale) }
        canvas.drawBitmap(bitmap, matrix, null)

        val inputBuffer = bitmapToInputBuffer(squareBitmap, inputDataType)
        squareBitmap.recycle()

        // Output: [1, 1, 17, 3] floats (y, x, score), always float32 regardless of input dtype.
        val output = Array(1) { Array(1) { Array(KEYPOINT_COUNT) { FloatArray(3) } } }
        interpreter.run(inputBuffer, output)

        val kps = output[0][0]
        val result = ArrayList<Keypoint2D>(KEYPOINT_COUNT)
        for (i in 0 until KEYPOINT_COUNT) {
            val ny = kps[i][0] // normalized within the padded 256x256 square
            val nx = kps[i][1]
            val score = kps[i][2]

            // Map padded-square-normalized coords back to the original (scaledW x scaledH)
            // region within the square, then to the original frame's own normalization.
            val pixelXInSquare = nx * INPUT_SIZE
            val pixelYInSquare = ny * INPUT_SIZE
            result.add(
                Keypoint2D(
                    x = clamp01(pixelXInSquare / scaledW),
                    y = clamp01(pixelYInSquare / scaledH),
                    score = clamp01(score)
                )
            )
        }
        return result
    }

    override fun close() {
        interpreter.close()
    }

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

    private fun bitmapToInputBuffer(bitmap: Bitmap, dataType: DataType): ByteBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val bytesPerChannel = if (dataType == DataType.UINT8) 1 else 4
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * bytesPerChannel)
        buffer.order(ByteOrder.nativeOrder())

        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (dataType == DataType.UINT8) {
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            } else {
                // Float32 MoveNet variants expect raw 0..255 float values (NOT /255 normalized) -
                // per the official TF Hub MoveNet signature, input is float32 pixel values 0-255.
                buffer.putFloat(r.toFloat())
                buffer.putFloat(g.toFloat())
                buffer.putFloat(b.toFloat())
            }
        }
        buffer.rewind()
        return buffer
    }
}
