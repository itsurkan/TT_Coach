package com.ttcoachai.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
     *  off (component D, Task 5). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_POSE_UPLOAD)
    }
}
