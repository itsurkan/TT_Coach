package com.ttcoachai

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ttcoachai.databinding.ActivityAppSettingsBinding
import com.ttcoachai.managers.SettingsManager

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupAccount() // Login/Account Logic
        setupDebugMode()
        setupSubscription()
    }

    override fun onResume() {
        super.onResume()
        // Refresh state in case we returned from LoginActivity
        updateAccountStatus()
    }

    private fun setupAccount() {
        binding.switchLoggedIn.setOnClickListener {
            val isChecked = binding.switchLoggedIn.isChecked
            if (isChecked) {
                // User wants to login -> Go to LoginActivity
                // We don't set isLoggedIn=true here, LoginActivity does that on success.
                // Reset switch to false for now, it will be true if we return logged in.
                binding.switchLoggedIn.isChecked = false 
                val intent = android.content.Intent(this, LoginActivity::class.java)
                startActivity(intent)
            } else {
                // User wants to logout
                settingsManager.setLoggedIn(false)
                updateAccountStatus()
            }
        }
        
        // Initial state update
        updateAccountStatus()
    }

    private fun updateAccountStatus() {
        val isLoggedIn = settingsManager.isLoggedIn()
        // Avoid triggering listener loops if we used OnCheckedChangeListener (used OnClickListener above to avoid this mostly)
        // But better safe:
        binding.switchLoggedIn.isChecked = isLoggedIn
        
        if (isLoggedIn) {
            binding.tvLoginStatusDesc.text = getString(R.string.status_logged_in_as)
        } else {
            binding.tvLoginStatusDesc.text = getString(R.string.status_not_logged_in)
        }
    }

    private fun setupDebugMode() {
        // Initialize switch state
        binding.switchDebugMode.isChecked = settingsManager.isDeveloperModeEnabled()
        updateDebugInfoCard(settingsManager.isDeveloperModeEnabled())

        // Handle toggle
        binding.switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDeveloperModeEnabled(isChecked)
            updateDebugInfoCard(isChecked)
            
            // Update logging state in real-time
            (application as TTCoachApplication).getFileLogger().setFileLoggingEnabled(isChecked)
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
