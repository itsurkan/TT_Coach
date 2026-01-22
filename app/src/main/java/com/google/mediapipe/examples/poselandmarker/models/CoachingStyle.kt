/*
 * AI Coach for Table Tennis
 * Coaching Style Model
 */

package com.google.mediapipe.examples.poselandmarker.models

enum class CoachingStyle(
    val displayName: String,
    val subtitle: String,
    val description: String,
    val avatarInitial: String
) {
    MOTIVATIONAL_ENERGETIC(
        displayName = "Vadym",
        subtitle = "Motivational & Energetic",
        description = "High-energy coaching with lots of encouragement and positive reinforcement",
        avatarInitial = "V"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): CoachingStyle {
            return values().getOrNull(ordinal) ?: MOTIVATIONAL_ENERGETIC
        }
    }
}
