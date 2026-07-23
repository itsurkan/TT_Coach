package com.ttcoachai.work

import java.io.File

/**
 * Pure upload-decision logic for [PoseUploadWorker], extracted so it's testable without
 * Robolectric/WorkManager test infra — see
 * docs/superpowers/plans/2026-07-23-pose-upload-firebase.md Task 3.
 */
class PoseUploadTask(
    private val uploadPoseFile: suspend (userId: String, sessionId: String, file: File) -> Result<String>,
    private val setPoseDataPath: suspend (sessionId: String, path: String) -> Unit,
) {
    sealed class Outcome {
        data class Success(val path: String) : Outcome()
        object Retry : Outcome()
        object MissingFile : Outcome()
    }

    suspend fun run(userId: String, sessionId: String, file: File): Outcome {
        if (!file.exists()) return Outcome.MissingFile
        val result = uploadPoseFile(userId, sessionId, file)
        return result.fold(
            onSuccess = { path ->
                setPoseDataPath(sessionId, path)
                file.delete()
                Outcome.Success(path)
            },
            onFailure = { Outcome.Retry }
        )
    }
}
