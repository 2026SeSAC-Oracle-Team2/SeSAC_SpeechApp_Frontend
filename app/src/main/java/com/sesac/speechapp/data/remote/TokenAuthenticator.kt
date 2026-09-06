package com.sesac.speechapp.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.dto.TokenRefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * P3-28: 401/403 만료 응답 → 무음 refresh → 원요청 1회 재시도.
 *
 * - single-flight: 동시 다발 401에서 refresh 호출은 1회만 (synchronized + 토큰 비교)
 * - refresh 성공: TokenManager 갱신 + 재시도 요청 반환
 * - refresh 실패: 토큰 삭제 + 세션 만료 이벤트 발행(로그인 화면 유도) + null(재시도 없음)
 *
 * ⚠️ 백엔드 실계약 (2026-09-03): 만료/무효 토큰이 401이 아니라 **403**으로 온다
 * (SecurityConfig anyRequest authenticated → AccessDeniedHandler 기본 403).
 * → 401과 403을 모두 만료 신호로 취급한다. 단, 인증 없이 열린 경로
 *   (auth, sessions, voice, content 전체 permitAll 경로)는
 *   토큰이 있어도 서버가 검증하지 않으므로 여기서 재시도하지 않는다(무한루프 방지).
 *
 * Authenticator는 OkHttp 스레드에서 동기 호출되므로 runBlocking 사용 (OkHttp 공식 패턴).
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val refreshCall: suspend (refreshToken: String) -> String?,
    /** D-8-C2 B-1: 만료 브로드캐스트용 appContext — RetrofitClient.initTokenManager에서 주입 */
    private val appContext: Context? = null
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
        /** 세션 만료 브로드캐스트 액션 — MainActivity/LoginActivity 수신 */
        const val ACTION_SESSION_EXPIRED = "com.sesac.speechapp.SESSION_EXPIRED"
    }

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 401/403 외엔 무시
        if (response.code != 401 && response.code != 403) return null

        // 이미 Authorization 없이 보낸 요청이면 재시도 의미 없음
        if (response.request.header("Authorization") == null) return null

        // Auth/permitAll 경로는 재시도 금지 (인증 필터 자체가 안 돌므로 refresh로 해결 안 됨)
        val path = response.request.url.encodedPath
        if (path.contains("/api/v1/auth/")) return null

        val expiredToken = tokenManager.getAccessToken()

        synchronized(lock) {
            // single-flight: 다른 스레드가 이미 갱신에 성공했다면 새 토큰으로 재시도
            val currentToken = tokenManager.getAccessToken()
            if (currentToken != null && currentToken != expiredToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "refresh token 없음 — 세션 만료 처리")
                tokenManager.clear()
                notifySessionExpired()
                return null
            }

            // 동기 refresh (OkHttp 스레드)
            val newAccessToken = try {
                runBlocking { refreshCall(refreshToken) }
            } catch (e: Exception) {
                Log.w(TAG, "refresh 호출 실패: ${e.message}")
                null
            }

            return if (newAccessToken != null && newAccessToken.isNotBlank()) {
                tokenManager.saveAccessToken(newAccessToken)
                Log.d(TAG, "토큰 무음 갱신 성공 — 원요청 재시도")
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            } else {
                Log.w(TAG, "refresh 실패 — 토큰 삭제 + 세션 만료")
                tokenManager.clear()
                notifySessionExpired()
                null
            }
        }
    }

    /**
     * D-8-C2 B-1: 세션 만료 브로드캐스트 실전송 — 기존은 로그만 출력(버그).
     * MainActivity가 수신해 로그인 화면으로 라우팅한다 (세션 중간 만료 대응).
     * 브로드캐스트는 앱 스코프(exported=false 무관 — 동일 앱 내 수신).
     */
    private fun notifySessionExpired() {
        Log.w(TAG, "세션 만료 이벤트 발행 — 브로드캐스트 전송 (B-1)")
        appContext?.let { ctx ->
            ctx.sendBroadcast(Intent(ACTION_SESSION_EXPIRED).setPackage(ctx.packageName))
        }
    }
}