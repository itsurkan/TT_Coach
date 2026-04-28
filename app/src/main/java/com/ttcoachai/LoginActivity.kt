package com.ttcoachai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ttcoachai.databinding.ActivityLoginBinding
import com.ttcoachai.managers.SettingsManager
import com.ttcoachai.viewmodels.AuthViewModel
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

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
            viewModel.signInAnonymously()
        }

        binding.btnLoginApple.setOnClickListener {
            viewModel.signInAnonymously()
        }
    }
    
    private fun observeViewModel() {
        android.util.Log.d("LoginActivity", "Observing ViewModel")
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is com.ttcoachai.viewmodels.AuthUiState.Loading -> {
                        setLoading(true)
                    }
                    is com.ttcoachai.viewmodels.AuthUiState.Success -> {
                        setLoading(false)
                        settingsManager.setLoggedIn(true)
                        navigateToMain()
                    }
                    is com.ttcoachai.viewmodels.AuthUiState.Error -> {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        setLoading(false)
                    }
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingSpinner.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        
        binding.btnLoginGoogle.isEnabled = !isLoading
        binding.btnLoginFacebook.isEnabled = !isLoading
        binding.btnLoginApple.isEnabled = !isLoading
        
        // Optionally adjust alpha to show disabled state more clearly if theme doesn't do it well
        val alpha = if (isLoading) 0.5f else 1.0f
        binding.btnLoginGoogle.alpha = alpha
        binding.btnLoginFacebook.alpha = alpha
        binding.btnLoginApple.alpha = alpha
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
