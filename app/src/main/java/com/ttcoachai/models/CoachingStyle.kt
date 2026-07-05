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
    val avatarColor: Int,
    val styleId: String
) {
    PLAYFUL(
        displayNameResId = R.string.coach_playful_name,
        subtitleResId = R.string.coach_playful_style,
        descriptionResId = R.string.coach_playful_desc,
        avatarInitial = "P",
        avatarColor = R.color.ttc_gold_bright,
        styleId = "preset-playful"
    ),
    STRICT(
        displayNameResId = R.string.coach_strict_name,
        subtitleResId = R.string.coach_strict_style,
        descriptionResId = R.string.coach_strict_desc,
        avatarInitial = "S",
        avatarColor = R.color.ttc_gold_bright,
        styleId = "preset-strict"
    ),
    EFFICIENT(
        displayNameResId = R.string.coach_efficient_name,
        subtitleResId = R.string.coach_efficient_style,
        descriptionResId = R.string.coach_efficient_desc,
        avatarInitial = "E",
        avatarColor = R.color.ttc_gold_bright,
        styleId = "preset-efficient"
    );

    companion object {
        fun fromOrdinal(ordinal: Int): CoachingStyle {
            return values().getOrNull(ordinal) ?: PLAYFUL
        }
    }
}
