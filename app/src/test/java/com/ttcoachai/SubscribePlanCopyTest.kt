package com.ttcoachai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscribePlanCopyTest {

    @Test
    fun monthly_hasNoBadgePerMonthOrSavings() {
        val s = SubscribePlanCopy.stateFor(Plan.MONTHLY)
        assertNull("Monthly shows no badge", s.badgeRes)
        assertNull("Monthly shows no per-month line", s.perMonthPriceRes)
        assertNull("Monthly shows no savings pill", s.savingsPercent)
        assertEquals(R.string.plan_monthly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_month, s.periodSuffixRes)
        assertEquals(R.string.subscribe_billed_monthly, s.billedCaptionRes)
    }

    @Test
    fun quarterly_isPopularAndSaves17() {
        val s = SubscribePlanCopy.stateFor(Plan.QUARTERLY)
        assertEquals(R.string.badge_popular, s.badgeRes)
        assertEquals(Integer.valueOf(17), s.savingsPercent)
        assertEquals(R.string.plan_quarterly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_quarter, s.periodSuffixRes)
        assertEquals(R.string.subscribe_per_month_quarterly_value, s.perMonthPriceRes)
        assertEquals(R.string.subscribe_billed_quarterly, s.billedCaptionRes)
    }

    @Test
    fun yearly_isBestValueAndSaves33() {
        val s = SubscribePlanCopy.stateFor(Plan.YEARLY)
        assertEquals(R.string.badge_best_value, s.badgeRes)
        assertEquals(Integer.valueOf(33), s.savingsPercent)
        assertEquals(R.string.plan_yearly_price, s.priceRes)
        assertEquals(R.string.subscribe_period_year, s.periodSuffixRes)
        assertEquals(R.string.subscribe_per_month_yearly_value, s.perMonthPriceRes)
        assertEquals(R.string.subscribe_billed_yearly, s.billedCaptionRes)
    }
}
