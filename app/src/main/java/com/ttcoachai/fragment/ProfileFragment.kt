package com.ttcoachai.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.ttcoachai.AppSettingsActivity
import com.ttcoachai.R
import com.ttcoachai.SubscribeActivity
import com.ttcoachai.WeeklySessionsActivity
import com.ttcoachai.SkillTargetActivity
import com.ttcoachai.DebugActivity
import com.ttcoachai.databinding.FragmentProfileBinding
import com.ttcoachai.managers.SettingsManager
import android.widget.ArrayAdapter
import android.widget.AdapterView
import com.ttcoachai.viewmodels.AuthViewModel
import coil.load
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: AuthViewModel

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
        
        // Initialize ViewModel
        val app = requireActivity().application as com.ttcoachai.TTCoachApplication
        val factory = com.ttcoachai.viewmodels.AuthViewModel.Factory(app.authRepository)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[com.ttcoachai.viewmodels.AuthViewModel::class.java]

        setupSubscriptionSection()
        setupLanguageSection()
        setupThemeButtons()
        setupMenuItems()
        observeViewModel()
        
        // Restore scroll position
        if (savedInstanceState != null) {
            val scrollPosition = savedInstanceState.getInt("SCROLL_POSITION", 0)
            binding.profileScrollView.post {
                binding.profileScrollView.scrollY = scrollPosition
            }
        }
    }

    private fun observeViewModel() {
        // Since we are in a Fragment, we should use viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state is com.ttcoachai.viewmodels.AuthUiState.Success) {
                    val user = state.user
                    binding.tvProfileName.text = user.displayName ?: getString(R.string.placeholder_user_name)
                    binding.tvProfileEmail.text = user.email
                    
                    user.photoUrl?.let { uri ->
                        binding.ivProfileImage.visibility = View.VISIBLE
                        binding.tvProfileInitials.visibility = View.GONE
                        
                        binding.ivProfileImage.load(uri) {
                           transformations(coil.transform.CircleCropTransformation())
                           crossfade(true)
                        }
                    } ?: run {
                        binding.ivProfileImage.visibility = View.GONE
                        binding.tvProfileInitials.visibility = View.VISIBLE
                        // Quick initials logic
                        val name = user.displayName ?: "User"
                        val initials = name.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase()
                        binding.tvProfileInitials.text = if (initials.isNotEmpty()) initials else "U"
                    }
                }
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
        val languages = resources.getStringArray(R.array.language_options)
        val languageCodes = resources.getStringArray(R.array.language_codes)
        
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, languages)
        binding.autoCompleteLanguage.setAdapter(adapter)

        // Set current selection based on saved language
        val currentLanguage = com.ttcoachai.LocaleHelper.getSavedLanguage(requireContext())
        val selectionIndex = languageCodes.indexOf(currentLanguage).coerceAtLeast(0)
        binding.autoCompleteLanguage.setText(languages[selectionIndex], false)

        binding.autoCompleteLanguage.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val newLanguage = languageCodes[position]
            val savedLanguage = com.ttcoachai.LocaleHelper.getSavedLanguage(requireContext())
            
            if (newLanguage != savedLanguage) {
                com.ttcoachai.LocaleHelper.setLocale(requireContext(), newLanguage)
                restartApp()
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), com.ttcoachai.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun setupThemeButtons() {
        // Get current theme from settings
        val currentMode = settingsManager.getNightMode()
        
        // Check corresponding button
        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.toggleGroupTheme.check(R.id.btn_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.toggleGroupTheme.check(R.id.btn_theme_dark)
            else -> binding.toggleGroupTheme.check(R.id.btn_theme_system)
        }
        
        binding.toggleGroupTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = when (checkedId) {
                    R.id.btn_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btn_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                settingsManager.setNightMode(newMode)
                AppCompatDelegate.setDefaultNightMode(newMode)
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
        // Sign out from Firebase and Google
        viewModel.signOut()
        
        settingsManager.setLoggedIn(false)
        settingsManager.setSubscriptionActive(false)
        
        val intent = Intent(requireContext(), com.ttcoachai.LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh subscription status when returning
        setupSubscriptionSection()
        setupDebugMode()
    }

    private fun setupDebugMode() {
        if (settingsManager.isDeveloperModeEnabled()) {
            binding.layoutDebugMode.visibility = View.VISIBLE
            binding.layoutDebugMode.setOnClickListener {
                val intent = Intent(requireContext(), DebugActivity::class.java)
                startActivity(intent)
            }
        } else {
            binding.layoutDebugMode.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
