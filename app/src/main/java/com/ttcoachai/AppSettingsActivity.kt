package com.ttcoachai

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ttcoachai.databinding.ActivityAppSettingsBinding
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.managers.CloudSyncManager
import com.ttcoachai.TTCoachApplication
import androidx.lifecycle.lifecycleScope
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var cloudSyncManager: com.ttcoachai.managers.CloudSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        cloudSyncManager = (application as TTCoachApplication).cloudSyncManager

        setupAccount() // Login/Account Logic
        setupCalibration()
        setupDebugMode()
        setupSubscription()
        setupPoseUploadConsent()
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

    /** Deliberate re-calibration entry point (A3) — lets a player re-record their reference
     *  forehand-drive technique on purpose, distinct from the "calibration required" dialog
     *  TrainingActivity shows when no baseline exists yet at all. */
    private fun setupCalibration() {
        binding.layoutRecalibrate.setOnClickListener {
            startActivity(android.content.Intent(this, com.ttcoachai.pose.RtmposeCalibrationActivity::class.java))
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
            com.ttcoachai.core.logging.LogManager.getLogger(this).setFileLoggingEnabled(isChecked)
            
            // Trigger cloud sync
            cloudSyncManager.uploadSettings()
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
            
            // Trigger cloud sync
            cloudSyncManager.uploadSettings()
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

    private fun setupPoseUploadConsent() {
        binding.switchPoseUpload.isChecked = settingsManager.isPoseUploadEnabled()

        binding.tvPoseUploadPrivacyLink.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.ttcoachai.core.LegalLinks.PRIVACY_URL)))
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(this, R.string.subscribe_no_browser_app, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchPoseUpload.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setPoseUploadEnabled(isChecked)
            if (!isChecked) {
                // cancelAllWorkByTag is asynchronous — an in-flight putFile() can still be
                // reading from a queued file's source path after this call returns. Await the
                // cancellation Operation's own result before deleting anything, so we never pull
                // a file out from under a still-running upload. Off the main thread throughout
                // (both the await and the directory listing/delete are IO-ish work).
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        com.ttcoachai.work.PoseUploadQueue.cancelAll(this@AppSettingsActivity).await()
                    } catch (e: Exception) {
                        android.util.Log.w("AppSettingsActivity", "cancelAll await failed, deleting cache anyway", e)
                    }
                    com.ttcoachai.pose.PoseSessionRecorder.cacheDir(this@AppSettingsActivity).listFiles()?.forEach { it.delete() }
                }
            }
            cloudSyncManager.uploadSettings()
        }
    }
}
