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

package com.ttcoachai

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ttcoachai.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check Authentication Status
        val settingsManager = com.ttcoachai.managers.SettingsManager(this)
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

        // Keep the Settings/Progress tabs highlighted on their pushed child screens.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_feedback, R.id.navigation_detection ->
                    binding.navView.menu.findItem(R.id.navigation_settings)?.isChecked = true
                R.id.navigation_session_history, R.id.navigation_session_review ->
                    binding.navView.menu.findItem(R.id.navigation_progress)?.isChecked = true
            }
        }

        // Finish-to-summary handoff: TrainingActivity's async save-completion callback sets
        // this on the Application; navigate to the session review screen exactly once, then
        // consume (reset to null) so backing out of review doesn't re-trigger it. See
        // docs/superpowers/specs/2026-07-03-finish-to-session-summary-flow-design.md.
        val app = application as TTCoachApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.pendingReviewSessionId.collectLatest { sessionId ->
                    if (sessionId != null) {
                        navController.navigate(
                            R.id.navigation_session_review,
                            Bundle().apply { putString("sessionId", sessionId) }
                        )
                        app.pendingReviewSessionId.value = null
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }
    // Explicitly re-setup navigation when menu changes to ensure listeners are correct
    // However, setupWithNavController sets the listener once. 
    // If we add items with IDs that match the graph, the existing listener *should* handle them,
    // provided the listener logic is "find destination by menuItem.itemId".
    // NavigationUI uses onNavDestinationSelected(item, navController).
    // So it should work dynamically.
}