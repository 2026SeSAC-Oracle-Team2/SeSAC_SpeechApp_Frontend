package com.sesac.speechapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sesac.speechapp.MainActivity
import com.sesac.speechapp.databinding.ActivityLoginBinding
import com.sesac.speechapp.ui.signup.SignUpActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 자동 로그인 체크
        if (viewModel.isLoggedIn()) {
            navigateToMain()
            return
        }

        observeLoginState()

        binding.btnGoogleSignIn.setOnClickListener {
            signInLauncher.launch(viewModel.googleSignInIntent)
        }
    }

    private fun observeLoginState() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.btnGoogleSignIn.isEnabled = false
                    binding.btnGoogleSignIn.text = "로그인 중..."
                }
                is LoginState.Success -> {
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Google로 계속하기"
                    // 신규 유저이거나 닉네임이 아직 없으면 회원가입 화면으로
                    if (state.isNewUser || state.nickname == null) {
                        navigateToSignUp()
                    } else {
                        navigateToMain()
                    }
                }
                is LoginState.Error -> {
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Google로 계속하기"
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Google로 계속하기"
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToSignUp() {
        startActivity(Intent(this, SignUpActivity::class.java))
        finish()
    }
}
