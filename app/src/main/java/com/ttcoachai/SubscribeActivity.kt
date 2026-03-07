package com.ttcoachai

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ttcoachai.databinding.ActivitySubscribeBinding
import com.ttcoachai.managers.SettingsManager

class SubscribeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscribeBinding
    private lateinit var settingsManager: SettingsManager
    
    private enum class Plan { MONTHLY, QUARTERLY, YEARLY }
    private var selectedPlan = Plan.QUARTERLY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscribeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        
        setupPlanSelection()
        setupButtons()
        updatePlanSelection()
    }

    private fun setupPlanSelection() {
        binding.cardMonthly.setOnClickListener {
            selectedPlan = Plan.MONTHLY
            updatePlanSelection()
        }
        
        binding.cardQuarterly.setOnClickListener {
            selectedPlan = Plan.QUARTERLY
            updatePlanSelection()
        }
        
        binding.cardYearly.setOnClickListener {
            selectedPlan = Plan.YEARLY
            updatePlanSelection()
        }
    }

    private fun updatePlanSelection() {
        val activeColor = getColor(R.color.blue_600)
        val mutedColor = getColor(R.color.text_muted)
        val activeBg = getColor(R.color.bg_card_selected)
        val inactiveBg = getColor(android.R.color.transparent)
        val borderColor = getColor(android.R.color.darker_gray)

        // Reset all to unselected state
        binding.checkMonthly.setImageResource(R.drawable.ic_radio_unchecked)
        binding.checkMonthly.setColorFilter(mutedColor)
        binding.cardMonthly.strokeColor = borderColor
        binding.cardMonthly.setCardBackgroundColor(inactiveBg)
        
        binding.checkQuarterly.setImageResource(R.drawable.ic_radio_unchecked)
        binding.checkQuarterly.setColorFilter(mutedColor)
        binding.cardQuarterly.strokeColor = borderColor
        binding.cardQuarterly.setCardBackgroundColor(inactiveBg)
        
        binding.checkYearly.setImageResource(R.drawable.ic_radio_unchecked)
        binding.checkYearly.setColorFilter(mutedColor)
        binding.cardYearly.strokeColor = borderColor
        binding.cardYearly.setCardBackgroundColor(inactiveBg)
        
        // Highlight selected
        when (selectedPlan) {
            Plan.MONTHLY -> {
                binding.checkMonthly.setImageResource(R.drawable.ic_radio_checked)
                binding.checkMonthly.setColorFilter(activeColor)
                binding.cardMonthly.strokeColor = activeColor
                binding.cardMonthly.setCardBackgroundColor(activeBg)
                binding.btnSubscribe.text = getString(R.string.start_monthly_plan)
                binding.btnSubscribe.setBackgroundResource(R.drawable.bg_button_gradient_cyan)
            }
            Plan.QUARTERLY -> {
                binding.checkQuarterly.setImageResource(R.drawable.ic_radio_checked)
                binding.checkQuarterly.setColorFilter(activeColor)
                binding.cardQuarterly.strokeColor = activeColor
                binding.cardQuarterly.setCardBackgroundColor(activeBg)
                binding.btnSubscribe.text = getString(R.string.start_quarterly_plan)
                binding.btnSubscribe.setBackgroundResource(R.drawable.bg_button_gradient_purple)
            }
            Plan.YEARLY -> {
                binding.checkYearly.setImageResource(R.drawable.ic_radio_checked)
                binding.checkYearly.setColorFilter(activeColor)
                binding.cardYearly.strokeColor = activeColor
                binding.cardYearly.setCardBackgroundColor(activeBg)
                binding.btnSubscribe.text = getString(R.string.start_yearly_plan)
                binding.btnSubscribe.setBackgroundResource(R.drawable.bg_button_gradient_cyan)
            }
        }
    }

    private fun setupButtons() {
        binding.btnSubscribe.setOnClickListener {
            // Simulate subscription purchase
            settingsManager.setSubscriptionActive(true)
            android.widget.Toast.makeText(
                this,
                "Subscription activated!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            finish()
        }
        
        binding.btnMaybeLater.setOnClickListener {
            finish()
        }
    }
}
