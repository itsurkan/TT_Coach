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
import com.google.mediapipe.examples.poselandmarker.SubscribeActivity
import com.google.mediapipe.examples.poselandmarker.WeeklySessionsActivity
import com.google.mediapipe.examples.poselandmarker.SkillTargetActivity
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager
import android.widget.ArrayAdapter
import android.widget.AdapterView

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
        setupLanguageSection()
        setupThemeButtons()
        setupMenuItems()
        
        // Restore scroll position
        if (savedInstanceState != null) {
            val scrollPosition = savedInstanceState.getInt("SCROLL_POSITION", 0)
            binding.profileScrollView.post {
                binding.profileScrollView.scrollY = scrollPosition
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_binding != null) {
            outState.putInt("SCROLL_POSITION", binding.profileScrollView.scrollY)
        }
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
            val intent = Intent(requireContext(), SubscribeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupLanguageSection() {
        val languages = arrayOf("English", "Українська")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        // Set current selection based on saved language
        val currentLanguage = com.google.mediapipe.examples.poselandmarker.LocaleHelper.getSavedLanguage(requireContext())
        val selectionIndex = if (currentLanguage == "uk") 1 else 0
        binding.spinnerLanguage.setSelection(selectionIndex, false)

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLanguage = if (position == 1) "uk" else "en"
                val savedLanguage = com.google.mediapipe.examples.poselandmarker.LocaleHelper.getSavedLanguage(requireContext())
                
                if (newLanguage != savedLanguage) {
                    com.google.mediapipe.examples.poselandmarker.LocaleHelper.setLocale(requireContext(), newLanguage)
                    restartApp()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), com.google.mediapipe.examples.poselandmarker.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
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
        val selectedBgColor = requireContext().getColor(android.R.color.black)
        val normalBgColor = requireContext().getColor(android.R.color.transparent)
        val selectedIconColor = requireContext().getColor(android.R.color.white)
        val normalIconColor = requireContext().getColor(R.color.text_muted)
        val borderColor = requireContext().getColor(android.R.color.darker_gray)
        
        // Reset all buttons to normal state
        binding.btnThemeLight.setCardBackgroundColor(normalBgColor)
        binding.btnThemeLight.strokeColor = borderColor
        binding.ivThemeLight.setColorFilter(normalIconColor)
        binding.tvThemeLight.setTextColor(normalIconColor)
        
        binding.btnThemeDark.setCardBackgroundColor(normalBgColor)
        binding.btnThemeDark.strokeColor = borderColor
        binding.ivThemeDark.setColorFilter(normalIconColor)
        binding.tvThemeDark.setTextColor(normalIconColor)
        
        binding.btnThemeSystem.setCardBackgroundColor(normalBgColor)
        binding.btnThemeSystem.strokeColor = borderColor
        binding.ivThemeSystem.setColorFilter(normalIconColor)
        binding.tvThemeSystem.setTextColor(normalIconColor)
        
        // Highlight selected with dark background
        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> {
                binding.btnThemeLight.setCardBackgroundColor(selectedBgColor)
                binding.btnThemeLight.strokeColor = selectedBgColor
                binding.ivThemeLight.setColorFilter(selectedIconColor)
                binding.tvThemeLight.setTextColor(selectedIconColor)
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                binding.btnThemeDark.setCardBackgroundColor(selectedBgColor)
                binding.btnThemeDark.strokeColor = selectedBgColor
                binding.ivThemeDark.setColorFilter(selectedIconColor)
                binding.tvThemeDark.setTextColor(selectedIconColor)
            }
            else -> {
                binding.btnThemeSystem.setCardBackgroundColor(selectedBgColor)
                binding.btnThemeSystem.strokeColor = selectedBgColor
                binding.ivThemeSystem.setColorFilter(selectedIconColor)
                binding.tvThemeSystem.setTextColor(selectedIconColor)
            }
        }
    }

    private fun setupMenuItems() {
        // Weekly Sessions
        binding.layoutWeeklySessions.setOnClickListener {
            val intent = Intent(requireContext(), WeeklySessionsActivity::class.java)
            startActivity(intent)
        }
        
        // Skill Target
        binding.layoutSkillTarget.setOnClickListener {
            val intent = Intent(requireContext(), SkillTargetActivity::class.java)
            startActivity(intent)
        }
        
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
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun performLogout() {
        settingsManager.setLoggedIn(false)
        settingsManager.setSubscriptionActive(false)
        
        val intent = Intent(requireContext(), com.google.mediapipe.examples.poselandmarker.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
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
