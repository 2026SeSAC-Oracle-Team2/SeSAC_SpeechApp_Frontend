package com.sesac.speech.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speech.MainActivity
import com.sesac.speech.data.local.TokenManager
import com.sesac.speech.ui.login.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView는 theme의 windowBackground로 처리 (bg_splash drawable)

        lifecycleScope.launch {
            delay(1500)

            val tokenManager = TokenManager(this@SplashActivity)
            val nextActivity = if (tokenManager.isLoggedIn()) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }

            startActivity(Intent(this@SplashActivity, nextActivity))
            finish()
        }
    }
}
