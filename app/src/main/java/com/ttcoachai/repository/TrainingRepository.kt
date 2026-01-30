/*
 * AI Coach for Table Tennis
 * Training Repository - Firestore operations for training sessions
 */

package com.ttcoachai.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ttcoachai.models.TrainingSession
import kotlinx.coroutines.tasks.await

/**
 * Repository for training session operations in Firestore.
 */
class TrainingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "TrainingRepository"
        private const val DEFAULT_LIMIT = 50
    }

    /**
     * Save a training session to Firestore.
     */
    suspend fun saveSession(session: TrainingSession): Result<String> {
        return try {
            val id = if (session.id.isNotEmpty()) session.id else TrainingSession.generateId()
            val sessionWithId = session.copy(id = id)

            firestore.collection(TrainingSession.COLLECTION)
                .document(id)
                .set(sessionWithId.toMap())
                .await()

            Log.d(TAG, "Session saved: $id")
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
            Result.failure(e)
        }
    }

    /**
     * Get sessions for a user, ordered by start time descending.
     */
    suspend fun getSessions(userId: String, limit: Int = DEFAULT_LIMIT): List<TrainingSession> {
        return try {
            val snapshot = firestore.collection(TrainingSession.COLLECTION)
                .whereEqualTo(TrainingSession.FIELD_USER_ID, userId)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(TrainingSession::class.java)
            }.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions for $userId", e)
            emptyList()
        }
    }

    /**
     * Get sessions for a specific exercise.
     */
    suspend fun getSessionsByExercise(
        userId: String,
        exerciseId: String,
        limit: Int = DEFAULT_LIMIT
    ): List<TrainingSession> {
        return try {
            val snapshot = firestore.collection(TrainingSession.COLLECTION)
                .whereEqualTo(TrainingSession.FIELD_USER_ID, userId)
                .whereEqualTo(TrainingSession.FIELD_EXERCISE_ID, exerciseId)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(TrainingSession::class.java)
            }.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions for exercise $exerciseId", e)
            emptyList()
        }
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
