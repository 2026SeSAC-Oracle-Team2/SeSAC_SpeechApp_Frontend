package com.sesac.speechapp.ui.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.MainActivity
import com.sesac.speechapp.R
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.ui.login.LoginActivity
import com.sesac.speechapp.ui.onboarding.PermissionOnboardingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * P3-27 스플래시: 덕담 로고 + 토큰 선제 검증.
 *
 * 로직 (지시문 §5 1):
 *  - 토큰 없음 → 로그인 (최초 설치)
 *  - 토큰 있음 → GET /users/me 1회 검증
 *      성공 → 홈 (만료였다면 Authenticator가 무음 refresh 후 성공)
 *      실패(네트워크 오류 제외한 인증 실패) → 로그인
 *    401/403 + refresh 실패 시 TokenAuthenticator가 토큰을 클리어하므로
 *    이 검증은 "refresh까지 실패한 진짜 만료" 케이스를 걸러낸다.
 *  - 감성 지연: 0.5~1.5초 랜덤 후 검증 시작
 *  - 온보딩: 로그인 전환 직전 PermissionOnboarding 분기 유지 (P3-22)
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { goToNext() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        tokenManager = TokenManager(this)

        lifecycleScope.launch {
            // 스플래시 감성 지연 0.5~1.5초
            delay(Random.nextLong(500, 1500))

            if (!tokenManager.isLoggedIn()) {
                goToLogin()
                return@launch
            }

            // 토큰 선제 검증 — GET /users/me 1회
            val profileOk = withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.apiService.getMyProfile()
                    // 200 + success=true, 또는 Authenticator가 무음 갱신 후 성공한 경우
                    response.isSuccessful && response.body()?.success == true
                } catch (e: Exception) {
                    // 네트워크 오류(서버 다운 등)는 로그인으로 보내지 않는다 — 오프라인 진입 허용
                    null
                }
            }

            when (profileOk) {
                true -> goToMain()
                false -> {
                    // 인증 확정 실패 — Authenticator가 이미 토큰을 클리어했을 것
                    Toast.makeText(this@SplashActivity, "로그인이 만료되었어요. 다시 로그인해 주세요", Toast.LENGTH_SHORT).show()
                    goToLogin()
                }
                null -> goToMain() // 네트워크 오류 — 홈에서 재시도 가능하도록 진입 허용
            }
        }
    }

    private fun goToMain() {
        if (PermissionOnboardingActivity.shouldShow(this)) {
            permissionLauncher.launch(Intent(this, PermissionOnboardingActivity::class.java))
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToNext() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}