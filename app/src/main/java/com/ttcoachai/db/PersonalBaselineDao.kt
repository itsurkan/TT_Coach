package com.ttcoachai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ttcoachai.models.PersonalBaselineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalBaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonalBaselineEntity): Long

    @Query("SELECT * FROM personal_baselines WHERE drillType = :drillType AND isActive = 1 LIMIT 1")
    fun getActiveByDrillType(drillType: String): Flow<PersonalBaselineEntity?>

    @Query("SELECT * FROM personal_baselines WHERE drillType = :drillType ORDER BY createdAtMs DESC")
    suspend fun getAllForDrillType(drillType: String): List<PersonalBaselineEntity>

    @Query("UPDATE personal_baselines SET isActive = 0 WHERE drillType = :drillType AND isActive = 1")
    suspend fun archiveActive(drillType: String)

    /**
     * Atomic version bump: archive any currently active baseline for this drill type
     * and insert the new one as active in a single transaction. Used by recalibration.
     */
    @Transaction
    suspend fun archiveAndInsert(drillType: String, newEntity: PersonalBaselineEntity): Long {
        archiveActive(drillType)
        return insert(newEntity)
    }
}
