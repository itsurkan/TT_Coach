/*
 * AI Coach for Table Tennis
 * Welcome Screen (Home Screen)
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityWelcomeBinding

class WelcomeActivity : BaseActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Set title
        supportActionBar?.title = getString(R.string.welcome_title)

        // Start training button
        binding.btnStartTraining.setOnClickListener {
            val intent = Intent(this, ExerciseSelectionActivity::class.java)
            startActivity(intent)
        }

        // Кнопка "Налаштування"
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Кнопка "Про додаток"
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    override fun onBackPressed() {
        // Exit confirmation
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> finish() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }
}
