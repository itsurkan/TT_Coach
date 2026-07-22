package com.ttcoachai.models

/**
 * Firestore-facing model for a drill shared to the public community collection.
 * Pure Kotlin — no Firebase annotations, no Android dependency.
 */
data class CommunityDrill(
    val id: String = "",
    val name: String,
    val baseTemplate: String,
    val focusCsv: String,
    val referenceType: String,
    val strictnessX: Float,
    val perPhaseTargetsJson: String,
    val creatorUid: String,
    val creatorName: String,
    val creatorPhotoUrl: String,
    val sharedAtMs: Long,
    val ratingSum: Long,
    val ratingCount: Long,
) {
    val averageRating: Float get() = if (ratingCount > 0L) ratingSum.toFloat() / ratingCount else 0f

    companion object {
        const val COLLECTION = "community_drills"
    }
}

data class DrillRating(val stars: Int, val ratedAtMs: Long)
