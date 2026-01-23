package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityLoginBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLoginGoogle.setOnClickListener {
            performMockLogin("Google")
        }

        binding.btnLoginFacebook.setOnClickListener {
            performMockLogin("Facebook")
        }

        binding.btnLoginApple.setOnClickListener {
            performMockLogin("Apple")
        }
    }

    private fun performMockLogin(provider: String) {
        // Stimulate a loading or process if needed, but for now just instant login
        Toast.makeText(this, "Logged in with $provider", Toast.LENGTH_SHORT).show()
        
        settingsManager.setLoggedIn(true)
        
        // Navigate to Main Screen (MainActivity) and clear back stack so user can't go back to Login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
