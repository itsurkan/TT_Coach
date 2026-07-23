package com.ttcoachai.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.ttcoachai.models.TrainingSession
import com.ttcoachai.repository.PoseDataRepository
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Background upload of one session's pose gzip file. See
 * docs/superpowers/specs/2026-07-23-pose-upload-firebase-design.md component C. Decision logic
 * lives in [PoseUploadTask] (tested directly, without WorkManager infra) — this class is only
 * the WorkManager glue: read [inputData], build the real collaborators, delegate, map the
 * [PoseUploadTask.Outcome] onto a WorkManager [Result].
 */
class PoseUploadWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: PoseDataRepository = PoseDataRepository(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_FILE_PATH = "filePath"
    }

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()

        val task = PoseUploadTask(
            uploadPoseFile = { uid, sid, f -> repository.uploadPoseFile(uid, sid, f) },
            setPoseDataPath = { sid, path ->
                firestore.collection(TrainingSession.COLLECTION)
                    .document(sid)
                    .update("poseDataPath", path)
                    .await()
            }
        )

        return when (task.run(userId, sessionId, File(filePath))) {
            is PoseUploadTask.Outcome.Success -> Result.success()
            PoseUploadTask.Outcome.Retry -> Result.retry()
            PoseUploadTask.Outcome.MissingFile -> Result.failure()
        }
    }
}
