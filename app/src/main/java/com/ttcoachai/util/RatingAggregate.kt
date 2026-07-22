package com.ttcoachai.util

/**
 * Pure rating-aggregate math: first-rate increments count; re-rate adjusts sum only.
 * No Android/Firebase dependency — safe to unit test on the JVM.
 */
object RatingAggregate {

    data class Aggregate(val sum: Long, val count: Long)

    fun applyRating(
        currentSum: Long,
        currentCount: Long,
        previousStars: Int?,
        newStars: Int,
    ): Aggregate {
        require(newStars in 1..5) { "newStars must be in 1..5, was $newStars" }
        if (previousStars != null) {
            require(previousStars in 1..5) { "previousStars must be in 1..5, was $previousStars" }
            return Aggregate(sum = currentSum - previousStars + newStars, count = currentCount)
        }
        return Aggregate(sum = currentSum + newStars, count = currentCount + 1)
    }
}
