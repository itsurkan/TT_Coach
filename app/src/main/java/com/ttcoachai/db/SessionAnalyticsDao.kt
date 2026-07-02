package com.ttcoachai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttcoachai.models.SessionAnalyticsEntity

@Dao
interface SessionAnalyticsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionAnalyticsEntity)

    @Query("SELECT * FROM session_analytics WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: String): SessionAnalyticsEntity?
}
