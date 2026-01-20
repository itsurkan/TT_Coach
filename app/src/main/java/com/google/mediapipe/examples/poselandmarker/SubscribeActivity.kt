package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivitySubscribeBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

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
        // Reset all
        binding.checkMonthly.visibility = View.INVISIBLE
        binding.checkQuarterly.visibility = View.INVISIBLE
        binding.checkYearly.visibility = View.INVISIBLE
        
        binding.cardMonthly.strokeColor = getColor(android.R.color.darker_gray)
        binding.cardMonthly.setCardBackgroundColor(getColor(android.R.color.transparent))
        binding.cardQuarterly.strokeColor = getColor(android.R.color.darker_gray)
        binding.cardQuarterly.setCardBackgroundColor(getColor(android.R.color.transparent))
        binding.cardYearly.strokeColor = getColor(android.R.color.darker_gray)
        binding.cardYearly.setCardBackgroundColor(getColor(android.R.color.transparent))
        
        // Highlight selected
        when (selectedPlan) {
            Plan.MONTHLY -> {
                binding.checkMonthly.visibility = View.VISIBLE
                binding.cardMonthly.strokeColor = getColor(android.R.color.holo_blue_dark)
                binding.cardMonthly.setCardBackgroundColor(0x1A2196F3.toInt())
                binding.btnSubscribe.text = getString(R.string.start_monthly_plan)
            }
            Plan.QUARTERLY -> {
                binding.checkQuarterly.visibility = View.VISIBLE
                binding.cardQuarterly.strokeColor = getColor(android.R.color.holo_blue_dark)
                binding.cardQuarterly.setCardBackgroundColor(0x1A2196F3.toInt())
                binding.btnSubscribe.text = getString(R.string.start_quarterly_plan)
            }
            Plan.YEARLY -> {
                binding.checkYearly.visibility = View.VISIBLE
                binding.cardYearly.strokeColor = getColor(android.R.color.holo_blue_dark)
                binding.cardYearly.setCardBackgroundColor(0x1A2196F3.toInt())
                binding.btnSubscribe.text = getString(R.string.start_yearly_plan)
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
