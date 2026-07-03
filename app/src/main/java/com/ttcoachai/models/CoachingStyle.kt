/*
 * AI Coach for Table Tennis
 * Coaching Style Model
 */

package com.ttcoachai.models

import com.ttcoachai.R

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
        avatarColor = R.color.ttc_gold_bright
    ),
    PRECISE_TECHNICAL(
        displayNameResId = R.string.coach_andriy_name,
        subtitleResId = R.string.coach_andriy_style,
        descriptionResId = R.string.coach_andriy_desc,
        avatarInitial = "A",
        avatarColor = R.color.ttc_gold_bright
    ),
    GENTLE_SUPPORTIVE(
        displayNameResId = R.string.coach_vadym_name,
        subtitleResId = R.string.coach_vadym_style,
        descriptionResId = R.string.coach_vadym_desc,
        avatarInitial = "V",
        avatarColor = R.color.ttc_gold_bright
    );

    companion object {
        fun fromOrdinal(ordinal: Int): CoachingStyle {
            return values().getOrNull(ordinal) ?: MOTIVATIONAL_ENERGETIC
        }
    }
}
