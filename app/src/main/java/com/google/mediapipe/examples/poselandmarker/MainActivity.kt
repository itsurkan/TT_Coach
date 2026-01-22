/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.navView.setupWithNavController(navController)
    }

    override fun onResume() {
        super.onResume()
        val settingsManager = com.google.mediapipe.examples.poselandmarker.managers.SettingsManager(this)
        val isDevMode = settingsManager.isDeveloperModeEnabled()
        
        val menu = binding.navView.menu
        
        if (isDevMode) {
            // Debug Mode: Show Developer, Hide Settings
            if (menu.findItem(R.id.navigation_settings) != null) {
                menu.removeItem(R.id.navigation_settings)
            }
            if (menu.findItem(R.id.navigation_developer) == null) {
                // Add Developer item at the position of Settings (index 3)
                // Note: Order is important for BottomNavigationView. 
                // We use order 300 to place it after Drills (Drills is usually 3rd item)
                // Assuming standard order: Home(0), Progress(1), Drills(2), [Target], Profile(4)
                menu.add(0, R.id.navigation_developer, 3, "Debug")
                    .setIcon(R.drawable.ic_settings) // Reuse settings icon for now
            }
        } else {
            // Standard Mode: Show Settings, Hide Developer
            if (menu.findItem(R.id.navigation_developer) != null) {
                menu.removeItem(R.id.navigation_developer)
            }
            if (menu.findItem(R.id.navigation_settings) == null) {
                menu.add(0, R.id.navigation_settings, 3, R.string.title_settings)
                    .setIcon(R.drawable.ic_settings)
            }
        }
    }
}