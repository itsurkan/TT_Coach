package com.ttcoachai.db

import androidx.room.*
import com.ttcoachai.models.TrainingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Query("SELECT * FROM training_sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getSessionsForUser(userId: String): Flow<List<TrainingSession>>

    @Query("SELECT * FROM training_sessions WHERE userId = :userId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(userId: String, limit: Int): List<TrainingSession>

    @Query("SELECT * FROM training_sessions WHERE userId = :userId ORDER BY startTime DESC LIMIT 1")
    suspend fun getMostRecentSessionForUser(userId: String): TrainingSession?

    @Query("SELECT * FROM training_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): TrainingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrainingSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<TrainingSession>)

    @Query("DELETE FROM training_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM training_sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<TrainingSession>

    @Query("UPDATE training_sessions SET isSynced = 1 WHERE id = :sessionId")
    suspend fun markAsSynced(sessionId: String)
    
    @Query("DELETE FROM training_sessions WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
