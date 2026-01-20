package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.AppSettingsActivity
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager(requireContext())
        
        setupSubscriptionSection()
        setupThemeButtons()
        setupMenuItems()
    }

    private fun setupSubscriptionSection() {
        // Check subscription status from settings (simulated)
        val isSubscribed = settingsManager.isSubscriptionActive()
        
        if (isSubscribed) {
            binding.cardSubscriptionActive.visibility = View.VISIBLE
            binding.cardSubscriptionUpgrade.visibility = View.GONE
            binding.tvRenewalDate.text = getString(R.string.premium_renews_on, "March 15, 2026")
        } else {
            binding.cardSubscriptionActive.visibility = View.GONE
            binding.cardSubscriptionUpgrade.visibility = View.VISIBLE
        }
        
        // Upgrade card click - navigate to subscribe
        binding.cardSubscriptionUpgrade.setOnClickListener {
            // TODO: Navigate to subscription page when implemented
        }
    }

    private fun setupThemeButtons() {
        // Get current theme
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        
        updateThemeButtonStates(currentMode)
        
        binding.btnThemeLight.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            updateThemeButtonStates(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        binding.btnThemeDark.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            updateThemeButtonStates(AppCompatDelegate.MODE_NIGHT_YES)
        }
        
        binding.btnThemeSystem.setOnClickListener {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            updateThemeButtonStates(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    private fun updateThemeButtonStates(currentMode: Int) {
        val selectedColor = requireContext().getColor(android.R.color.holo_blue_dark)
        val normalColor = requireContext().getColor(R.color.text_muted)
        
        // Reset all buttons
        binding.btnThemeLight.setTextColor(normalColor)
        binding.btnThemeDark.setTextColor(normalColor)
        binding.btnThemeSystem.setTextColor(normalColor)
        
        // Highlight selected
        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.btnThemeLight.setTextColor(selectedColor)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.btnThemeDark.setTextColor(selectedColor)
            else -> binding.btnThemeSystem.setTextColor(selectedColor)
        }
    }

    private fun setupMenuItems() {
        // App Settings
        binding.layoutAppSettings.setOnClickListener {
            val intent = Intent(requireContext(), AppSettingsActivity::class.java)
            startActivity(intent)
        }
        
        // Edit Profile - placeholder
        binding.layoutEditProfile.setOnClickListener {
            // TODO: Implement edit profile
        }
        
        // Help & Support - placeholder
        binding.layoutHelpSupport.setOnClickListener {
            // TODO: Implement help & support
        }
        
        // Log Out
        binding.btnLogOut.setOnClickListener {
            // TODO: Implement log out
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh subscription status when returning
        setupSubscriptionSection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
