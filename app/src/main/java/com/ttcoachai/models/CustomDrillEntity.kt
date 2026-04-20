package com.ttcoachai.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_drills")
data class CustomDrillEntity(
    @PrimaryKey val drillType: String,
    val name: String,
    val baseTemplate: String,
    val createdAtMs: Long
)
