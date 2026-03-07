/*
 * AI Coach for Table Tennis
 * Training Repository - Firestore operations for training sessions
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ttcoachai.db.TrainingDao
import com.ttcoachai.models.TrainingSession
import kotlinx.coroutines.tasks.await

/**
 * Repository for training session operations, using Room as local cache
 * and Firestore for cloud sync.
 */
class TrainingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val trainingDao: TrainingDao? = null
) {
    companion object {
        private const val TAG = "TrainingRepository"
        private const val DEFAULT_LIMIT = 100
    }

    /**
     * Save a training session. Saves to Room immediately and pushes to Firestore.
     */
    suspend fun saveSession(session: TrainingSession): Result<String> {
        val id = if (session.id.isNotEmpty()) session.id else TrainingSession.generateId()
        val sessionWithId = session.copy(id = id)

        // 1. Save to local DB first (offline-first)
        trainingDao?.insertSession(sessionWithId.copy(isSynced = false))

        return try {
            // 2. Try to save to Firestore
            firestore.collection(TrainingSession.COLLECTION)
                .document(id)
                .set(sessionWithId.toMap())
                .await()

            // 3. If successful, mark as synced locally
            trainingDao?.markAsSynced(id)
            
            Log.d(TAG, "Session saved and synced: $id")
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync session, will retry later", e)
            // Still return success of the ID because it's saved locally
            Result.success(id)
        }
    }

    /**
     * Get sessions for a user from local Room database.
     */
    suspend fun getSessions(userId: String, limit: Int = DEFAULT_LIMIT): List<TrainingSession> {
        return trainingDao?.getRecentSessions(userId, limit) ?: emptyList()
    }

    /**
     * Get sessions directly from Firestore (bypassing local cache).
     */
    suspend fun getSessionsFromCloud(userId: String, limit: Int = DEFAULT_LIMIT): List<TrainingSession> {
        return try {
            val snapshot = firestore.collection(TrainingSession.COLLECTION)
                .whereEqualTo(TrainingSession.FIELD_USER_ID, userId)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(TrainingSession::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions from cloud", e)
            emptyList()
        }
    }

    /**
     * Save multiple sessions to local Room.
     */
    suspend fun saveSessionsLocally(sessions: List<TrainingSession>) {
        trainingDao?.insertSessions(sessions)
    }

    /**
     * Push all unsynced local sessions to Firestore.
     */
    suspend fun pushUnsyncedSessions(userId: String) {
        val unsynced = trainingDao?.getUnsyncedSessions()?.filter { it.userId == userId } ?: return
        for (session in unsynced) {
            try {
                firestore.collection(TrainingSession.COLLECTION)
                    .document(session.id)
                    .set(session.toMap())
                    .await()
                trainingDao.markAsSynced(session.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push session ${session.id}", e)
            }
        }
    }

    /**
     * Clear all local data for a user.
     */
    suspend fun clearLocalData(userId: String) {
        trainingDao?.clearAllForUser(userId)
    }

    /**
     * Get sessions for a specific exercise.
     */
    suspend fun getSessionsByExercise(
        userId: String,
        exerciseId: String,
        limit: Int = DEFAULT_LIMIT
    ): List<TrainingSession> {
        // For now, still use local DB but we don't have a specific DAO method for this yet.
        // I should probably add it to TrainingDao.
        return trainingDao?.getRecentSessions(userId, 100)?.filter { it.exerciseId == exerciseId } ?: emptyList()
    }

    /**
     * Get sessions within a time range.
     */
    suspend fun getSessionsInRange(
        userId: String,
        startTime: Long,
        endTime: Long
    ): List<TrainingSession> {
        return try {
            val snapshot = firestore.collection(TrainingSession.COLLECTION)
                .whereEqualTo(TrainingSession.FIELD_USER_ID, userId)
                .whereGreaterThanOrEqualTo(TrainingSession.FIELD_START_TIME, startTime)
                .whereLessThanOrEqualTo(TrainingSession.FIELD_START_TIME, endTime)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(TrainingSession::class.java)
            }.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions in range", e)
            emptyList()
        }
    }

    /**
     * Get session by ID.
     */
    suspend fun getSession(sessionId: String): TrainingSession? {
        return try {
            val doc = firestore.collection(TrainingSession.COLLECTION)
                .document(sessionId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(TrainingSession::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session $sessionId", e)
            null
        }
    }

    /**
     * Delete a session.
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            firestore.collection(TrainingSession.COLLECTION)
                .document(sessionId)
                .delete()
                .await()

            Log.d(TAG, "Session deleted: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session $sessionId", e)
            Result.failure(e)
        }
    }

    /**
     * Get total session count for a user.
     */
    suspend fun getSessionCount(userId: String): Int {
        return try {
            val snapshot = firestore.collection(TrainingSession.COLLECTION)
                .whereEqualTo(TrainingSession.FIELD_USER_ID, userId)
                .get()
                .await()

            snapshot.size()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session count", e)
            0
        }
    }
}
