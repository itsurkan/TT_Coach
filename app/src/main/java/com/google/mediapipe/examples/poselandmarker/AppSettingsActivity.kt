package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityAppSettingsBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupDebugMode()
        setupSubscription()
    }

    private fun setupDebugMode() {
        // Initialize switch state
        binding.switchDebugMode.isChecked = settingsManager.isDeveloperModeEnabled()
        updateDebugInfoCard(settingsManager.isDeveloperModeEnabled())

        // Handle toggle
        binding.switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDeveloperModeEnabled(isChecked)
            updateDebugInfoCard(isChecked)
        }
    }

    private fun updateDebugInfoCard(isEnabled: Boolean) {
        binding.cardDebugInfo.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun setupSubscription() {
        // Initialize switch state
        val isSubscribed = settingsManager.isSubscriptionActive()
        binding.switchSubscription.isChecked = isSubscribed
        updateSubscriptionStatus(isSubscribed)

        // Handle toggle
        binding.switchSubscription.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSubscriptionActive(isChecked)
            updateSubscriptionStatus(isChecked)
        }
    }

    private fun updateSubscriptionStatus(isActive: Boolean) {
        if (isActive) {
            binding.tvSubscriptionStatus.text = getString(R.string.premium_active)
            binding.tvSubscriptionInfo.text = getString(R.string.subscription_active_info, "March 15, 2026")
            binding.ivSubscriptionStatus.setImageResource(R.drawable.ic_check_circle_2)
            binding.ivSubscriptionStatus.setColorFilter(getColor(android.R.color.holo_green_dark))
        } else {
            binding.tvSubscriptionStatus.text = getString(R.string.free_plan)
            binding.tvSubscriptionInfo.text = getString(R.string.subscription_inactive_info)
            binding.ivSubscriptionStatus.setImageResource(R.drawable.ic_alert_circle)
            binding.ivSubscriptionStatus.setColorFilter(getColor(android.R.color.holo_red_light))
        }
    }
}
