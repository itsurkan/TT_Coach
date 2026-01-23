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
        
        // Check Authentication Status
        val settingsManager = com.google.mediapipe.examples.poselandmarker.managers.SettingsManager(this)
        if (!settingsManager.isLoggedIn()) {
            val intent = android.content.Intent(this, LoginActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.navView.setupWithNavController(navController)
    }

    override fun onResume() {
        super.onResume()
        updateBottomNavigation()
    }

    private fun updateBottomNavigation() {
        val settingsManager = com.google.mediapipe.examples.poselandmarker.managers.SettingsManager(this)
        val menu = binding.navView.menu
        val isDebugMode = settingsManager.isDeveloperModeEnabled()

        if (isDebugMode) {
            // Show Developer item instead of Settings
            if (menu.findItem(R.id.navigation_developer) == null) {
                // Remove Settings if present
                menu.removeItem(R.id.navigation_settings)
                
                // Add Developer item at correct position (order 40)
                // Use a bug icon or generic settings icon if bug not available
                menu.add(android.view.Menu.NONE, R.id.navigation_developer, 40, "Debug").apply {
                    setIcon(android.R.drawable.ic_menu_manage) // Using system icon for now
                }
            }
        } else {
            // Show Settings item instead of Developer
            if (menu.findItem(R.id.navigation_settings) == null) {
                // Remove Developer if present
                menu.removeItem(R.id.navigation_developer)
                
                // Restore Settings item
                menu.add(android.view.Menu.NONE, R.id.navigation_settings, 40, getString(R.string.title_settings)).apply {
                    setIcon(R.drawable.ic_settings)
                }
            }
        }
    }
    
    // Explicitly re-setup navigation when menu changes to ensure listeners are correct
    // However, setupWithNavController sets the listener once. 
    // If we add items with IDs that match the graph, the existing listener *should* handle them,
    // provided the listener logic is "find destination by menuItem.itemId".
    // NavigationUI uses onNavDestinationSelected(item, navController).
    // So it should work dynamically.
}