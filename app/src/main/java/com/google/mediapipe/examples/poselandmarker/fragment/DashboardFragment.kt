package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnBeginSession.setOnClickListener {
            // Navigate to Drills tab
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.google.mediapipe.examples.poselandmarker.R.id.nav_view)
            bottomNav.selectedItemId = com.google.mediapipe.examples.poselandmarker.R.id.navigation_drills
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
