package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_drills")
data class CustomDrillEntity(
    @PrimaryKey val drillType: String,
    val name: String,
    val baseTemplate: String,
    val createdAtMs: Long,
    val focusCsv: String = "",
    val referenceType: String = "standard",
    val baselineId: Long? = null,
    val strictnessX: Float = 1.0f,
    val perPhaseTargetsJson: String = "",
    val sharedCommunityId: String? = null
)
