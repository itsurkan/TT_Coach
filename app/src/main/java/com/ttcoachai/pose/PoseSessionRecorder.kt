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
 * thread via LiveTrainingController.onPoseResult) never blocks on file IO. [onFrame] calls
 * are fire-and-forget but always land on that same single thread in submission order — so, for
 * frames submitted strictly before [finish] starts running, [finish] is guaranteed to see every
 * prior write completed with no extra synchronization needed. A frame that arrives concurrently
 * with [finish]/[abort] tearing down the executor is handled defensively, see [onFrame].
 *
 * Caller contract: [start], [finish], and [abort] belong to a single session lifecycle and must
 * only ever be invoked sequentially from that lifecycle — never concurrently with each other
 * (both do a non-atomic check-then-set on `finished`). Only [onFrame] is safe to call from
 * another thread (the camera/pose thread) while the lifecycle methods run elsewhere.
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
        // This check-then-launch is not atomic with finish()/abort()'s check-then-set on
        // `finished`: this call can pass the guard above just as finish()/abort() flips
        // `finished` and shuts the executor down, so the launch below can race the shutdown.
        // A lock would make onFrame block on the camera/UI thread, which it must never do, so
        // instead we let the race happen and defensively catch the one way it can fail: the
        // dispatcher rejecting work after shutdown. Dropping a frame that arrives mid-teardown
        // is the correct outcome — the session is finalizing anyway.
        try {
            scope.launch {
                val w = writer ?: return@launch
                if (frameCount >= MAX_FRAMES) return@launch
                val isFirst = frameCount == 0
                if (isFirst) firstTimestampMs = timestampMs
                lastTimestampMs = timestampMs
                w.write(PoseJsonV2Writer.frameLine(PoseFrame2D(frameCount, timestampMs, keypoints), isFirst))
                frameCount++
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // Recorder was finalized concurrently between the guard check and this launch; drop
            // the frame.
        }
    }

    /** Finalizes the recording: streams the temp file into a compact-JSON gzip with a real
     *  header (only known now — totalFrames/videoDurationMs are end-of-session facts), deletes
     *  the temp file, and returns the final file. Returns null if [start] was never called or
     *  zero frames were captured. Must not be called concurrently with [abort] — see class
     *  KDoc caller contract. */
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
                if (!tempFile.exists()) {
                    // Temp file was removed externally mid-session (e.g. pose-upload consent
                    // revoked and AppSettingsActivity swept the cache dir). Nothing left to
                    // finalize — this is a legitimate "nothing to upload" outcome, not an IO
                    // failure, so degrade to null rather than letting the GZIP step below throw.
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
     *  instead of [finish] (session discarded). Must not be called concurrently with [finish] —
     *  see class KDoc caller contract. */
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
