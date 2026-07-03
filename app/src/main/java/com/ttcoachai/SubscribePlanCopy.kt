package com.ttcoachai

/** Billing periods offered by the subscription paywall. */
enum class Plan { MONTHLY, QUARTERLY, YEARLY }

/**
 * View state for one billing period. A `null` in [badgeRes], [perMonthPriceRes], or
 * [savingsPercent] means the corresponding view is hidden (GONE) for that period.
 */
data class SubscribePlanUiState(
    val priceRes: Int,
    val periodSuffixRes: Int,
    val badgeRes: Int?,
    val perMonthPriceRes: Int?,
    val savingsPercent: Int?,
    val billedCaptionRes: Int,
)

/** Pure mapping from a [Plan] to its price-hero + CTA copy (the spec's behavior table). */
object SubscribePlanCopy {
    fun stateFor(plan: Plan): SubscribePlanUiState = when (plan) {
        Plan.MONTHLY -> SubscribePlanUiState(
            priceRes = R.string.plan_monthly_price,
            periodSuffixRes = R.string.subscribe_period_month,
            badgeRes = null,
            perMonthPriceRes = null,
            savingsPercent = null,
            billedCaptionRes = R.string.subscribe_billed_monthly,
        )
        Plan.QUARTERLY -> SubscribePlanUiState(
            priceRes = R.string.plan_quarterly_price,
            periodSuffixRes = R.string.subscribe_period_quarter,
            badgeRes = R.string.badge_popular,
            perMonthPriceRes = R.string.subscribe_per_month_quarterly_value,
            savingsPercent = 17,
            billedCaptionRes = R.string.subscribe_billed_quarterly,
        )
        Plan.YEARLY -> SubscribePlanUiState(
            priceRes = R.string.plan_yearly_price,
            periodSuffixRes = R.string.subscribe_period_year,
            badgeRes = R.string.badge_best_value,
            perMonthPriceRes = R.string.subscribe_per_month_yearly_value,
            savingsPercent = 33,
            billedCaptionRes = R.string.subscribe_billed_yearly,
        )
    }
}
