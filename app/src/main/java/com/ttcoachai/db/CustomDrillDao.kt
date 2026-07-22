package com.ttcoachai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttcoachai.models.CustomDrillEntity

@Dao
interface CustomDrillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomDrillEntity)

    @Query("SELECT * FROM custom_drills ORDER BY createdAtMs DESC")
    suspend fun getAll(): List<CustomDrillEntity>

    @Query("SELECT * FROM custom_drills WHERE drillType = :drillType LIMIT 1")
    suspend fun getByDrillType(drillType: String): CustomDrillEntity?

    @Query("SELECT COUNT(*) FROM custom_drills")
    suspend fun count(): Int

    @Query("DELETE FROM custom_drills WHERE drillType = :drillType")
    suspend fun deleteByDrillType(drillType: String)

    @Query("SELECT * FROM custom_drills WHERE sharedCommunityId = :communityId LIMIT 1")
    suspend fun getBySharedCommunityId(communityId: String): CustomDrillEntity?
}
