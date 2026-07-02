package com.ttcoachai

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.ttcoachai.databinding.ActivitySubscribeBinding
import com.ttcoachai.managers.SettingsManager

class SubscribeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscribeBinding
    private lateinit var settingsManager: SettingsManager

    private var selectedPlan = Plan.YEARLY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupToggle()
        setupButtons()

        selectedPlan = Plan.YEARLY
        applyPlanSelection()
    }

    private fun setupToggle() {
        binding.btnMonthly.setOnClickListener {
            selectedPlan = Plan.MONTHLY
            applyPlanSelection()
        }
        binding.btnQuarterly.setOnClickListener {
            selectedPlan = Plan.QUARTERLY
            applyPlanSelection()
        }
        binding.btnYearly.setOnClickListener {
            selectedPlan = Plan.YEARLY
            applyPlanSelection()
        }
    }

    private fun applyPlanSelection() {
        val selected = when (selectedPlan) {
            Plan.MONTHLY -> binding.btnMonthly
            Plan.QUARTERLY -> binding.btnQuarterly
            Plan.YEARLY -> binding.btnYearly
        }
        listOf(binding.btnMonthly, binding.btnQuarterly, binding.btnYearly)
            .forEach { styleSegment(it, it === selected) }
        renderPlan()
    }

    /**
     * Paywall selected segment = solid bright-gold pill with on-gold bold text (spec 12b/13a),
     * distinct from Settings' muted gold-container style. Inactive = transparent with muted
     * secondary text. Each button keeps its own 999dp corners (plain LinearLayout track, not
     * MaterialButtonToggleGroup) so the selected pill is fully rounded in every position.
     */
    private fun styleSegment(btn: MaterialButton, active: Boolean) {
        val bgColor = if (active) R.color.ttc_gold_bright else android.R.color.transparent
        val textColor = if (active) R.color.ttc_on_gold else R.color.ttc_text_2
        val font = if (active) R.font.inter_tight_bold else R.font.inter_tight_semibold
        btn.backgroundTintList = ContextCompat.getColorStateList(this, bgColor)
        btn.setTextColor(ContextCompat.getColor(this, textColor))
        btn.strokeWidth = 0
        btn.typeface = ResourcesCompat.getFont(this, font)
    }

    private fun renderPlan() {
        val state = SubscribePlanCopy.stateFor(selectedPlan)

        binding.textPrice.text = getString(state.priceRes)
        binding.textPeriod.text = getString(state.periodSuffixRes)

        binding.textBadge.apply {
            if (state.badgeRes == null) {
                visibility = View.GONE
            } else {
                text = getString(state.badgeRes)
                visibility = View.VISIBLE
            }
        }

        binding.textPerMonth.apply {
            if (state.perMonthPriceRes == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.subscribe_per_month_format, getString(state.perMonthPriceRes))
                visibility = View.VISIBLE
            }
        }

        binding.textSavings.apply {
            val percent = state.savingsPercent
            if (percent == null) {
                visibility = View.GONE
            } else {
                text = getString(R.string.subscribe_savings_format, percent)
                visibility = View.VISIBLE
            }
        }

        binding.textBilledCaption.text = getString(state.billedCaptionRes)
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            // Mock purchase — no real billing integration.
            settingsManager.setSubscriptionActive(true)
            Toast.makeText(this, getString(R.string.subscribe_activated), Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnRestore.setOnClickListener {
            Toast.makeText(this, getString(R.string.subscribe_restore_none), Toast.LENGTH_SHORT).show()
        }

        // Terms / Privacy — stubbed no-ops, ready to wire to URLs later.
        binding.btnTerms.setOnClickListener { /* TODO: open Terms URL */ }
        binding.btnPrivacy.setOnClickListener { /* TODO: open Privacy URL */ }
    }
}
