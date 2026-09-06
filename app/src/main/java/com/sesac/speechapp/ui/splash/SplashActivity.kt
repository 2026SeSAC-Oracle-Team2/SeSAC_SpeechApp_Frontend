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
 * P3-27 스플래시: 덕담 로고 + 라우팅 진입점.
 *
 * 로직 (D-8④ 사이클1 — 사용자 확정 플로우로 단순화):
 *  1) 스플래시에서 마이크 권한을 최우선 확인한다
 *  2-1) 권한 있음(또는 온보딩 완료) → 토큰 라우팅 진행
 *  2-2) 권한 없음 → PermissionOnboarding 노출 → 결과 무관 토큰 라우팅 진행
 *  토큰 라우팅 (기존 P3-27 유지):
 *   - 토큰 없음 → 로그인 (최초 설치)
 *   - 토큰 있음 → GET /users/me 1회 검증
 *       성공 → 홈 (만료였다면 Authenticator가 무음 refresh 후 성공)
 *       실패(네트워크 오류 제외한 인증 실패) → 로그인
 *  - 감성 지연: 0.5~1.5초 랜덤 후 검증 시작
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { routeNext() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        tokenManager = TokenManager(this)

        lifecycleScope.launch {
            // 스플래시 감성 지연 0.5~1.5초
            delay(Random.nextLong(500, 1500))

            // D-8④ 사이클1: 권한 확인이 라우팅(로그인/메인)보다 선행한다 (사용자 확정 플로우)
            // 1) 권한 없음 → 온보딩 노출 → 결과 무관 2) 토큰 라우팅
            // 2-1) 권한 있음(또는 온보딩 완료) → 바로 토큰 라우팅
            if (PermissionOnboardingActivity.shouldShow(this@SplashActivity)) {
                permissionLauncher.launch(Intent(this@SplashActivity, PermissionOnboardingActivity::class.java))
                return@launch
            }
            routeNext()
        }
    }

    /** 권한 게이트 통과 후 토큰 라우팅 (기존 P3-27 로직 그대로 이동) */
    private fun routeNext() {
        if (!tokenManager.isLoggedIn()) {
            goToLogin()
            return
        }

        // 토큰 선제 검증 — GET /users/me 1회
        lifecycleScope.launch {
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
                    // D-8-C2 B-1: Toast를 goToLogin 직전으로 이동 — 스플래시 종료와 겹쳐
                    // 안 보이던 케이스 방지 (로그인 화면 위에서 인지 가능)
                    goToLogin()
                    Toast.makeText(this, "로그인이 만료되었어요. 다시 로그인해 주세요", Toast.LENGTH_LONG).show()
                }
                null -> goToMain() // 네트워크 오류 — 홈에서 재시도 가능하도록 진입 허용
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}