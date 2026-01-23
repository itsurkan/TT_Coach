/*
 * AI Coach for Table Tennis
 * Coaching Style Model
 */

package com.google.mediapipe.examples.poselandmarker.models

import com.google.mediapipe.examples.poselandmarker.R

enum class CoachingStyle(
    val displayNameResId: Int,
    val subtitleResId: Int,
    val descriptionResId: Int,
    val avatarInitial: String,
    val avatarColor: Int
) {
    MOTIVATIONAL_ENERGETIC(
        displayNameResId = R.string.coach_ivan_name,
        subtitleResId = R.string.coach_ivan_style,
        descriptionResId = R.string.coach_ivan_desc,
        avatarInitial = "I",
        avatarColor = R.color.coach_avatar_green
    ),
    PRECISE_TECHNICAL(
        displayNameResId = R.string.coach_andriy_name,
        subtitleResId = R.string.coach_andriy_style,
        descriptionResId = R.string.coach_andriy_desc,
        avatarInitial = "A",
        avatarColor = R.color.coach_avatar_blue
    ),
    GENTLE_SUPPORTIVE(
        displayNameResId = R.string.coach_vadym_name,
        subtitleResId = R.string.coach_vadym_style,
        descriptionResId = R.string.coach_vadym_desc,
        avatarInitial = "V",
        avatarColor = R.color.coach_avatar_purple
    );

    companion object {
        fun fromOrdinal(ordinal: Int): CoachingStyle {
            return values().getOrNull(ordinal) ?: MOTIVATIONAL_ENERGETIC
        }
    }
}
