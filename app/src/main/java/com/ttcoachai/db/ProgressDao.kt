package com.ttcoachai.db

import androidx.room.*
import com.ttcoachai.models.UserProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    fun getProgressFlow(userId: String): Flow<UserProgress?>

    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    suspend fun getProgress(userId: String): UserProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: UserProgress)

    @Query("SELECT * FROM user_progress WHERE isSynced = 0")
    suspend fun getUnsyncedProgress(): List<UserProgress>

    @Query("UPDATE user_progress SET isSynced = 1 WHERE userId = :userId")
    suspend fun markAsSynced(userId: String)

    @Query("DELETE FROM user_progress WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
