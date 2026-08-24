package com.sesac.speech.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sesac.speech.MainActivity
import com.sesac.speech.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: Firebase Google Sign-In 연동 (P2-09)
        //  현재는 바로 MainActivity로 이동 (스텁)

        binding.btnGoogleSignIn.setOnClickListener {
            // Stub: 바로 메인으로 이동
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
