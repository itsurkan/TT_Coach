package com.ttcoachai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RatingAggregateTest {

    @Test fun first_rating_increments_count_and_adds_to_sum() {
        val result = RatingAggregate.applyRating(
            currentSum = 0L,
            currentCount = 0L,
            previousStars = null,
            newStars = 4,
        )
        assertEquals(RatingAggregate.Aggregate(sum = 4L, count = 1L), result)
    }

    @Test fun re_rate_adjusts_sum_only_count_unchanged() {
        val result = RatingAggregate.applyRating(
            currentSum = 4L,
            currentCount = 1L,
            previousStars = 4,
            newStars = 2,
        )
        assertEquals(RatingAggregate.Aggregate(sum = 2L, count = 1L), result)
    }

    @Test fun re_rate_up_adjusts_sum_only() {
        val result = RatingAggregate.applyRating(
            currentSum = 10L,
            currentCount = 3L,
            previousStars = 2,
            newStars = 5,
        )
        assertEquals(RatingAggregate.Aggregate(sum = 13L, count = 3L), result)
    }

    @Test fun newStars_zero_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RatingAggregate.applyRating(
                currentSum = 0L,
                currentCount = 0L,
                previousStars = null,
                newStars = 0,
            )
        }
    }

    @Test fun newStars_six_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RatingAggregate.applyRating(
                currentSum = 0L,
                currentCount = 0L,
                previousStars = null,
                newStars = 6,
            )
        }
    }

    @Test fun previousStars_zero_invalid_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RatingAggregate.applyRating(
                currentSum = 4L,
                currentCount = 1L,
                previousStars = 0,
                newStars = 3,
            )
        }
    }
}
