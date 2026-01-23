package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityLoginBinding
import com.google.mediapipe.examples.poselandmarker.managers.SettingsManager
import com.google.mediapipe.examples.poselandmarker.viewmodels.AuthViewModel
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: AuthViewModel
    private lateinit var signInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        
        // Initialize ViewModel
        val app = application as TTCoachApplication
        val factory = AuthViewModel.Factory(app.authRepository)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[AuthViewModel::class.java]

        setupSignInLauncher()
        setupListeners()
        observeViewModel()
    }
    
    private fun setupSignInLauncher() {
        signInLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    account.idToken?.let { idToken ->
                        viewModel.signInWithGoogle(idToken)
                    }
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnLoginGoogle.setOnClickListener {
            val signInIntent = (application as TTCoachApplication).authRepository.getSignInIntent()
            signInLauncher.launch(signInIntent)
        }

        binding.btnLoginFacebook.setOnClickListener {
            Toast.makeText(this, "Facebook Login not implemented yet", Toast.LENGTH_SHORT).show()
        }

        binding.btnLoginApple.setOnClickListener {
            Toast.makeText(this, "Apple Login not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeViewModel() {
        android.util.Log.d("LoginActivity", "Observing ViewModel")
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is com.google.mediapipe.examples.poselandmarker.viewmodels.AuthUiState.Loading -> {
                        // TODO: Show loading indicator
                    }
                    is com.google.mediapipe.examples.poselandmarker.viewmodels.AuthUiState.Success -> {
                        settingsManager.setLoggedIn(true)
                        navigateToMain()
                    }
                    is com.google.mediapipe.examples.poselandmarker.viewmodels.AuthUiState.Error -> {
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
