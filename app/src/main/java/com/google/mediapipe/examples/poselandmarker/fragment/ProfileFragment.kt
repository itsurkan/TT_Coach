package com.google.mediapipe.examples.poselandmarker.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

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
        
        val settingsManager = com.google.mediapipe.examples.poselandmarker.managers.SettingsManager(requireContext())
        
        // Activity Settings button
        binding.layoutActivitySettings.setOnClickListener {
            val intent = Intent(requireContext(), com.google.mediapipe.examples.poselandmarker.ActivitySettingsActivity::class.java)
            startActivity(intent)
        }
        
        binding.switchDeveloperMode.isChecked = settingsManager.isDeveloperModeEnabled()
        
        binding.switchDeveloperMode.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDeveloperModeEnabled(isChecked)
            
            // Update MainActivity bottom navigation immediately
            val mainActivity = requireActivity() as com.google.mediapipe.examples.poselandmarker.MainActivity
            val navView = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.google.mediapipe.examples.poselandmarker.R.id.nav_view)
            navView.menu.findItem(com.google.mediapipe.examples.poselandmarker.R.id.navigation_developer)?.isVisible = isChecked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
