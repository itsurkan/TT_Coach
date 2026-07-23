package com.ttcoachai.pose

import android.content.Context
import com.ttcoachai.shared.io.PoseJsonV2Writer
import com.ttcoachai.shared.models.Keypoint2D
import com.ttcoachai.shared.models.PoseFrame2D
import com.ttcoachai.shared.models.Topology
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

/**
 * Records every live RTM pose frame of one training session to a schema-v2 compact-JSON gzip
 * file, off the caller's thread. See
 * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md, component B. Takes a plain
 * output [File] directory (not a Context) so tests can point it at a temp dir; real call sites
 * use [cacheDir].
 *
 * Writes go through a dedicated single-thread dispatcher so [onFrame] (called from the UI
 * thread via RtmposeTrainingController.onPoseResult) never blocks on file IO. [onFrame] calls
 * are fire-and-forget but always land on that same single thread in submission order — so
 * [finish], which is `suspend` and dispatches onto the same thread, is guaranteed to run after
 * every prior [onFrame] write has completed, with no extra synchronization needed.
 */
class PoseSessionRecorder(private val outputDir: File) {

    companion object {
        private const val MODEL_NAME = "rtmpose-m"
        private const val CACHE_DIR_NAME = "pose_uploads"

        /** Hard cap: recording stops accepting frames past this many, but whatever was
         *  captured so far still finalizes normally. Backstop against unmeasured on-device
         *  fps (see spec Risks), not a target. */
        const val MAX_FRAMES = 60_000

        /** Real on-device output directory. Tests use their own temp [File] via the
         *  constructor instead. */
        fun cacheDir(context: Context): File = File(context.filesDir, CACHE_DIR_NAME)
    }

    private val provisionalId = "pose_${UUID.randomUUID()}"
    private val tempFile = File(outputDir, "$provisionalId.tmp")

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var writer: BufferedWriter? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var frameCount = 0
    private var firstTimestampMs = 0L
    private var lastTimestampMs = 0L
    @Volatile private var started = false
    @Volatile private var finished = false

    fun start(videoWidth: Int, videoHeight: Int) {
        if (finished) return
        this.videoWidth = videoWidth
        this.videoHeight = videoHeight
        started = true
        scope.launch {
            outputDir.mkdirs()
            writer = tempFile.bufferedWriter()
        }
    }

    fun onFrame(keypoints: List<Keypoint2D>, timestampMs: Long) {
        if (!started || finished) return
        scope.launch {
            val w = writer ?: return@launch
            if (frameCount >= MAX_FRAMES) return@launch
            val isFirst = frameCount == 0
            if (isFirst) firstTimestampMs = timestampMs
            lastTimestampMs = timestampMs
            w.write(PoseJsonV2Writer.frameLine(PoseFrame2D(frameCount, timestampMs, keypoints), isFirst))
            frameCount++
        }
    }

    /** Finalizes the recording: streams the temp file into a compact-JSON gzip with a real
     *  header (only known now — totalFrames/videoDurationMs are end-of-session facts), deletes
     *  the temp file, and returns the final file. Returns null if [start] was never called or
     *  zero frames were captured. */
    suspend fun finish(): File? {
        if (!started || finished) return null
        finished = true
        try {
            return withContext(dispatcher) {
                writer?.flush()
                writer?.close()
                writer = null
                if (frameCount == 0) {
                    tempFile.delete()
                    return@withContext null
                }
                val totalFrames = frameCount
                val durationMs = (lastTimestampMs - firstTimestampMs).coerceAtLeast(0L)
                val intervalMs = if (totalFrames > 1) (durationMs / (totalFrames - 1)).coerceAtLeast(1L) else 1L
                val finalFile = File(outputDir, "$provisionalId.json.gz")
                try {
                    GZIPOutputStream(finalFile.outputStream()).use { gz ->
                        gz.write(
                            PoseJsonV2Writer.header(
                                topology = Topology.COCO17,
                                model = MODEL_NAME,
                                videoName = "",
                                intervalMs = intervalMs,
                                totalFrames = totalFrames,
                                videoDurationMs = durationMs,
                                videoWidth = videoWidth,
                                videoHeight = videoHeight
                            ).toByteArray(Charsets.UTF_8)
                        )
                        tempFile.inputStream().use { it.copyTo(gz) }
                        gz.write(PoseJsonV2Writer.footer().toByteArray(Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    finalFile.delete()
                    throw e
                } finally {
                    tempFile.delete()
                }
                finalFile
            }
        } finally {
            executor.shutdown()
        }
    }

    /** Cancels any pending writes and deletes the temp file. Safe to call before [start] or
     *  instead of [finish] (session discarded). */
    fun abort() {
        if (finished) return
        finished = true
        try {
            runBlocking(dispatcher) {
                writer?.close()
                writer = null
            }
            tempFile.delete()
        } finally {
            executor.shutdown()
        }
    }
}
