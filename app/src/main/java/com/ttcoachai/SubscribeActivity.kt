package com.ttcoachai

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        renderPlan()
    }

    private fun setupToggle() {
        binding.toggleBilling.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedPlan = when (checkedId) {
                R.id.btn_monthly -> Plan.MONTHLY
                R.id.btn_quarterly -> Plan.QUARTERLY
                else -> Plan.YEARLY
            }
            renderPlan()
        }
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
