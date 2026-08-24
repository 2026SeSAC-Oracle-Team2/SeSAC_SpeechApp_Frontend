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
    suspend fun handleSignInResult(data: Intent?): Result<Boolean> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
                ?: return Result.failure(Exception("Google Sign-In account is null"))

            // Firebase Auth에 Google 계정 연동 → ID Token 획득
            val idToken = firebaseAuthWithGoogle(account)

            // 서버에 ID Token 전송 → JWT 발급
            serverLogin(idToken)

            Result.success(true)
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

        return user.getIdToken(false).await()?.token
            ?: throw Exception("Failed to get Firebase ID Token")
    }

    /**
     * 서버 로그인 API 호출
     */
    private suspend fun serverLogin(idToken: String) {
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

        // 토큰 저장
        tokenManager.saveAccessToken(loginData.accessToken)
        tokenManager.saveRefreshToken(loginData.refreshToken)
        tokenManager.saveUserInfo(
            uuid = loginData.user.uuid,
            email = loginData.user.email
        )
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
}
