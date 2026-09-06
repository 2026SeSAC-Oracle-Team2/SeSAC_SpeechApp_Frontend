package com.sesac.speechapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.sesac.speechapp.data.remote.TokenAuthenticator
import com.sesac.speechapp.databinding.ActivityMainBinding
import com.sesac.speechapp.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * D-8-C2 B-1: 세션 만료 브로드캐스트 수신 — 로그인 화면으로 라우팅 + 안내 토스트 + finish.
     * 세션 만료는 홈에서도 발생 가능 (토큰 15분 만료 — 세션 중간 대응).
     */
    private val sessionExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TokenAuthenticator.ACTION_SESSION_EXPIRED) {
                navigateToLoginOnExpired()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
    }

    override fun onStart() {
        super.onStart()
        // D-8-C2 B-1: 앱 스코프 브로드캐스트 수신 (setPackage로 동일 앱 한정)
        ContextCompat.registerReceiver(
            this,
            sessionExpiredReceiver,
            IntentFilter(TokenAuthenticator.ACTION_SESSION_EXPIRED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(sessionExpiredReceiver)
    }

    /** 만료 → 로그인 화면 이동 (토스트는 LoginActivity 착화 후 보이도록 이동 직전 표시) */
    private fun navigateToLoginOnExpired() {
        Toast.makeText(this, "로그인이 만료되었어요. 다시 로그인해 주세요", Toast.LENGTH_LONG).show()
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}