package com.ttcoachai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ttcoachai.models.DrillConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DrillConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DrillConfigEntity)

    @Query("SELECT * FROM drill_configs WHERE drillType = :drillType LIMIT 1")
    suspend fun getByDrillType(drillType: String): DrillConfigEntity?

    @Query("SELECT * FROM drill_configs WHERE drillType = :drillType LIMIT 1")
    fun observeByDrillType(drillType: String): Flow<DrillConfigEntity?>

    @Query("DELETE FROM drill_configs WHERE drillType = :drillType")
    suspend fun clearForDrill(drillType: String)
}
