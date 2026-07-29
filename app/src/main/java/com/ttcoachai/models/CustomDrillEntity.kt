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
    val sharedCommunityId: String? = null,
    /** "general" for the widened-locomotion-tolerance profile (see
     *  ForehandDriveGeneral.MOVEMENT_TOLERANT_HIP_TRAVEL), null for the default structured
     *  tolerance. Editor does not expose this in v1 — seed-only (Task F). Deliberately excluded
     *  from CommunityDrillMapper (never syncs to Firestore), same precedent as drillType/baselineId. */
    val movementProfile: String? = null
)
