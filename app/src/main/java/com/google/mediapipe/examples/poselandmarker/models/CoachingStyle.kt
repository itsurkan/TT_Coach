/*
 * AI Coach for Table Tennis
 * Coaching Style Model
 */

package com.google.mediapipe.examples.poselandmarker.models

import com.google.mediapipe.examples.poselandmarker.R

enum class CoachingStyle(
    val displayName: String,
    val subtitle: String,
    val description: String,
    val avatarInitial: String,
    val avatarColor: Int
) {
    MOTIVATIONAL_ENERGETIC(
        displayName = "Vadym",
        subtitle = "Motivational & Energetic",
        description = "High-energy coaching with lots of encouragement and positive reinforcement",
        avatarInitial = "V",
        avatarColor = R.color.coach_avatar_green
    ),
    PRECISE_TECHNICAL(
        displayName = "Ivan",
        subtitle = "Precise and sticky to technique",
        description = "Detail-oriented coaching focused on perfect form. Ideal for players who want to master technical precision.",
        avatarInitial = "I",
        avatarColor = R.color.coach_avatar_blue
    ),
    GENTLE_SUPPORTIVE(
        displayName = "Andriy",
        subtitle = "Short and soft",
        description = "Gentle encouragement with concise feedback. Perfect for beginners who prefer a supportive approach.",
        avatarInitial = "A",
        avatarColor = R.color.coach_avatar_purple
    );

    companion object {
        fun fromOrdinal(ordinal: Int): CoachingStyle {
            return values().getOrNull(ordinal) ?: MOTIVATIONAL_ENERGETIC
        }
    }
}
