/*
 * AI Coach for Table Tennis
 * Settings Screen
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Spinner
import com.google.mediapipe.examples.poselandmarker.databinding.ActivitySettingsBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager
import com.google.mediapipe.examples.poselandmarker.managers.SettingsUIController

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var uiController: SettingsUIController
    private var languageChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        uiController = SettingsUIController(binding, settingsManager)
        
        setupUI()
        uiController.loadAllSettings()
    }

    private fun setupUI() {
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        uiController.setupExerciseParameters()
        uiController.setupAudioSettings()
        uiController.setupCameraSettings()
        setupLanguageSettings()

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnReset.setOnClickListener { resetToDefaults() }
    }
    
    private fun setupLanguageSettings() {
        val languageSpinner = findViewById<Spinner>(R.id.spinner_language) ?: return
        uiController.setupLanguageSpinner(languageSpinner, this)
    }

    private fun saveSettings() {
        handleLanguageChange()
        uiController.saveAllSettings()
        showSavedDialog()
    }
    
    private fun handleLanguageChange() {
        val languageSpinner = findViewById<Spinner>(R.id.spinner_language) ?: return
        val selectedPosition = languageSpinner.selectedItemPosition
        val languageCodes = resources.getStringArray(R.array.language_codes)
        val newLanguage = languageCodes[selectedPosition]
        val currentLanguage = LocaleHelper.getSavedLanguage(this)
        
        if (newLanguage != currentLanguage) {
            LocaleHelper.setLocale(this, newLanguage)
            languageChanged = true
        }
    }
    
    private fun showSavedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_saved_title)
            .setMessage(R.string.settings_saved_message)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                if (languageChanged) {
                    restartApp()
                } else {
                    finish()
                }
            }
            .show()
    }
    
    private fun restartApp() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                settingsManager.resetToDefaults()
                uiController.loadAllSettings()
                showResetCompleteDialog()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }
    
    private fun showResetCompleteDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.reset_done_title)
            .setMessage(R.string.reset_done_message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
