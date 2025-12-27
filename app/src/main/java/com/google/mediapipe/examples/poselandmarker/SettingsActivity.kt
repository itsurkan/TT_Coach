/*
 * AI Coach for Table Tennis
 * Settings Screen
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import com.google.mediapipe.examples.poselandmarker.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private var languageChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("ai_coach_prefs", MODE_PRIVATE)
        
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Setup Action Bar
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Setup exercise parameters "Forehand Drive"
        setupForehandParameters()

        // Setup audio feedback
        setupAudioSettings()

        // Setup camera
        setupCameraSettings()
        
        // Setup language
        setupLanguageSettings()

        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Reset button
        binding.btnReset.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun setupForehandParameters() {
        // Ідеальний кут зап'ястя
        binding.seekBarWristAngle.apply {
            max = 200
            progress = prefs.getInt("ideal_wrist_angle", 180)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvWristAngleValue.text = "$progress°"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            binding.tvWristAngleValue.text = "${progress}°"
        }

        // Мінімальна ротація корпусу
        binding.seekBarBodyRotation.apply {
            max = 90
            progress = prefs.getInt("min_body_rotation", 45)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvBodyRotationValue.text = "$progress°"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            binding.tvBodyRotationValue.text = "${progress}°"
        }

        // Кут проведення (follow-through)
        binding.seekBarFollowThrough.apply {
            max = 180
            progress = prefs.getInt("follow_through_angle", 120)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvFollowThroughValue.text = "$progress°"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            binding.tvFollowThroughValue.text = "${progress}°"
        }
    }

    private fun setupAudioSettings() {
        // Увімкнути/вимкнути аудіо фідбек
        binding.switchAudioFeedback.isChecked = prefs.getBoolean("audio_feedback_enabled", true)

        // Гучність фідбеку
        binding.seekBarVolume.apply {
            max = 100
            progress = prefs.getInt("feedback_volume", 80)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvVolumeValue.text = "$progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            binding.tvVolumeValue.text = "${progress}%"
        }

        // Швидкість мовлення
        binding.seekBarSpeechRate.apply {
            max = 100
            progress = prefs.getInt("speech_rate", 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rate = progress / 50f // 0.0 - 2.0
                    binding.tvSpeechRateValue.text = String.format("%.1fx", rate)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            val rate = progress / 50f
            binding.tvSpeechRateValue.text = String.format("%.1fx", rate)
        }
    }

    private fun setupCameraSettings() {
        // Дозвіл камери (якість аналізу vs продуктивність)
        binding.spinnerCameraResolution.setSelection(prefs.getInt("camera_resolution", 1))
        
        // FPS обробки
        binding.seekBarFps.apply {
            max = 60
            // min = 15 (requires API 26+, handle manually)
            progress = prefs.getInt("target_fps", 30).coerceIn(15, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Забезпечити мінімум 15 FPS
                    val validProgress = progress.coerceAtLeast(15)
                    if (progress < 15 && fromUser) {
                        seekBar?.progress = 15
                    }
                    binding.tvFpsValue.text = "$validProgress FPS"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            binding.tvFpsValue.text = "${progress} FPS"
        }

        // Показувати скелет поверх відео
        binding.switchShowSkeleton.isChecked = prefs.getBoolean("show_skeleton", true)
    }
    
    private fun setupLanguageSettings() {
        // Get language spinner from layout (need to add to layout first)
        val languageSpinner = findViewById<Spinner>(R.id.spinner_language) ?: return
        
        // Setup language spinner
        val languages = resources.getStringArray(R.array.language_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        
        // Set current language
        val currentLang = LocaleHelper.getSavedLanguage(this)
        val languageCodes = resources.getStringArray(R.array.language_codes)
        val currentIndex = languageCodes.indexOf(currentLang)
        if (currentIndex >= 0) {
            languageSpinner.setSelection(currentIndex)
        }
    }

    private fun saveSettings() {
        // Save language first
        val languageSpinner = findViewById<Spinner>(R.id.spinner_language)
        if (languageSpinner != null) {
            val selectedPosition = languageSpinner.selectedItemPosition
            val languageCodes = resources.getStringArray(R.array.language_codes)
            val newLanguage = languageCodes[selectedPosition]
            val currentLanguage = LocaleHelper.getSavedLanguage(this)
            
            if (newLanguage != currentLanguage) {
                LocaleHelper.setLocale(this, newLanguage)
                languageChanged = true
            }
        }
        
        prefs.edit().apply {
            // Параметри вправи
            putInt("ideal_wrist_angle", binding.seekBarWristAngle.progress)
            putInt("min_body_rotation", binding.seekBarBodyRotation.progress)
            putInt("follow_through_angle", binding.seekBarFollowThrough.progress)

            // Аудіо налаштування
            putBoolean("audio_feedback_enabled", binding.switchAudioFeedback.isChecked)
            putInt("feedback_volume", binding.seekBarVolume.progress)
            putInt("speech_rate", binding.seekBarSpeechRate.progress)

            // Налаштування камери
            putInt("camera_resolution", binding.spinnerCameraResolution.selectedItemPosition)
            putInt("target_fps", binding.seekBarFps.progress)
            putBoolean("show_skeleton", binding.switchShowSkeleton.isChecked)

            apply()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_saved_title)
            .setMessage(R.string.settings_saved_message)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                if (languageChanged) {
                    // Restart the entire app to apply language to all activities
                    val intent = Intent(this, WelcomeActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                } else {
                    finish()
                }
            }
            .show()
    }

    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                prefs.edit().clear().apply()
                loadSettings()
                
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.reset_done_title)
                    .setMessage(R.string.reset_done_message)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun loadSettings() {
        // Перезавантаження всіх значень з SharedPreferences
        binding.seekBarWristAngle.progress = prefs.getInt("ideal_wrist_angle", 180)
        binding.seekBarBodyRotation.progress = prefs.getInt("min_body_rotation", 45)
        binding.seekBarFollowThrough.progress = prefs.getInt("follow_through_angle", 120)
        binding.switchAudioFeedback.isChecked = prefs.getBoolean("audio_feedback_enabled", true)
        binding.seekBarVolume.progress = prefs.getInt("feedback_volume", 80)
        binding.seekBarSpeechRate.progress = prefs.getInt("speech_rate", 50)
        binding.spinnerCameraResolution.setSelection(prefs.getInt("camera_resolution", 1))
        binding.seekBarFps.progress = prefs.getInt("target_fps", 30)
        binding.switchShowSkeleton.isChecked = prefs.getBoolean("show_skeleton", true)
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
