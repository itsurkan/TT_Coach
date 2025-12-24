/*
 * AI Coach for Table Tennis
 * Welcome Screen (Home Screen)
 */

package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Налаштування заголовку
        supportActionBar?.title = "AI Coach - Настільний Теніс"

        // Кнопка "Почати тренування"
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
            .setTitle("Про AI Coach")
            .setMessage("""
                AI Coach для настільного тенісу
                
                Версія: 1.0.0 MVP
                
                Використовує MediaPipe для аналізу техніки
                та надання миттєвого фідбеку.
                
                © 2025 TT Coach AI
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onBackPressed() {
        // Підтвердження виходу з додатку
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Вихід")
            .setMessage("Ви впевнені, що хочете вийти?")
            .setPositiveButton("Так") { _, _ -> finish() }
            .setNegativeButton("Ні", null)
            .show()
    }
}
