package com.ttcoachai.work

import android.util.Log
import com.google.firebase.storage.StorageException
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
        object PermanentFailure : Outcome()
        object MissingFile : Outcome()
    }

    suspend fun run(userId: String, sessionId: String, file: File): Outcome {
        if (!file.exists()) return Outcome.MissingFile
        val result = uploadPoseFile(userId, sessionId, file)
        return result.fold(
            onSuccess = { path ->
                // The blob is already at `path` in Storage by the time we get here. If the
                // Firestore write below throws, we must NOT delete the local file or report
                // Success: returning Retry re-runs `run` on the next WorkManager attempt, which
                // re-uploads to the SAME Storage path (idempotent overwrite) and retries the
                // Firestore write. Without this, an uncaught exception here would propagate out
                // of doWork() as a TERMINAL failure — orphaning the blob, never setting
                // poseDataPath, and never deleting the local file.
                try {
                    setPoseDataPath(sessionId, path)
                } catch (e: Exception) {
                    Log.w(TAG, "setPoseDataPath failed for session $sessionId, will retry: ${e.message}")
                    return Outcome.Retry
                }
                file.delete()
                Outcome.Success(path)
            },
            onFailure = { e ->
                when (classifyFailure(e)) {
                    Outcome.PermanentFailure -> {
                        val errorCode = (e as? StorageException)?.errorCode
                        Log.w(TAG, "Permanent upload failure for session $sessionId, errorCode=$errorCode: ${e.message}")
                        // Do NOT delete the local file here. storage.rules is a manual-deploy
                        // step (see project CLAUDE.md) — until the user deploys it, a real
                        // session against the currently-live default rules denies every write
                        // with ERROR_NOT_AUTHORIZED, which classifies as PermanentFailure. That
                        // is an expected-today outcome, not proof the data is unrecoverable —
                        // deleting here would silently destroy the player's pose capture on
                        // every session for as long as rules are undeployed. Leave the file for
                        // evictOldCache's age/size reaper instead.
                        Outcome.PermanentFailure
                    }
                    else -> Outcome.Retry
                }
            }
        )
    }

    companion object {
        private const val TAG = "PoseUploadTask"

        /**
         * Thin extraction of the StorageException-specific bits; the actual classification
         * decision lives in [classifyStorageFailure] so it can be driven directly by tests with
         * representative (errorCode, httpResultCode) pairs.
         */
        internal fun classifyFailure(e: Throwable): Outcome {
            val storageException = e as? StorageException ?: return Outcome.Retry
            return classifyStorageFailure(storageException.errorCode, storageException.httpResultCode)
        }

        internal fun classifyStorageFailure(errorCode: Int, httpResultCode: Int): Outcome {
            val permanentErrorCodes = setOf(
                StorageException.ERROR_NOT_AUTHENTICATED,
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_QUOTA_EXCEEDED,
                StorageException.ERROR_INVALID_CHECKSUM,
            )
            if (errorCode in permanentErrorCodes) return Outcome.PermanentFailure
            // Retry-limit-exceeded and 5xx are transient by definition; anything unrecognised
            // stays Retry too (safer default). Only a definite 4xx client-error code is treated
            // as permanent here.
            if (errorCode != StorageException.ERROR_RETRY_LIMIT_EXCEEDED &&
                httpResultCode in 400..499
            ) {
                return Outcome.PermanentFailure
            }
            return Outcome.Retry
        }
    }
}
