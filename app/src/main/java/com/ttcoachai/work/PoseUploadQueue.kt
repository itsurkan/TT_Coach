package com.ttcoachai.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.pose.PoseSessionRecorder
import java.io.File

/**
 * Enqueue-side API for background pose-file uploads (component C of
 * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md). ANY network (not
 * Wi-Fi-only, per spec decision #3) and WorkManager's default exponential backoff (no
 * hand-tuned initial delay needed for v1). Unique work name per session so a retry or app
 * restart can't double-enqueue the same file.
 */
object PoseUploadQueue {

    const val TAG_POSE_UPLOAD = "pose-upload"

    fun enqueue(context: Context, userId: String, sessionId: String, file: File) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data = Data.Builder()
            .putString(PoseUploadWorker.KEY_USER_ID, userId)
            .putString(PoseUploadWorker.KEY_SESSION_ID, sessionId)
            .putString(PoseUploadWorker.KEY_FILE_PATH, file.absolutePath)
            .build()
        val request = OneTimeWorkRequestBuilder<PoseUploadWorker>()
            .setConstraints(constraints)
            .addTag(TAG_POSE_UPLOAD)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("pose-upload-$sessionId", ExistingWorkPolicy.KEEP, request)
    }

    /** Cancels all queued/running pose-upload work — called when the consent toggle is turned
     *  off (component D, Task 5). Returns the [Operation] so callers that then delete cached
     *  files can await actual cancellation first (cancelAllWorkByTag itself only enqueues the
     *  cancellation asynchronously — an in-flight upload can otherwise still be mid-`putFile()`
     *  when the caller deletes its source file out from under it). */
    fun cancelAll(context: Context): androidx.work.Operation {
        return WorkManager.getInstance(context).cancelAllWorkByTag(TAG_POSE_UPLOAD)
    }

    /** Matches [PoseSessionRecorder]'s `provisionalId` format ("pose_&lt;uuid&gt;"). A
     *  finalized `pose_<uuid>.json.gz` still bearing this prefix never reached the
     *  post-finish() rename to `<sessionId>.json.gz` (process death between finish() and the
     *  rename in TrainingActivity) — it has no real sessionId and no Firestore session
     *  document to attach `poseDataPath` to, so it is not recoverable and must not be
     *  uploaded under a fabricated id. See [sweepOrphans].
     */
    private const val PROVISIONAL_ID_PREFIX = "pose_"

    /** Grace period before a still-provisionally-named `pose_<uuid>.json.gz` is treated as
     *  unrecoverable and deleted. TrainingActivity deliberately enqueues under this exact
     *  provisional name when the post-finish() rename to `<sessionId>.json.gz` fails — that
     *  queued-but-not-yet-run upload must survive an app restart that happens to land between
     *  enqueue and the worker actually running. Only a file this old could not possibly still
     *  be one of those in-flight uploads. */
    private const val PROVISIONAL_GRACE_PERIOD_MS = 60L * 60 * 1000

    /** Called on app start: re-enqueues any finalized `<sessionId>.json.gz` left behind in the
     *  cache dir by a process death after the Task 4 rename but before the worker finished
     *  (component C's "App-start sweep"). Three categories of leftover file, handled
     *  differently:
     *  - `.tmp` files are un-gzipped, header-less fragments from a session that died
     *    mid-recording — never uploadable, never enqueued. Left alone here; reaped by
     *    [evictOldCache]'s age cap.
     *  - `pose_<uuid>.json.gz` (still provisionally named) finalized but never renamed to a
     *    real sessionId — no session document to attach `poseDataPath` to. Not recoverable;
     *    deleted immediately rather than uploaded under an invented id.
     *  - `<sessionId>.json.gz` (renamed) is a genuine orphan: re-enqueued if pose upload is
     *    still consented to and a user is signed in, otherwise deleted (consent may have been
     *    revoked, or the user signed out, since the file was written).
     *
     *  Re-enqueuing is safe against duplicate work: [enqueue] uses
     *  `enqueueUniqueWork("pose-upload-$sessionId", ExistingWorkPolicy.KEEP, ...)`, so a
     *  sweep that finds a file whose upload is already queued/running is a no-op for that
     *  file.
     */
    fun sweepOrphans(context: Context) {
        val dir = PoseSessionRecorder.cacheDir(context)
        val files = dir.listFiles() ?: return
        val uploadEnabled = SettingsManager(context).isPoseUploadEnabled()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        val now = System.currentTimeMillis()
        for (file in files) {
            if (!file.name.endsWith(".json.gz")) continue
            val sessionId = file.name.removeSuffix(".json.gz")
            val isRecentProvisional = sessionId.startsWith(PROVISIONAL_ID_PREFIX) &&
                (now - file.lastModified()) < PROVISIONAL_GRACE_PERIOD_MS
            when {
                isRecentProvisional -> Unit // may still be a legitimately queued upload — leave it
                sessionId.startsWith(PROVISIONAL_ID_PREFIX) -> file.delete()
                uploadEnabled && userId != null -> enqueue(context, userId, sessionId, file)
                else -> file.delete()
            }
        }
    }

    /** Called on app start: deletes cached pose files older than [maxAgeDays] or, if the
     *  directory still exceeds [maxBytes] after that, the oldest remaining files until under
     *  budget. A local-disk cap independent of the Storage-side retention gap (see spec
     *  Risks). */
    fun evictOldCache(context: Context, maxAgeDays: Int = 7, maxBytes: Long = 200L * 1024 * 1024) {
        val dir = PoseSessionRecorder.cacheDir(context)
        val files = dir.listFiles()?.toList() ?: return
        val entries = files.map { PoseCacheEviction.Entry(it.absolutePath, it.lastModified(), it.length()) }
        val toDelete = PoseCacheEviction.entriesToEvict(entries, System.currentTimeMillis(), maxAgeDays, maxBytes)
        toDelete.forEach { File(it).delete() }
    }
}
