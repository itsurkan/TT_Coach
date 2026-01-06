/*
 * AI Coach for Table Tennis
 * Welcome Screen (Home Screen)
 */

package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityWelcomeBinding

class WelcomeActivity : BaseActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    
    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestStoragePermission()
    }
    
    private fun requestStoragePermission() {
        // Storage permissions only needed on Android 6-12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - logs will work
            } else {
                // Permission denied - show explanation
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Дозвіл на файли")
                    .setMessage("Для збереження логів потрібен доступ до файлів. Логи не будуть видимі в File Manager, але доступні через ADB.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
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

        // Кнопка "Debug Video" (for development/testing)
        binding.btnDebugVideo.setOnClickListener {
            val intent = Intent(this, DebugActivity::class.java)
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
