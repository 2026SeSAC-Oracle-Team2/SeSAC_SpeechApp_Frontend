package com.sesac.speechapp.data.repository

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.sesac.speechapp.R
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.FirebaseAuthRequest
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val tokenManager = TokenManager(context)
    private val apiService = RetrofitClient.apiService

    // Google Sign-In 설정
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    /**
     * Google Sign-In Intent 획득 (Activity에서 launch)
     */
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Google Sign-In 결과 처리 → Firebase 인증 → 서버 로그인
     */
    suspend fun handleSignInResult(data: Intent?): Result<LoginResult> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
                ?: return Result.failure(Exception("Google Sign-In account is null"))

            // Firebase Auth에 Google 계정 연동 → ID Token 획득
            val idToken = firebaseAuthWithGoogle(account)

            // 서버에 ID Token 전송 → JWT 발급
            val loginResult = serverLogin(idToken)

            Result.success(loginResult)
        } catch (e: ApiException) {
            Result.failure(Exception("Google Sign-In failed: ${e.statusCode}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Firebase Auth로 Google ID Token 획득
     */
    private suspend fun firebaseAuthWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): String {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user ?: throw Exception("Firebase user is null")

        return user.getIdToken(true).await()?.token
            ?: throw Exception("Failed to get Firebase ID Token")
    }

    /**
     * 서버 로그인 API 호출
     *
     * user 객체는 응답에 포함되지 않을 수 있으므로 nullable로 취급하고,
     * uuid/email은 로컬 저장분이나 ID Token으로 보완한다.
     * isNewUser/nickname은 LoginResult로 반환해 회원가입 분기에 사용한다.
     */
    private suspend fun serverLogin(idToken: String): LoginResult {
        val response = apiService.firebaseAuth(FirebaseAuthRequest(idToken))

        if (!response.isSuccessful) {
            throw Exception("Server login failed: ${response.code()}")
        }

        val body = response.body()
            ?: throw Exception("Server response body is null")

        if (!body.success) {
            throw Exception(body.error?.message ?: "Server login failed")
        }

        val loginData = body.data
            ?: throw Exception("Login data is null")

        // user는 응답에서 생략될 수 있다 (nullable)
        val user = loginData.user

        // 토큰 저장
        tokenManager.saveAccessToken(loginData.accessToken)
        tokenManager.saveRefreshToken(loginData.refreshToken)
        tokenManager.saveUserInfo(
            userId = user?.id ?: -1,
            uuid = user?.uuid ?: tokenManager.getUserUuid() ?: "",
            email = user?.email ?: tokenManager.getUserEmail() ?: extractEmailFromIdToken(idToken)
        )

        return LoginResult(
            isNewUser = loginData.isNewUser,
            nickname = user?.nickname
        )
    }

    /**
     * Firebase ID Token(JWT) payload에서 email 추출 (보완용)
     */
    private fun extractEmailFromIdToken(idToken: String): String {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return ""
            val payload = String(
                android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                ),
                Charsets.UTF_8
            )
            val json = org.json.JSONObject(payload)
            json.optString("email", "")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 로그아웃
     */
    suspend fun logout() {
        try {
            apiService.logout()
        } catch (_: Exception) {
            // 서버 로그아웃 실패해도 로컬은 클리어
        }
        firebaseAuth.signOut()
        googleSignInClient.signOut().await()
        tokenManager.clear()
    }

    /**
     * 자동 로그인 체크
     */
    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    /**
     * 회원탈퇴
     * 1) DELETE /api/v1/users/me (서버에서 사용자 데이터 + Firebase 계정 삭제)
     * 2) 실패 시 로컬 Firebase 계정 삭제 시도
     * 3) 로컬 토큰/유저정보 전체 삭제
     */
    suspend fun withdraw(): Result<Unit> {
        return try {
            val response = apiService.withdraw()

            val body = response.body()
            val serverDeleted = when {
                response.code() == 204 -> true // No Content
                body != null -> body.success
                else -> response.isSuccessful
            }

            if (!serverDeleted) {
                val message = body?.error?.message
                    ?: "회원탈퇴 실패 (${response.code()})"
                return Result.failure(Exception(message))
            }

            // 서버에서 Firebase 계정까지 삭제 처리하지만, 로컬 Firebase에 계정이
            // 남아있는 경우를 대비해 클라이언트에서도 삭제 시도 (실패해도 무시)
            try {
                firebaseAuth.currentUser?.delete()?.await()
            } catch (_: Exception) {
                // 이미 삭제되었거나 재인증 필요 등 — 무시
            }

            googleSignInClient.signOut().await()
            tokenManager.clear()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 로그인 결과 — 회원가입 분기에 필요한 메타데이터
 *
 * isNewUser: 첫 로그인 1회 true
 * nickname: 미설정(회원가입 필요) 시 null
 */
data class LoginResult(
    val isNewUser: Boolean,
    val nickname: String?,
)
